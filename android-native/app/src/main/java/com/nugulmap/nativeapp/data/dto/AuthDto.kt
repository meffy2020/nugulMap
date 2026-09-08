package com.nugulmap.nativeapp.data.dto

data class MobileOAuthExchangeRequest(
    val code: String,
    val codeVerifier: String,
)

data class RefreshTokenRequest(
    val refreshToken: String,
)

data class RefreshTokenResponse(
    val success: Boolean = false,
    val message: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
)

data class AuthTokenResponse(
    val accessToken: String,
    val refreshToken: String? = null,
    val profileComplete: Boolean = false,
    val user: AuthUserSummary? = null,
)

data class AccountDeletionPayload(
    val deletedUserId: Long,
    val manualAppleRevocationRequired: Boolean = false,
)

data class AuthUserSummary(
    val id: Long? = null,
    val email: String? = null,
    val nickname: String? = null,
)
