package com.nugulmap.nativeapp.ui.map

import com.nugulmap.nativeapp.data.dto.UserProfileDto
import com.nugulmap.nativeapp.data.dto.HotplaceInsightPayload
import com.nugulmap.nativeapp.data.dto.EventInsightPayload
import com.nugulmap.nativeapp.data.dto.InsightStatusPayload
import com.nugulmap.nativeapp.data.dto.LocationLetterNearbyPayload
import com.nugulmap.nativeapp.data.dto.RecommendationIntent
import com.nugulmap.nativeapp.data.dto.RecommendationPlacesPayload
import com.nugulmap.nativeapp.data.dto.RecommendationPreferenceDto
import com.nugulmap.nativeapp.data.dto.ZoneDto
import com.nugulmap.nativeapp.data.dto.ZoneReviewDto

data class MapUiState(
    val isLoading: Boolean = false,
    val zones: List<ZoneDto> = emptyList(),
    val hotplaceInsight: HotplaceInsightPayload = HotplaceInsightPayload(),
    val eventInsight: EventInsightPayload = EventInsightPayload(),
    val insightStatus: InsightStatusPayload? = null,
    val isInsightLoading: Boolean = false,
    val recommendationIntent: RecommendationIntent = RecommendationIntent.DATE,
    val recommendations: RecommendationPlacesPayload = RecommendationPlacesPayload.empty(),
    val recommendationViewportKey: String? = null,
    val isRecommendationLoading: Boolean = false,
    val recommendationErrorMessage: String? = null,
    val recommendationMessage: String? = null,
    val recommendationPreference: RecommendationPreferenceDto? = null,
    val savedRecommendationKeys: Set<String> = emptySet(),
    val isRecommendationActionLoading: Boolean = false,
    val locationLetters: LocationLetterNearbyPayload = LocationLetterNearbyPayload.empty(),
    val locationLetterLatitude: Double? = null,
    val locationLetterLongitude: Double? = null,
    val isLocationLetterLoading: Boolean = false,
    val isLocationLetterSubmitting: Boolean = false,
    val isLocationLetterLikeUpdating: Boolean = false,
    val isLocationLetterModerating: Boolean = false,
    val locationLetterErrorMessage: String? = null,
    val locationLetterMessage: String? = null,
    val selectedZoneId: Int? = null,
    val selectedZoneReviews: List<ZoneReviewDto> = emptyList(),
    val isReviewLoading: Boolean = false,
    val isReviewSubmitting: Boolean = false,
    val isReviewModerating: Boolean = false,
    val reviewErrorMessage: String? = null,
    val reviewModerationMessage: String? = null,
    val myZones: List<ZoneDto> = emptyList(),
    val currentUser: UserProfileDto? = null,
    val errorMessage: String? = null,
    val isSignedIn: Boolean = false,
    val isAuthLoading: Boolean = false,
    val isActionLoading: Boolean = false,
    val authMessage: String? = null,
    val actionMessage: String? = null,
)
