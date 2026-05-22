package com.nugulmap.nativeapp.data.dto

data class MobileOAuthExchangeRequest(
    val code: String,
    val codeVerifier: String,
)

data class AuthTokenResponse(
    val accessToken: String,
    val refreshToken: String? = null,
    val profileComplete: Boolean = false,
    val user: AuthUserSummary? = null,
)

data class AuthUserSummary(
    val id: Long? = null,
    val email: String? = null,
    val nickname: String? = null,
)
