package com.nugulmap.nativeapp.data.repository

import com.nugulmap.nativeapp.core.auth.AuthSessionStore
import com.nugulmap.nativeapp.data.api.NugulApiClient
import com.nugulmap.nativeapp.data.api.NugulApiService
import com.nugulmap.nativeapp.data.dto.RefreshTokenRequest
import com.nugulmap.nativeapp.data.dto.RefreshTokenResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

class AuthenticatedRequestExecutor internal constructor(
    private val tokenStore: AuthSessionStore,
    private val refreshSession: suspend (String) -> RefreshTokenResponse,
) {
    constructor(
        tokenStore: AuthSessionStore,
        apiService: NugulApiService = NugulApiClient.service,
    ) : this(tokenStore, { refreshToken ->
        apiService.refreshSession(RefreshTokenRequest(refreshToken))
    })

    private val refreshMutex = Mutex()

    fun hasSession(): Boolean = !tokenStore.accessToken().isNullOrBlank() ||
        !tokenStore.refreshToken().isNullOrBlank()

    fun hasAccessToken(): Boolean = !tokenStore.accessToken().isNullOrBlank()

    suspend fun <T> execute(request: suspend (accessToken: String) -> T): T {
        val storedAccessToken = tokenStore.accessToken()?.takeIf(String::isNotBlank)
        val accessToken = storedAccessToken ?: refreshAccessToken(failedAccessToken = null)

        return try {
            request(accessToken)
        } catch (error: HttpException) {
            if (error.code() != 401) throw error
            if (storedAccessToken == null) expireSession()

            val refreshedAccessToken = refreshAccessToken(failedAccessToken = accessToken)
            try {
                request(refreshedAccessToken)
            } catch (retryError: HttpException) {
                if (retryError.code() == 401) expireSession()
                throw retryError
            }
        }
    }

    private suspend fun refreshAccessToken(failedAccessToken: String?): String = refreshMutex.withLock {
        val currentAccessToken = tokenStore.accessToken()?.takeIf(String::isNotBlank)
        if (currentAccessToken != null && currentAccessToken != failedAccessToken) {
            return@withLock currentAccessToken
        }

        val refreshToken = tokenStore.refreshToken()?.takeIf(String::isNotBlank)
            ?: expireSession()

        val response = try {
            refreshSession(refreshToken)
        } catch (error: HttpException) {
            if (error.code() == 400 || error.code() == 401) {
                expireSession()
            }
            throw error
        }

        val newAccessToken = response.accessToken?.takeIf(String::isNotBlank)
        if (!response.success || newAccessToken == null) {
            expireSession()
        }

        tokenStore.save(
            accessToken = newAccessToken,
            refreshToken = response.refreshToken?.takeIf(String::isNotBlank) ?: refreshToken,
        )
        newAccessToken
    }

    private fun expireSession(): Nothing {
        tokenStore.clear()
        throw AuthSessionExpiredException()
    }
}

class AuthSessionExpiredException : IllegalStateException("로그인이 만료되었습니다. 다시 로그인해 주세요.")
