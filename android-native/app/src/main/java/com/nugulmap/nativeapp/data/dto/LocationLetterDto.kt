package com.nugulmap.nativeapp.data.dto

data class LocationLetterNearbyPayload(
    val letters: List<LocationLetterDto> = emptyList(),
    val count: Int = letters.size,
    val radiusMeters: Int = 180,
) {
    companion object {
        fun empty(): LocationLetterNearbyPayload = LocationLetterNearbyPayload()
    }
}

data class LocationLetterDto(
    val id: Long,
    val authorId: Long? = null,
    val authorLabel: String = "익명사용자",
    val content: String,
    val mine: Boolean = false,
    val createdAt: String? = null,
    val likeCount: Long = 0,
    val likedByMe: Boolean = false,
)

data class LocationLetterLikeState(
    val letterId: Long,
    val likeCount: Long,
    val likedByMe: Boolean,
)

data class LocationLetterCreateRequest(
    val latitude: Double,
    val longitude: Double,
    val content: String,
)

data class LocationLetterPayload(
    val letter: LocationLetterDto? = null,
)

data class LocationLetterReportPayload(
    val report: LocationLetterReportDto? = null,
)

data class LocationLetterReportDto(
    val id: Long,
    val letterId: Long,
    val reason: String,
    val status: String,
    val createdAt: String? = null,
)
