package com.nugulmap.nativeapp.data.dto

data class ZoneCreatePayload(
    val region: String,
    val type: String,
    val subtype: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val size: String? = null,
    val address: String,
    val user: String,
)

data class ZoneReviewCreateRequest(
    val content: String,
)

data class ReviewReportRequest(
    val reason: String,
    val details: String? = null,
)

data class ZoneReportRequest(
    val reason: String,
    val details: String? = null,
)

data class ZoneReportPayload(
    val report: ZoneReportDto? = null,
)

data class ZoneReportDto(
    val id: Long,
    val zoneId: Int,
    val reason: String,
    val status: String,
    val createdAt: String? = null,
)

data class ReviewReportPayload(
    val report: ReviewReportDto? = null,
)

data class ReviewReportDto(
    val id: Long,
    val reviewId: Int,
    val reason: String,
    val status: String,
    val createdAt: String? = null,
)

data class UserBlockPayload(
    val blockedUserId: Long? = null,
    val unblockedUserId: Long? = null,
)

data class ZoneReviewPayload(
    val reviews: List<ZoneReviewDto> = emptyList(),
    val count: Int = reviews.size,
)

data class ZoneReviewDto(
    val id: Int,
    val zoneId: Int,
    val authorId: Long? = null,
    val authorNickname: String? = null,
    val authorProfileImage: String? = null,
    val content: String,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    val displayAuthor: String
        get() = authorNickname?.takeIf { it.isNotBlank() }
            ?: "익명"
}

data class UserProfilePayload(
    val user: UserProfileDto? = null,
)

data class UserProfileDto(
    val id: Long,
    val email: String,
    val nickname: String? = null,
    val profileImage: String? = null,
    val profileImageUrl: String? = null,
    val createdAt: String? = null,
) {
    val displayName: String
        get() = nickname?.takeIf { it.isNotBlank() } ?: email
}
