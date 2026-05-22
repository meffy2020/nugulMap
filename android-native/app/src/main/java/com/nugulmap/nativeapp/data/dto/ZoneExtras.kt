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

data class ZoneReviewPayload(
    val reviews: List<ZoneReviewDto> = emptyList(),
    val count: Int = reviews.size,
)

data class ZoneReviewDto(
    val id: Int,
    val zoneId: Int,
    val authorId: Long? = null,
    val authorNickname: String? = null,
    val authorEmail: String? = null,
    val authorProfileImage: String? = null,
    val content: String,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    val displayAuthor: String
        get() = authorNickname?.takeIf { it.isNotBlank() }
            ?: authorEmail?.takeIf { it.isNotBlank() }
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
