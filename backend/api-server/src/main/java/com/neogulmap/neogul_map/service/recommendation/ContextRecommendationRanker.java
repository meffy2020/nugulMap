package com.neogulmap.neogul_map.service.recommendation;

import com.neogulmap.neogul_map.dto.recommendation.CandidateFreshness;
import com.neogulmap.neogul_map.dto.recommendation.PlaceCandidate;
import com.neogulmap.neogul_map.dto.recommendation.PlaceProvider;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationContext;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationIntent;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationReasonCode;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationResult;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ContextRecommendationRanker {

    private static final double SAME_CATEGORY_PENALTY = 0.16;
    private static final int DEFAULT_DISTANCE_REFERENCE_METERS = 20_000;

    public RecommendationResult rank(List<PlaceCandidate> candidates, RecommendationContext context) {
        Objects.requireNonNull(context, "context");
        if (candidates == null || candidates.isEmpty()) {
            return new RecommendationResult(List.of(), 0);
        }

        Set<String> preferredTags = canonicalSet(context.preferredTags());
        Set<String> dislikedTags = canonicalSet(context.dislikedTags());
        List<ScoredCandidate> eligible = candidates.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> !isRecentlyExcluded(candidate, context.recentExcludedIds()))
                .filter(candidate -> withinMaximumDistance(candidate, context.maxDistanceMeters()))
                .map(candidate -> score(candidate, context, preferredTags, dislikedTags))
                .filter(Objects::nonNull)
                .sorted(baseComparator())
                .toList();

        Map<String, ScoredCandidate> uniqueCandidates = new LinkedHashMap<>();
        for (ScoredCandidate candidate : eligible) {
            uniqueCandidates.putIfAbsent(candidate.candidate().provider() + ":" + candidate.candidate().id(), candidate);
        }

        List<ScoredCandidate> remaining = new ArrayList<>(uniqueCandidates.values());
        List<RecommendationResult.Item> ranked = new ArrayList<>();
        Map<String, Integer> selectedCategoryCounts = new LinkedHashMap<>();

        while (!remaining.isEmpty() && ranked.size() < context.limit()) {
            ScoredCandidate selected = remaining.stream()
                    .min(diversifiedComparator(selectedCategoryCounts, context.intent()))
                    .orElseThrow();
            remaining.remove(selected);

            String diversityKey = diversityKey(selected.candidate(), context.intent());
            int previousCategoryCount = selectedCategoryCounts.getOrDefault(diversityKey, 0);
            double finalScore = selected.baseScore() - (SAME_CATEGORY_PENALTY * previousCategoryCount);

            List<RecommendationReasonCode> reasons = new ArrayList<>(selected.reasonCodes());
            if (!ranked.isEmpty() && previousCategoryCount == 0) {
                reasons.add(RecommendationReasonCode.DIVERSE_CATEGORY);
            }
            ranked.add(new RecommendationResult.Item(
                    selected.candidate(),
                    roundScore(finalScore),
                    reasons
            ));
            selectedCategoryCounts.merge(diversityKey, 1, Integer::sum);
        }

        return new RecommendationResult(ranked, uniqueCandidates.size());
    }

    private ScoredCandidate score(
            PlaceCandidate candidate,
            RecommendationContext context,
            Set<String> preferredTags,
            Set<String> dislikedTags
    ) {
        Set<String> candidateTags = candidateTags(candidate);
        if (!matchesIntent(candidateTags, context.intent()) || intersects(candidateTags, dislikedTags)) {
            return null;
        }

        List<RecommendationReasonCode> reasons = new ArrayList<>();
        double score = intentScore(context.intent());
        reasons.add(intentReason(context.intent()));

        int preferredMatches = intersectionSize(candidateTags, preferredTags);
        if (preferredMatches > 0) {
            score += Math.min(0.24, preferredMatches * 0.08);
            reasons.add(RecommendationReasonCode.PREFERRED_TAG_MATCH);
        }

        if (matchesItemKey(candidate, context.positiveHistoryItemIds())) {
            score += 0.16;
            reasons.add(RecommendationReasonCode.POSITIVE_HISTORY_MATCH);
        }

        score += freshnessScore(candidate.freshness());
        if (candidate.freshness() == CandidateFreshness.LIVE
                || candidate.freshness() == CandidateFreshness.RECENT) {
            reasons.add(RecommendationReasonCode.FRESH_CANDIDATE);
        }

        score += providerScore(candidate.provider());
        if (isTrustedProvider(candidate.provider())) {
            reasons.add(RecommendationReasonCode.TRUSTED_PROVIDER);
        }

        if (candidate.distanceMeters() != null) {
            int distanceReference = context.maxDistanceMeters() == null
                    ? DEFAULT_DISTANCE_REFERENCE_METERS
                    : context.maxDistanceMeters();
            double proximity = Math.max(0, 1 - (candidate.distanceMeters() / (double) distanceReference));
            score += 0.20 * proximity;
            if (candidate.distanceMeters() <= 1_000) {
                reasons.add(RecommendationReasonCode.WITHIN_ONE_KILOMETER);
            }
        }

        return new ScoredCandidate(candidate, roundScore(score), List.copyOf(reasons));
    }

    private Comparator<ScoredCandidate> diversifiedComparator(
            Map<String, Integer> selectedCategoryCounts,
            RecommendationIntent intent
    ) {
        return Comparator
                .comparingDouble((ScoredCandidate candidate) -> adjustedScore(
                        candidate,
                        selectedCategoryCounts,
                        intent
                ))
                .reversed()
                .thenComparing(baseComparator());
    }

    private double adjustedScore(
            ScoredCandidate candidate,
            Map<String, Integer> selectedCategoryCounts,
            RecommendationIntent intent
    ) {
        String category = diversityKey(candidate.candidate(), intent);
        return candidate.baseScore()
                - (SAME_CATEGORY_PENALTY * selectedCategoryCounts.getOrDefault(category, 0));
    }

    private Comparator<ScoredCandidate> baseComparator() {
        return Comparator
                .comparingDouble(ScoredCandidate::baseScore)
                .reversed()
                .thenComparingInt(candidate -> distanceOrMaximum(candidate.candidate()))
                .thenComparing(candidate -> candidate.candidate().provider().name())
                .thenComparing(candidate -> candidate.candidate().id())
                .thenComparing(candidate -> canonical(candidate.candidate().name()))
                .thenComparing(candidate -> canonical(candidate.candidate().category()));
    }

    private boolean withinMaximumDistance(PlaceCandidate candidate, Integer maximumDistanceMeters) {
        if (maximumDistanceMeters == null) {
            return true;
        }
        return candidate.distanceMeters() != null && candidate.distanceMeters() <= maximumDistanceMeters;
    }

    private boolean isRecentlyExcluded(PlaceCandidate candidate, Set<String> recentExcludedIds) {
        return matchesItemKey(candidate, recentExcludedIds);
    }

    private boolean matchesItemKey(PlaceCandidate candidate, Set<String> itemIds) {
        if (itemIds.contains(candidate.id())) {
            return true;
        }
        String providerItemId = candidate.provider().name().toLowerCase(Locale.ROOT) + ":" + candidate.id();
        return itemIds.contains(providerItemId);
    }

    private boolean matchesIntent(Set<String> tags, RecommendationIntent intent) {
        return switch (intent) {
            case CAFE -> containsAny(tags, "ce7", "카페");
            case RESTAURANT -> containsAny(tags, "fd6", "음식점");
            case DATE -> containsAny(tags, "ce7", "카페", "fd6", "음식점",
                    "at4", "관광명소", "ct1", "문화시설");
        };
    }

    private String diversityKey(PlaceCandidate candidate, RecommendationIntent intent) {
        Set<String> tags = candidateTags(candidate);
        if (intent == RecommendationIntent.DATE) {
            if (containsAny(tags, "ct1", "문화시설")) {
                return "culture";
            }
            if (containsAny(tags, "at4", "관광명소")) {
                return "attraction";
            }
            if (containsAny(tags, "fd6", "음식점")) {
                return "restaurant";
            }
            if (containsAny(tags, "ce7", "카페")) {
                return "cafe";
            }
        }

        String category = candidate.category();
        int separator = category.lastIndexOf('>');
        String leaf = separator >= 0 ? category.substring(separator + 1) : category;
        String normalizedLeaf = canonical(leaf);
        if (!normalizedLeaf.isEmpty()) {
            return normalizedLeaf;
        }
        return candidate.provider() + ":" + candidate.id();
    }

    private Set<String> candidateTags(PlaceCandidate candidate) {
        LinkedHashSet<String> tags = new LinkedHashSet<>(canonicalSet(candidate.tags()));
        String category = canonical(candidate.category());
        if (!category.isEmpty()) {
            tags.add(category);
            for (String part : category.split(">")) {
                String normalizedPart = canonical(part);
                if (!normalizedPart.isEmpty()) {
                    tags.add(normalizedPart);
                }
            }
        }
        return tags;
    }

    private static Set<String> canonicalSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            String canonicalValue = canonical(value);
            if (!canonicalValue.isEmpty()) {
                normalized.add(canonicalValue);
            }
        }
        return normalized;
    }

    private static String canonical(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static boolean intersects(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        return left.stream().anyMatch(right::contains);
    }

    private static int intersectionSize(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        return (int) left.stream().filter(right::contains).count();
    }

    private static boolean containsAny(Set<String> values, String... expected) {
        for (String value : expected) {
            if (values.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static double intentScore(RecommendationIntent intent) {
        return intent == RecommendationIntent.DATE ? 0.32 : 0.40;
    }

    private static RecommendationReasonCode intentReason(RecommendationIntent intent) {
        return switch (intent) {
            case CAFE -> RecommendationReasonCode.CAFE_INTENT_MATCH;
            case RESTAURANT -> RecommendationReasonCode.RESTAURANT_INTENT_MATCH;
            case DATE -> RecommendationReasonCode.DATE_INTENT_MATCH;
        };
    }

    private static double freshnessScore(CandidateFreshness freshness) {
        return switch (freshness) {
            case LIVE -> 0.12;
            case RECENT -> 0.08;
            case STALE -> -0.08;
            case UNKNOWN -> 0;
        };
    }

    private static double providerScore(PlaceProvider provider) {
        return switch (provider) {
            case KAKAO_LOCAL -> 0.10;
            case SEOUL_OPEN_DATA -> 0.09;
            case TOUR_API -> 0.08;
            case USER_SUBMITTED -> 0.02;
            case UNKNOWN -> 0;
        };
    }

    private static boolean isTrustedProvider(PlaceProvider provider) {
        return provider == PlaceProvider.KAKAO_LOCAL
                || provider == PlaceProvider.SEOUL_OPEN_DATA
                || provider == PlaceProvider.TOUR_API;
    }

    private static int distanceOrMaximum(PlaceCandidate candidate) {
        return candidate.distanceMeters() == null ? Integer.MAX_VALUE : candidate.distanceMeters();
    }

    private static double roundScore(double score) {
        return Math.round(score * 1_000_000d) / 1_000_000d;
    }

    private record ScoredCandidate(
            PlaceCandidate candidate,
            double baseScore,
            List<RecommendationReasonCode> reasonCodes
    ) {
    }
}
