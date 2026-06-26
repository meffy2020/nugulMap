package com.nugulmap.nativeapp.data.repository

import android.net.Uri
import com.nugulmap.nativeapp.BuildConfig
import com.nugulmap.nativeapp.core.auth.AuthTokenStore
import com.nugulmap.nativeapp.core.auth.OAuthProvider
import com.nugulmap.nativeapp.core.deeplink.OAuthDeepLink
import com.nugulmap.nativeapp.core.auth.PkcePair
import com.nugulmap.nativeapp.data.api.NugulApiClient
import com.nugulmap.nativeapp.data.api.NugulApiService
import com.nugulmap.nativeapp.data.dto.MobileOAuthExchangeRequest
import com.nugulmap.nativeapp.data.dto.UserProfileDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class AuthRepository(
    private val tokenStore: AuthTokenStore,
    private val apiService: NugulApiService = NugulApiClient.service,
) {
    private var pendingPkce: PkcePair? = null

    fun buildAuthorizationUrl(provider: OAuthProvider): String {
        val pkce = PkcePair.create()
        pendingPkce = pkce

        return Uri.parse(BuildConfig.NUGUL_API_BASE_URL)
            .buildUpon()
            .appendEncodedPath("api/oauth2/authorization/${provider.id}")
            .appendQueryParameter("redirect_uri", OAuthDeepLink.CALLBACK_URL)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("code_challenge", pkce.challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
            .toString()
    }

    suspend fun exchangeCallback(callbackUri: Uri): Boolean {
        val code = callbackUri.getQueryParameter("code") ?: error("OAuth code가 없습니다.")
        val verifier = pendingPkce?.verifier ?: error("로그인 세션이 만료되었습니다.")
        val response = apiService.exchangeMobileOAuthCode(
            MobileOAuthExchangeRequest(code = code, codeVerifier = verifier),
        )
        val token = response.data ?: error(response.message ?: "로그인에 실패했습니다.")
        tokenStore.save(token.accessToken, token.refreshToken)
        pendingPkce = null
        return token.profileComplete
    }

    suspend fun currentUser(): UserProfileDto? {
        val token = tokenStore.accessToken()?.takeIf { it.isNotBlank() } ?: return null
        val response = apiService.getCurrentUser(token.bearer())
        if (!response.success) {
            error(response.message ?: "프로필 조회에 실패했습니다.")
        }
        return response.data?.user
    }

    suspend fun completeProfile(nickname: String): UserProfileDto {
        val normalized = nickname.trim()
        require(normalized.isNotBlank()) { "닉네임을 입력해 주세요." }
        val token = tokenStore.accessToken()?.takeIf { it.isNotBlank() } ?: error("로그인이 필요합니다.")
        val response = apiService.completeProfileSetup(
            authorization = token.bearer(),
            nickname = normalized.toRequestBody(TEXT_PLAIN),
        )
        if (!response.success) {
            error(response.message ?: "프로필 설정에 실패했습니다.")
        }
        return response.data?.user ?: error("프로필 설정 응답이 올바르지 않습니다.")
    }

    suspend fun deleteAccount() {
        val token = tokenStore.accessToken()?.takeIf { it.isNotBlank() } ?: error("로그인이 필요합니다.")
        val response = apiService.deleteCurrentUser(token.bearer())
        if (!response.success) {
            error(response.message ?: "계정 삭제에 실패했습니다.")
        }
        logout()
    }

    fun isSignedIn(): Boolean = !tokenStore.accessToken().isNullOrBlank()

    fun logout() {
        pendingPkce = null
        tokenStore.clear()
    }

    private fun String.bearer(): String = "Bearer $this"

    companion object {
        private val TEXT_PLAIN = "text/plain; charset=utf-8".toMediaType()
    }
}
