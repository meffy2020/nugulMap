package com.neogulmap.neogul_map.dto.recommendation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record RecommendationContext(
        RecommendationIntent intent,
        Set<String> preferredTags,
        Set<String> dislikedTags,
        Integer maxDistanceMeters,
        Set<String> recentExcludedIds,
        Set<String> positiveHistoryItemIds,
        int limit
) {
    public static final int MAX_LIMIT = 20;

    public RecommendationContext {
        intent = Objects.requireNonNull(intent, "intent");
        preferredTags = immutableTextSet(preferredTags);
        dislikedTags = immutableTextSet(dislikedTags);
        recentExcludedIds = immutableTextSet(recentExcludedIds);
        positiveHistoryItemIds = immutableTextSet(positiveHistoryItemIds);
        if (maxDistanceMeters != null && maxDistanceMeters < 1) {
            throw new IllegalArgumentException("maxDistanceMeters must be positive");
        }
        limit = Math.max(1, Math.min(MAX_LIMIT, limit));
    }

    private static Set<String> immutableTextSet(Set<String> values) {
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        if (values != null) {
            values.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .forEach(copy::add);
        }
        return Collections.unmodifiableSet(copy);
    }
}
