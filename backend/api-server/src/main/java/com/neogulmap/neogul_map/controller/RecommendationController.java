package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.config.annotation.CurrentUser;
import com.neogulmap.neogul_map.config.security.PublicInsightRateLimiter;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.domain.recommendation.RecommendationItemType;
import com.neogulmap.neogul_map.dto.ApiResponse;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationInteractionRequest;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationInteractionResponse;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationIntent;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationItemKeyRequest;
import com.neogulmap.neogul_map.dto.recommendation.RecommendationPlaceResponse;
import com.neogulmap.neogul_map.dto.recommendation.UserPreferenceResponse;
import com.neogulmap.neogul_map.dto.recommendation.UserPreferenceUpdateRequest;
import com.neogulmap.neogul_map.dto.recommendation.UserSavedItemRequest;
import com.neogulmap.neogul_map.dto.recommendation.UserSavedItemResponse;
import com.neogulmap.neogul_map.service.recommendation.RecommendationDataService;
import com.neogulmap.neogul_map.service.recommendation.RecommendationQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@ConditionalOnProperty(name = "app.discovery.enabled", havingValue = "true")
@RequiredArgsConstructor
@RequestMapping("/recommendations")
public class RecommendationController {

    private final RecommendationQueryService recommendationQueryService;
    private final RecommendationDataService recommendationDataService;
    private final PublicInsightRateLimiter publicInsightRateLimiter;

    @GetMapping("/places")
    public ResponseEntity<?> getPlaces(
            @RequestParam("intent") RecommendationIntent intent,
            @RequestParam("lat") @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @RequestParam("lng") @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
            @RequestParam(value = "radiusMeters", defaultValue = "3000")
            @Min(RecommendationQueryService.MIN_RADIUS_METERS)
            @Max(RecommendationQueryService.MAX_RADIUS_METERS) int radiusMeters,
            @RequestParam(value = "limit", defaultValue = "8") @Min(1) @Max(20) int limit,
            @CurrentUser(required = false) User currentUser,
            HttpServletRequest request
    ) {
        PublicInsightRateLimiter.Decision rateLimit = publicInsightRateLimiter.tryAcquire(
                request,
                recommendationPermitCost(intent)
        );
        if (!rateLimit.allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(rateLimit.retryAfterSeconds()))
                    .body(Map.of(
                            "success", false,
                            "message", "요청이 많습니다. 잠시 후 다시 시도해 주세요."
                    ));
        }
        RecommendationPlaceResponse response = recommendationQueryService.recommendPlaces(
                currentUser,
                intent,
                latitude,
                longitude,
                radiusMeters,
                limit
        );
        return ResponseEntity.ok(ApiResponse.ok("페로 맞춤 추천 조회 성공", response));
    }

    static int recommendationPermitCost(RecommendationIntent intent) {
        return intent == RecommendationIntent.DATE ? 4 : 1;
    }

    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<UserPreferenceResponse>> getPreferences(@CurrentUser User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                "추천 설정 조회 성공",
                recommendationDataService.getPreference(currentUser)
        ));
    }

    @PutMapping("/preferences")
    public ResponseEntity<ApiResponse<UserPreferenceResponse>> updatePreferences(
            @CurrentUser User currentUser,
            @Valid @RequestBody UserPreferenceUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "추천 설정 저장 성공",
                recommendationDataService.updatePreference(currentUser, request)
        ));
    }

    @GetMapping("/saved-items")
    public ResponseEntity<ApiResponse<List<UserSavedItemResponse>>> getSavedItems(@CurrentUser User currentUser) {
        return ResponseEntity.ok(ApiResponse.ok(
                "저장한 추천 조회 성공",
                recommendationDataService.getSavedItems(currentUser)
        ));
    }

    @PostMapping("/saved-items")
    public ResponseEntity<ApiResponse<UserSavedItemResponse>> saveItem(
            @CurrentUser User currentUser,
            @Valid @RequestBody UserSavedItemRequest request
    ) {
        UserSavedItemResponse response = recommendationDataService.saveItem(currentUser, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("추천 장소 저장 성공", response));
    }

    @DeleteMapping("/saved-items")
    public ResponseEntity<Void> unsaveItem(
            @CurrentUser User currentUser,
            @RequestParam("itemType") RecommendationItemType itemType,
            @RequestParam("provider") String provider,
            @RequestParam("itemId") String itemId
    ) {
        recommendationDataService.unsaveItem(
                currentUser,
                new RecommendationItemKeyRequest(itemType, provider, itemId)
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/interactions")
    public ResponseEntity<ApiResponse<RecommendationInteractionResponse>> recordInteraction(
            @CurrentUser User currentUser,
            @Valid @RequestBody RecommendationInteractionRequest request
    ) {
        RecommendationInteractionResponse response = recommendationDataService.recordInteraction(
                currentUser,
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("추천 행동 기록 성공", response));
    }

    @DeleteMapping("/data")
    public ResponseEntity<Void> clearRecommendationData(@CurrentUser User currentUser) {
        recommendationDataService.clearRecommendationData(currentUser);
        return ResponseEntity.noContent().build();
    }
}
