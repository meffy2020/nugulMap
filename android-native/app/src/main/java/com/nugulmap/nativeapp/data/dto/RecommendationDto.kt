package com.nugulmap.nativeapp.data.dto

import java.util.Locale

enum class RecommendationIntent(val apiValue: String, val label: String) {
    CAFE("CAFE", "카페"),
    RESTAURANT("RESTAURANT", "맛집"),
    DATE("DATE", "데이트"),
}

enum class RecommendationEventType {
    IMPRESSION,
    DETAIL_OPEN,
    SAVE,
    UNSAVE,
    ROUTE,
    NOT_INTERESTED,
}

data class RecommendationPlacesPayload(
    val requestId: String = "",
    val modelVersion: String = "",
    val dataStatus: String = "PROVIDER_UNAVAILABLE",
    val personalizationMode: String = "CONTEXT_ONLY",
    val intent: String = RecommendationIntent.DATE.apiValue,
    val eligibleCandidateCount: Int = 0,
    val generatedAt: String? = null,
    val items: List<RecommendationPlaceDto> = emptyList(),
) {
    val isPersonalized: Boolean
        get() = personalizationMode == "PERSONALIZED"

    companion object {
        fun empty(intent: RecommendationIntent = RecommendationIntent.DATE) = RecommendationPlacesPayload(
            intent = intent.apiValue,
        )
    }
}

data class RecommendationPlaceDto(
    val id: String = "",
    val provider: String = "UNKNOWN",
    val name: String = "",
    val category: String = "",
    val address: String = "",
    val latitude: Double = Double.NaN,
    val longitude: Double = Double.NaN,
    val distanceMeters: Int? = null,
    val score: Double = 0.0,
    val reasonCodes: List<String> = emptyList(),
    val sourceUrl: String = "",
) {
    val stableKey: String
        get() = recommendationItemKey(provider, id)

    val mapKey: String
        get() = "recommendation:$stableKey"

    val isUsable: Boolean
        get() = id.isNotBlank() &&
            provider.isNotBlank() &&
            name.isNotBlank() &&
            latitude.isFinite() && latitude in -90.0..90.0 &&
            longitude.isFinite() && longitude in -180.0..180.0
}

data class RecommendationInteractionRequest(
    val requestId: String,
    val itemType: String = "PLACE",
    val provider: String,
    val itemId: String,
    val eventType: String,
    val intent: String,
    val contextRegion: String? = null,
    val modelVersion: String,
    val occurredAt: String? = null,
)

data class RecommendationInteractionResponse(
    val id: Long? = null,
    val requestId: String = "",
    val itemType: String = "PLACE",
    val provider: String = "",
    val itemId: String = "",
    val eventType: String = "",
    val intent: String = "",
    val contextRegion: String? = null,
    val modelVersion: String = "",
    val occurredAt: String? = null,
)

data class SavedRecommendationRequest(
    val itemType: String = "PLACE",
    val provider: String,
    val itemId: String,
    val title: String,
    val category: String,
    val lat: Double,
    val lng: Double,
    val sourceUrl: String?,
)

data class SavedRecommendationDto(
    val id: Long? = null,
    val itemType: String = "PLACE",
    val provider: String = "",
    val itemId: String = "",
    val title: String = "",
    val category: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val sourceUrl: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    val stableKey: String
        get() = recommendationItemKey(provider, itemId)
}

data class RecommendationPreferenceUpdateRequest(
    val personalizationEnabled: Boolean,
    val preferredTags: Set<String> = emptySet(),
    val dislikedTags: Set<String> = emptySet(),
    val budgetLevel: String? = null,
    val maxDistanceMeters: Int? = null,
)

data class RecommendationPreferenceDto(
    val personalizationEnabled: Boolean = false,
    val preferredTags: Set<String> = emptySet(),
    val dislikedTags: Set<String> = emptySet(),
    val budgetLevel: String? = null,
    val maxDistanceMeters: Int? = null,
    val updatedAt: String? = null,
) {
    fun withPersonalizationEnabled(enabled: Boolean) = RecommendationPreferenceUpdateRequest(
        personalizationEnabled = enabled,
        preferredTags = preferredTags,
        dislikedTags = dislikedTags,
        budgetLevel = budgetLevel,
        maxDistanceMeters = maxDistanceMeters,
    )
}

internal fun recommendationItemKey(provider: String, itemId: String): String =
    "${provider.trim().lowercase(Locale.ROOT)}:${itemId.trim()}"

internal const val MAX_PERO_RECOMMENDATIONS = 8
