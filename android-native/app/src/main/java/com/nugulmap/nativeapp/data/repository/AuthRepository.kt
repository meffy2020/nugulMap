package com.nugulmap.nativeapp.data.repository

import android.net.Uri
import com.nugulmap.nativeapp.BuildConfig
import com.nugulmap.nativeapp.core.auth.AuthStore
import com.nugulmap.nativeapp.core.auth.OAuthClientState
import com.nugulmap.nativeapp.core.auth.OAuthProvider
import com.nugulmap.nativeapp.core.auth.PendingOAuthRequest
import com.nugulmap.nativeapp.core.auth.PendingOAuthRequestConsumption
import com.nugulmap.nativeapp.core.deeplink.OAuthDeepLink
import com.nugulmap.nativeapp.core.auth.PkcePair
import com.nugulmap.nativeapp.data.api.NugulApiClient
import com.nugulmap.nativeapp.data.api.NugulApiService
import com.nugulmap.nativeapp.data.dto.AccountDeletionPayload
import com.nugulmap.nativeapp.data.dto.ApiEnvelope
import com.nugulmap.nativeapp.data.dto.AuthTokenResponse
import com.nugulmap.nativeapp.data.dto.MobileOAuthExchangeRequest
import com.nugulmap.nativeapp.data.dto.UserProfileDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl

class AuthRepository(
    private val tokenStore: AuthStore,
    private val apiService: NugulApiService = NugulApiClient.service,
    private val authenticatedRequests: AuthenticatedRequestExecutor =
        AuthenticatedRequestExecutor(tokenStore, apiService),
    private val exchangeMobileOAuthCode: suspend (MobileOAuthExchangeRequest) -> ApiEnvelope<AuthTokenResponse> =
        apiService::exchangeMobileOAuthCode,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    fun buildAuthorizationUrl(provider: OAuthProvider): String {
        val pkce = PkcePair.create()
        val clientState = OAuthClientState.create()
        val authorizationUrl = buildOAuthAuthorizationUrl(
            baseUrl = BuildConfig.NUGUL_API_BASE_URL,
            providerId = provider.id,
            redirectUri = OAuthDeepLink.CALLBACK_URL,
            codeChallenge = pkce.challenge,
            clientState = clientState,
        )
        tokenStore.savePendingOAuthRequest(
            PendingOAuthRequest(
                codeVerifier = pkce.verifier,
                clientState = clientState,
                createdAtEpochMillis = currentTimeMillis(),
            ),
        )
        return authorizationUrl
    }

    suspend fun exchangeCallback(callbackUri: Uri): Boolean = exchangeCallback(
        code = callbackUri.getQueryParameter("code"),
        clientState = callbackUri.getQueryParameter(OAUTH_CLIENT_STATE_QUERY_PARAMETER),
        providerError = callbackUri.getQueryParameter("error"),
    )

    internal suspend fun exchangeCallback(
        code: String?,
        clientState: String?,
        providerError: String? = null,
    ): Boolean {
        val verifier = when (
            val result = tokenStore.consumePendingOAuthRequest(
                callbackState = clientState,
                nowEpochMillis = currentTimeMillis(),
                ttlMillis = OAUTH_PENDING_REQUEST_TTL_MILLIS,
            )
        ) {
            is PendingOAuthRequestConsumption.Accepted -> result.codeVerifier
            PendingOAuthRequestConsumption.NoPendingRequest ->
                error("로그인 요청이 없거나 이미 처리되었습니다. 다시 로그인해 주세요.")
            PendingOAuthRequestConsumption.MissingState ->
                error("로그인 확인 정보가 없습니다. 다시 로그인해 주세요.")
            PendingOAuthRequestConsumption.StateMismatch ->
                error("로그인 요청이 일치하지 않습니다. 다시 로그인해 주세요.")
            PendingOAuthRequestConsumption.Expired ->
                error("로그인 요청이 만료되었습니다. 다시 로그인해 주세요.")
        }

        if (!providerError.isNullOrBlank()) {
            error("소셜 로그인 인증에 실패했습니다. 다시 시도해 주세요.")
        }

        val normalizedCode = code?.trim()?.takeIf(String::isNotEmpty)
            ?: error("OAuth code가 없습니다.")
        val response = exchangeMobileOAuthCode(
            MobileOAuthExchangeRequest(code = normalizedCode, codeVerifier = verifier),
        )
        val token = response.data ?: error(response.message ?: "로그인에 실패했습니다.")
        tokenStore.save(token.accessToken, token.refreshToken)
        return token.profileComplete
    }

    suspend fun currentUser(): UserProfileDto? {
        if (!authenticatedRequests.hasSession()) return null
        val response = authenticatedRequests.execute { token ->
            apiService.getCurrentUser(token.bearer())
        }
        if (!response.success) {
            error(response.message ?: "프로필 조회에 실패했습니다.")
        }
        return response.data?.user
    }

    suspend fun completeProfile(nickname: String): UserProfileDto {
        val normalized = nickname.trim()
        require(normalized.isNotBlank()) { "닉네임을 입력해 주세요." }
        val response = authenticatedRequests.execute { token ->
            apiService.completeProfileSetup(
                authorization = token.bearer(),
                nickname = normalized.toRequestBody(TEXT_PLAIN),
            )
        }
        if (!response.success) {
            error(response.message ?: "프로필 설정에 실패했습니다.")
        }
        return response.data?.user ?: error("프로필 설정 응답이 올바르지 않습니다.")
    }

    suspend fun deleteAccount() {
        deleteAccountSession(
            authenticatedRequests = authenticatedRequests,
            deleteCurrentUser = apiService::deleteCurrentUser,
            clearSession = tokenStore::clear,
        )
    }

    fun isSignedIn(): Boolean = authenticatedRequests.hasSession()

    fun logout() {
        tokenStore.clear()
    }

    private fun String.bearer(): String = "Bearer $this"

    companion object {
        private val TEXT_PLAIN = "text/plain; charset=utf-8".toMediaType()
    }
}

internal fun buildOAuthAuthorizationUrl(
    baseUrl: String,
    providerId: String,
    redirectUri: String,
    codeChallenge: String,
    clientState: String,
): String = baseUrl.trim().trimEnd('/').toHttpUrl()
    .newBuilder()
    .addPathSegments("api/oauth2/authorization")
    .addPathSegment(providerId)
    .addQueryParameter("redirect_uri", redirectUri)
    .addQueryParameter("response_type", "code")
    .addQueryParameter("code_challenge", codeChallenge)
    .addQueryParameter("code_challenge_method", "S256")
    .addQueryParameter(OAUTH_CLIENT_STATE_QUERY_PARAMETER, clientState)
    .build()
    .toString()

internal const val OAUTH_CLIENT_STATE_QUERY_PARAMETER = "client_state"
internal const val OAUTH_PENDING_REQUEST_TTL_MILLIS = 10 * 60 * 1000L

internal suspend fun deleteAccountSession(
    authenticatedRequests: AuthenticatedRequestExecutor,
    deleteCurrentUser: suspend (authorization: String) -> ApiEnvelope<AccountDeletionPayload>,
    clearSession: () -> Unit,
) {
    val response = authenticatedRequests.execute { token ->
        deleteCurrentUser("Bearer $token")
    }
    if (!response.success) {
        error(response.message ?: "계정 삭제에 실패했습니다.")
    }
    clearSession()
}
