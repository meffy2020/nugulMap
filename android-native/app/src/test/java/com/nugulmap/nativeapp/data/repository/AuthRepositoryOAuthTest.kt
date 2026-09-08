package com.nugulmap.nativeapp.data.repository

import com.nugulmap.nativeapp.core.auth.AuthStore
import com.nugulmap.nativeapp.core.auth.OAuthPendingRequestCoordinator
import com.nugulmap.nativeapp.core.auth.PendingOAuthRequest
import com.nugulmap.nativeapp.core.auth.PendingOAuthRequestConsumption
import com.nugulmap.nativeapp.data.dto.ApiEnvelope
import com.nugulmap.nativeapp.data.dto.AuthTokenResponse
import com.nugulmap.nativeapp.data.dto.MobileOAuthExchangeRequest
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthRepositoryOAuthTest {
    @Test
    fun `authorization URL carries the backend client state contract`() {
        val url = buildOAuthAuthorizationUrl(
            baseUrl = "https://api.nugulmap.com",
            providerId = "kakao",
            redirectUri = "nugulmap://oauth/callback",
            codeChallenge = CODE_CHALLENGE,
            clientState = CLIENT_STATE,
        ).toHttpUrl()

        assertEquals("/api/oauth2/authorization/kakao", url.encodedPath)
        assertEquals("nugulmap://oauth/callback", url.queryParameter("redirect_uri"))
        assertEquals("code", url.queryParameter("response_type"))
        assertEquals(CODE_CHALLENGE, url.queryParameter("code_challenge"))
        assertEquals("S256", url.queryParameter("code_challenge_method"))
        assertEquals(CLIENT_STATE, url.queryParameter(OAUTH_CLIENT_STATE_QUERY_PARAMETER))
    }

    @Test
    fun `missing callback state is blocked before token exchange`() = runBlocking {
        val fixture = fixture()

        assertFailsWith<IllegalStateException> {
            fixture.repository.exchangeCallback(code = "oauth-code", clientState = null)
        }

        assertEquals(0, fixture.exchangeCalls)
        assertNull(fixture.store.accessToken())
    }

    @Test
    fun `mismatched callback state is blocked before exchange and keeps legitimate callback usable`() = runBlocking {
        val fixture = fixture()

        assertFailsWith<IllegalStateException> {
            fixture.repository.exchangeCallback(code = "attacker-code", clientState = "x".repeat(43))
        }
        assertEquals(0, fixture.exchangeCalls)

        assertTrue(
            fixture.repository.exchangeCallback(code = "legitimate-code", clientState = CLIENT_STATE),
        )
        assertEquals(1, fixture.exchangeCalls)
        assertEquals("legitimate-code", fixture.lastRequest?.code)
        assertEquals(CODE_VERIFIER, fixture.lastRequest?.codeVerifier)
    }

    @Test
    fun `expired callback state is blocked and cleared before token exchange`() = runBlocking {
        val fixture = fixture(createdAt = NOW - OAUTH_PENDING_REQUEST_TTL_MILLIS)

        assertFailsWith<IllegalStateException> {
            fixture.repository.exchangeCallback(code = "oauth-code", clientState = CLIENT_STATE)
        }
        assertEquals(0, fixture.exchangeCalls)

        assertFailsWith<IllegalStateException> {
            fixture.repository.exchangeCallback(code = "oauth-code", clientState = CLIENT_STATE)
        }
        assertEquals(0, fixture.exchangeCalls)
    }

    @Test
    fun `callback remains valid through a long mfa within the ten minute window`() = runBlocking {
        val fixture = fixture(createdAt = NOW - OAUTH_PENDING_REQUEST_TTL_MILLIS + 1_000L)

        assertTrue(fixture.repository.exchangeCallback(code = "post-mfa-code", clientState = CLIENT_STATE))
        assertEquals(1, fixture.exchangeCalls)
        assertEquals("post-mfa-code", fixture.lastRequest?.code)
    }

    @Test
    fun `valid callback consumes state before exchange and cannot be replayed`() = runBlocking {
        val fixture = fixture()

        assertTrue(fixture.repository.exchangeCallback(code = "oauth-code", clientState = CLIENT_STATE))
        assertEquals("access-token", fixture.store.accessToken())
        assertEquals("refresh-token", fixture.store.refreshToken())

        assertFailsWith<IllegalStateException> {
            fixture.repository.exchangeCallback(code = "oauth-code", clientState = CLIENT_STATE)
        }
        assertEquals(1, fixture.exchangeCalls)
    }

    @Test
    fun `provider failure with valid state consumes request without token exchange`() = runBlocking {
        val fixture = fixture()

        assertFailsWith<IllegalStateException> {
            fixture.repository.exchangeCallback(
                code = null,
                clientState = CLIENT_STATE,
                providerError = "access_denied",
            )
        }
        assertEquals(0, fixture.exchangeCalls)

        assertFailsWith<IllegalStateException> {
            fixture.repository.exchangeCallback(code = "late-code", clientState = CLIENT_STATE)
        }
        assertEquals(0, fixture.exchangeCalls)
    }

    @Test
    fun `server processing failure callback with echoed state is handled without token exchange`() = runBlocking {
        val fixture = fixture()

        assertFailsWith<IllegalStateException> {
            fixture.repository.exchangeCallback(
                code = null,
                clientState = CLIENT_STATE,
                providerError = "oauth2_processing_failed",
            )
        }

        assertEquals(0, fixture.exchangeCalls)
        assertNull(fixture.store.accessToken())
        assertFailsWith<IllegalStateException> {
            fixture.repository.exchangeCallback(code = "late-code", clientState = CLIENT_STATE)
        }
        assertEquals(0, fixture.exchangeCalls)
    }

    private fun fixture(createdAt: Long = NOW - 1_000L): OAuthFixture {
        val store = FakeAuthStore()
        store.savePendingOAuthRequest(
            PendingOAuthRequest(
                codeVerifier = CODE_VERIFIER,
                clientState = CLIENT_STATE,
                createdAtEpochMillis = createdAt,
            ),
        )
        return OAuthFixture(store)
    }

    private class OAuthFixture(
        val store: FakeAuthStore,
    ) {
        var exchangeCalls: Int = 0
        var lastRequest: MobileOAuthExchangeRequest? = null

        val repository = AuthRepository(
            tokenStore = store,
            exchangeMobileOAuthCode = { request ->
                exchangeCalls += 1
                lastRequest = request
                ApiEnvelope(
                    success = true,
                    data = AuthTokenResponse(
                        accessToken = "access-token",
                        refreshToken = "refresh-token",
                        profileComplete = true,
                    ),
                )
            },
            currentTimeMillis = { NOW },
        )
    }

    private class FakeAuthStore : AuthStore {
        private var access: String? = null
        private var refresh: String? = null
        private var pending: PendingOAuthRequest? = null
        private val pendingCoordinator = OAuthPendingRequestCoordinator(
            read = { pending },
            write = { pending = it },
        )

        override fun save(accessToken: String, refreshToken: String?) {
            access = accessToken
            refresh = refreshToken
        }

        override fun accessToken(): String? = access

        override fun refreshToken(): String? = refresh

        override fun clear() {
            access = null
            refresh = null
            pending = null
        }

        override fun savePendingOAuthRequest(request: PendingOAuthRequest) {
            pendingCoordinator.savePendingOAuthRequest(request)
        }

        override fun consumePendingOAuthRequest(
            callbackState: String?,
            nowEpochMillis: Long,
            ttlMillis: Long,
        ): PendingOAuthRequestConsumption = pendingCoordinator.consumePendingOAuthRequest(
            callbackState = callbackState,
            nowEpochMillis = nowEpochMillis,
            ttlMillis = ttlMillis,
        )
    }

    companion object {
        private const val NOW = 1_700_000_000_000L
        private val CODE_VERIFIER = "v".repeat(43)
        private val CODE_CHALLENGE = "c".repeat(43)
        private val CLIENT_STATE = "s".repeat(43)
    }
}
