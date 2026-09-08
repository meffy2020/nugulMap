package com.nugulmap.nativeapp.data.repository

import com.nugulmap.nativeapp.core.auth.AuthSessionStore
import com.nugulmap.nativeapp.data.dto.RefreshTokenResponse
import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AuthenticatedRequestExecutorTest {
    @Test
    fun `401 refreshes tokens and retries exactly once`() = runBlocking {
        val store = FakeAuthSessionStore(access = "expired-access", refresh = "valid-refresh")
        var refreshCalls = 0
        var requestCalls = 0
        val executor = AuthenticatedRequestExecutor(
            tokenStore = store,
            refreshSession = {
                refreshCalls += 1
                assertEquals("valid-refresh", it)
                RefreshTokenResponse(
                    success = true,
                    accessToken = "fresh-access",
                    refreshToken = "fresh-refresh",
                )
            },
        )

        val result = executor.execute { accessToken ->
            requestCalls += 1
            if (requestCalls == 1) throw httpError(401)
            accessToken
        }

        assertEquals("fresh-access", result)
        assertEquals("fresh-access", store.accessToken())
        assertEquals("fresh-refresh", store.refreshToken())
        assertEquals(1, refreshCalls)
        assertEquals(2, requestCalls)
    }

    @Test
    fun `expired refresh token clears the local session`() = runBlocking {
        val store = FakeAuthSessionStore(access = "expired-access", refresh = "expired-refresh")
        val executor = AuthenticatedRequestExecutor(
            tokenStore = store,
            refreshSession = { throw httpError(401) },
        )

        assertFailsWith<AuthSessionExpiredException> {
            executor.execute { throw httpError(401) }
        }
        assertNull(store.accessToken())
        assertNull(store.refreshToken())
    }

    @Test
    fun `temporary refresh network failure preserves the session`() = runBlocking {
        val store = FakeAuthSessionStore(access = "expired-access", refresh = "valid-refresh")
        val executor = AuthenticatedRequestExecutor(
            tokenStore = store,
            refreshSession = { throw IOException("offline") },
        )

        assertFailsWith<IOException> {
            executor.execute { throw httpError(401) }
        }
        assertEquals("expired-access", store.accessToken())
        assertEquals("valid-refresh", store.refreshToken())
    }

    @Test
    fun `401 after the single retry expires the session`() = runBlocking {
        val store = FakeAuthSessionStore(access = "expired-access", refresh = "valid-refresh")
        var refreshCalls = 0
        var requestCalls = 0
        val executor = AuthenticatedRequestExecutor(
            tokenStore = store,
            refreshSession = {
                refreshCalls += 1
                RefreshTokenResponse(
                    success = true,
                    accessToken = "fresh-access",
                    refreshToken = "fresh-refresh",
                )
            },
        )

        assertFailsWith<AuthSessionExpiredException> {
            executor.execute {
                requestCalls += 1
                throw httpError(401)
            }
        }
        assertEquals(1, refreshCalls)
        assertEquals(2, requestCalls)
        assertNull(store.accessToken())
        assertNull(store.refreshToken())
    }

    private fun httpError(statusCode: Int): HttpException = HttpException(
        Response.error<Unit>(statusCode, "{}".toResponseBody()),
    )

    private class FakeAuthSessionStore(
        private var access: String?,
        private var refresh: String?,
    ) : AuthSessionStore {
        override fun save(accessToken: String, refreshToken: String?) {
            access = accessToken
            refresh = refreshToken
        }

        override fun accessToken(): String? = access

        override fun refreshToken(): String? = refresh

        override fun clear() {
            access = null
            refresh = null
        }
    }
}
