package com.neogulmap.neogul_map.service.recommendation;

import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.domain.recommendation.RecommendationEventType;
import com.neogulmap.neogul_map.domain.recommendation.RecommendationInteraction;
import com.neogulmap.neogul_map.domain.recommendation.RecommendationItemType;
import com.neogulmap.neogul_map.domain.recommendation.UserPreference;
import com.neogulmap.neogul_map.domain.recommendation.UserSavedItem;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationInteractionRequest;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationInteractionResponse;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationItemKeyRequest;
import com.neogulmap.neogul_map.dto.recommendation.UserPreferenceResponse;
import com.neogulmap.neogul_map.dto.recommendation.UserPreferenceUpdateRequest;
import com.neogulmap.neogul_map.dto.recommendation.UserSavedItemRequest;
import com.neogulmap.neogul_map.dto.recommendation.UserSavedItemResponse;
import com.neogulmap.neogul_map.repository.recommendation.RecommendationInteractionRepository;
import com.neogulmap.neogul_map.repository.recommendation.UserPreferenceRepository;
import com.neogulmap.neogul_map.repository.recommendation.UserSavedItemRepository;
import com.neogulmap.neogul_map.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class RecommendationDataService {

    private static final int MAX_TAGS_PER_GROUP = 20;
    private static final int MAX_TAG_LENGTH = 30;
    private static final int MIN_DISTANCE_METERS = 100;
    private static final int MAX_DISTANCE_METERS = 50_000;
    private static final int MAX_EXCLUDED_ITEMS = 100;
    static final int MAX_SAVED_ITEMS_PER_USER = 500;
    static final int MAX_INTERACTIONS_PER_USER = 10_000;
    private static final Pattern PROVIDER_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._-]*");

    private final UserPreferenceRepository userPreferenceRepository;
    private final UserSavedItemRepository userSavedItemRepository;
    private final RecommendationInteractionRepository recommendationInteractionRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Autowired
    public RecommendationDataService(
            UserPreferenceRepository userPreferenceRepository,
            UserSavedItemRepository userSavedItemRepository,
            RecommendationInteractionRepository recommendationInteractionRepository,
            UserRepository userRepository
    ) {
        this(
                userPreferenceRepository,
                userSavedItemRepository,
                recommendationInteractionRepository,
                userRepository,
                Clock.systemUTC()
        );
    }

    RecommendationDataService(
            UserPreferenceRepository userPreferenceRepository,
            UserSavedItemRepository userSavedItemRepository,
            RecommendationInteractionRepository recommendationInteractionRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this.userPreferenceRepository = userPreferenceRepository;
        this.userSavedItemRepository = userSavedItemRepository;
        this.recommendationInteractionRepository = recommendationInteractionRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserPreferenceResponse getPreference(User user) {
        Long userId = requireAuthenticatedUser(user);
        return userPreferenceRepository.findByUser_Id(userId)
                .map(UserPreferenceResponse::from)
                .orElseGet(UserPreferenceResponse::defaults);
    }

    @Transactional
    public UserPreferenceResponse updatePreference(User user, UserPreferenceUpdateRequest request) {
        Long userId = requireAuthenticatedUser(user);
        if (request == null) {
            throw validation("추천 설정이 필요합니다.");
        }
        lockUser(userId);

        Set<String> preferredTags = normalizeTags(request.preferredTags());
        Set<String> dislikedTags = normalizeTags(request.dislikedTags());
        Set<String> overlappingTags = new LinkedHashSet<>(preferredTags);
        overlappingTags.retainAll(dislikedTags);
        if (!overlappingTags.isEmpty()) {
            throw validation("선호 태그와 비선호 태그는 겹칠 수 없습니다.");
        }
        validateDistance(request.maxDistanceMeters());

        LocalDateTime now = now();
        UserPreference preference = userPreferenceRepository.findByUser_Id(userId)
                .orElseGet(() -> UserPreference.builder()
                        .user(user)
                        .preferredTags(new LinkedHashSet<>())
                        .dislikedTags(new LinkedHashSet<>())
                        .updatedAt(now)
                        .build());
        preference.update(
                request.personalizationEnabled(),
                preferredTags,
                dislikedTags,
                request.budgetLevel(),
                request.maxDistanceMeters(),
                now
        );
        UserPreference savedPreference = userPreferenceRepository.save(preference);
        if (!request.personalizationEnabled()) {
            recommendationInteractionRepository.deleteByUser_Id(userId);
        }
        return UserPreferenceResponse.from(savedPreference);
    }

    @Transactional(readOnly = true)
    public List<UserSavedItemResponse> getSavedItems(User user) {
        Long userId = requireAuthenticatedUser(user);
        return userSavedItemRepository.findByUser_IdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(UserSavedItemResponse::from)
                .toList();
    }

    @Transactional
    public UserSavedItemResponse saveItem(User user, UserSavedItemRequest request) {
        Long userId = requireAuthenticatedUser(user);
        if (request == null) {
            throw validation("저장할 추천 항목이 필요합니다.");
        }
        lockUser(userId);

        RecommendationItemType itemType = requireItemType(request.itemType());
        String provider = normalizeProvider(request.provider());
        String itemId = requiredText(request.itemId(), 191, "항목 ID");
        String title = requiredText(request.title(), 255, "장소명");
        String category = optionalText(request.category(), 100, "카테고리");
        BigDecimal lat = validateCoordinate(request.lat(), BigDecimal.valueOf(-90), BigDecimal.valueOf(90), "위도");
        BigDecimal lng = validateCoordinate(request.lng(), BigDecimal.valueOf(-180), BigDecimal.valueOf(180), "경도");
        String sourceUrl = normalizeSourceUrl(request.sourceUrl());
        LocalDateTime now = now();

        UserSavedItem item = userSavedItemRepository
                .findByUser_IdAndItemTypeAndProviderAndItemId(userId, itemType, provider, itemId)
                .orElse(null);
        if (item == null) {
            if (userSavedItemRepository.countByUser_Id(userId) >= MAX_SAVED_ITEMS_PER_USER) {
                throw validation("저장한 추천 장소는 사용자당 최대 500개까지 보관할 수 있습니다.");
            }
            item = UserSavedItem.builder()
                    .user(user)
                    .itemType(itemType)
                    .provider(provider)
                    .itemId(itemId)
                    .createdAt(now)
                    .build();
        }
        item.refreshMetadata(title, category, lat, lng, sourceUrl, now);
        return UserSavedItemResponse.from(userSavedItemRepository.save(item));
    }

    @Transactional
    public void unsaveItem(User user, RecommendationItemKeyRequest request) {
        Long userId = requireAuthenticatedUser(user);
        if (request == null) {
            throw validation("삭제할 추천 항목 키가 필요합니다.");
        }
        lockUser(userId);
        userSavedItemRepository.deleteByUser_IdAndItemTypeAndProviderAndItemId(
                userId,
                requireItemType(request.itemType()),
                normalizeProvider(request.provider()),
                requiredText(request.itemId(), 191, "항목 ID")
        );
    }

    @Transactional
    public RecommendationInteractionResponse recordInteraction(
            User user,
            RecommendationInteractionRequest request
    ) {
        Long userId = requireAuthenticatedUser(user);
        if (request == null) {
            throw validation("추천 행동 정보가 필요합니다.");
        }
        lockUser(userId);
        boolean personalizationEnabled = userPreferenceRepository.findByUser_Id(userId)
                .map(UserPreference::isPersonalizationEnabled)
                .orElse(false);
        if (!personalizationEnabled) {
            throw validation("개인화를 켠 사용자만 추천 행동을 저장할 수 있습니다.");
        }

        String requestId = requiredText(request.requestId(), 64, "요청 ID");
        RecommendationItemType itemType = requireItemType(request.itemType());
        String provider = normalizeProvider(request.provider());
        String itemId = requiredText(request.itemId(), 191, "항목 ID");
        RecommendationEventType eventType = requireEventType(request.eventType());
        RecommendationInteraction existing = recommendationInteractionRepository
                .findByUser_IdAndRequestIdAndItemTypeAndProviderAndItemIdAndEventType(
                        userId,
                        requestId,
                        itemType,
                        provider,
                        itemId,
                        eventType
                )
                .orElse(null);
        if (existing != null) {
            return RecommendationInteractionResponse.from(existing);
        }
        if (recommendationInteractionRepository.countByUser_Id(userId) >= MAX_INTERACTIONS_PER_USER) {
            throw validation("추천 행동은 사용자당 최대 10,000개까지 저장할 수 있습니다.");
        }

        RecommendationInteraction interaction = RecommendationInteraction.builder()
                .user(user)
                .requestId(requestId)
                .itemType(itemType)
                .provider(provider)
                .itemId(itemId)
                .eventType(eventType)
                .intent(requiredText(request.intent(), 40, "추천 목적"))
                // 정밀 위치나 임의의 자유 텍스트를 행동 이력에 섞지 않는다.
                // 신뢰할 수 있는 서버 측 행정구역 변환을 붙이기 전까지는 지역 문맥을 저장하지 않는다.
                .contextRegion(null)
                .modelVersion(requiredText(request.modelVersion(), 64, "모델 버전"))
                .occurredAt(now())
                .build();
        return RecommendationInteractionResponse.from(recommendationInteractionRepository.save(interaction));
    }

    @Transactional
    public void clearRecommendationData(User user) {
        Long userId = requireAuthenticatedUser(user);
        lockUser(userId);
        recommendationInteractionRepository.deleteByUser_Id(userId);
        userSavedItemRepository.deleteByUser_Id(userId);
        userPreferenceRepository.deleteByUser_Id(userId);
    }

    @Transactional(readOnly = true)
    public Set<String> getRecentExcludedItemIds(User user, int limit) {
        Long userId = requireAuthenticatedUser(user);
        if (limit <= 0) {
            return Set.of();
        }

        int boundedLimit = Math.min(limit, MAX_EXCLUDED_ITEMS);
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        recommendationInteractionRepository.findByUser_IdAndEventTypeOrderByOccurredAtDesc(
                        userId,
                        RecommendationEventType.NOT_INTERESTED,
                        PageRequest.of(0, boundedLimit)
                )
                .forEach(interaction -> keys.add(interaction.getProvider() + ":" + interaction.getItemId()));
        return Collections.unmodifiableSet(keys);
    }

    @Transactional(readOnly = true)
    public Set<String> getRecentPositiveItemIds(User user, int limit) {
        Long userId = requireAuthenticatedUser(user);
        if (limit <= 0) {
            return Set.of();
        }

        int boundedLimit = Math.min(limit, MAX_EXCLUDED_ITEMS);
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        recommendationInteractionRepository.findByUser_IdAndEventTypeInOrderByOccurredAtDesc(
                        userId,
                        Set.of(
                                RecommendationEventType.DETAIL_OPEN,
                                RecommendationEventType.SAVE,
                                RecommendationEventType.ROUTE
                        ),
                        PageRequest.of(0, boundedLimit)
                )
                .forEach(interaction -> keys.add(interaction.getProvider() + ":" + interaction.getItemId()));
        return Collections.unmodifiableSet(keys);
    }

    private Long requireAuthenticatedUser(User user) {
        if (user == null || user.getId() == null) {
            throw validation("로그인한 사용자만 추천 데이터를 저장할 수 있습니다.");
        }
        return user.getId();
    }

    private void lockUser(Long userId) {
        if (userRepository.findForUpdateById(userId).isEmpty()) {
            throw validation("추천 데이터를 저장할 사용자 계정을 찾을 수 없습니다.");
        }
    }

    private RecommendationItemType requireItemType(RecommendationItemType itemType) {
        if (itemType == null) {
            throw validation("항목 유형이 필요합니다.");
        }
        return itemType;
    }

    private RecommendationEventType requireEventType(RecommendationEventType eventType) {
        if (eventType == null) {
            throw validation("추천 행동 유형이 필요합니다.");
        }
        return eventType;
    }

    private Set<String> normalizeTags(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Set.of();
        }
        if (tags.size() > MAX_TAGS_PER_GROUP) {
            throw validation("태그는 그룹당 20개까지 저장할 수 있습니다.");
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            String value = tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT);
            if (value.isEmpty() || value.length() > MAX_TAG_LENGTH) {
                throw validation("태그는 1자 이상 30자 이하여야 합니다.");
            }
            normalized.add(value);
        }
        return normalized;
    }

    private void validateDistance(Integer maxDistanceMeters) {
        if (maxDistanceMeters != null
                && (maxDistanceMeters < MIN_DISTANCE_METERS || maxDistanceMeters > MAX_DISTANCE_METERS)) {
            throw validation("최대 이동 거리는 100m 이상 50km 이하여야 합니다.");
        }
    }

    private String normalizeProvider(String value) {
        String provider = requiredText(value, 64, "데이터 제공자").toLowerCase(Locale.ROOT);
        if (!PROVIDER_PATTERN.matcher(provider).matches()) {
            throw validation("데이터 제공자 형식이 올바르지 않습니다.");
        }
        return provider;
    }

    private String requiredText(String value, int maxLength, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw validation(label + "이(가) 필요합니다.");
        }
        if (normalized.length() > maxLength) {
            throw validation(label + "은(는) " + maxLength + "자 이하여야 합니다.");
        }
        return normalized;
    }

    private String optionalText(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw validation(label + "은(는) " + maxLength + "자 이하여야 합니다.");
        }
        return normalized;
    }

    private BigDecimal validateCoordinate(BigDecimal value, BigDecimal min, BigDecimal max, String label) {
        if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw validation(label + " 범위가 올바르지 않습니다.");
        }
        return value;
    }

    private String normalizeSourceUrl(String value) {
        String sourceUrl = optionalText(value, 2048, "출처 URL");
        if (sourceUrl == null) {
            return null;
        }
        try {
            URI uri = new URI(sourceUrl);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw validation("출처 URL은 http 또는 https 주소여야 합니다.");
            }
            return sourceUrl;
        } catch (URISyntaxException exception) {
            throw validation("출처 URL 형식이 올바르지 않습니다.");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private ValidationException validation(String message) {
        return new ValidationException(ErrorCode.VALIDATION_ERROR, message);
    }
}
