package com.nugulmap.nativeapp.core.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OAuthPendingRequestCoordinatorTest {
    @Test
    fun `client state uses 32 random bytes encoded as 43 URL-safe characters`() {
        val states = List(32) { OAuthClientState.create() }

        states.forEach { state ->
            assertEquals(43, state.length)
            assertTrue(state.matches(Regex("^[A-Za-z0-9_-]{43}$")))
        }
        assertEquals(states.size, states.toSet().size)
    }

    @Test
    fun `PKCE verifier and challenge use URL-safe SHA-256 values`() {
        val pair = PkcePair.create()

        assertEquals(43, pair.verifier.length)
        assertEquals(43, pair.challenge.length)
        assertTrue(pair.verifier.matches(Regex("^[A-Za-z0-9_-]{43}$")))
        assertTrue(pair.challenge.matches(Regex("^[A-Za-z0-9_-]{43}$")))
        assertNotEquals(pair.verifier, pair.challenge)
    }

    @Test
    fun `missing or mismatched state is rejected without consuming the valid request`() {
        val harness = PendingStoreHarness()
        harness.store.savePendingOAuthRequest(PENDING_REQUEST)

        assertIs<PendingOAuthRequestConsumption.MissingState>(
            harness.store.consumePendingOAuthRequest(null, NOW, TTL),
        )
        assertIs<PendingOAuthRequestConsumption.StateMismatch>(
            harness.store.consumePendingOAuthRequest("x".repeat(43), NOW, TTL),
        )
        assertIs<PendingOAuthRequestConsumption.Accepted>(
            harness.store.consumePendingOAuthRequest(CLIENT_STATE, NOW, TTL),
        )
    }

    @Test
    fun `expired request is cleared and cannot be reused`() {
        val harness = PendingStoreHarness()
        harness.store.savePendingOAuthRequest(
            PENDING_REQUEST.copy(createdAtEpochMillis = NOW - TTL),
        )

        assertIs<PendingOAuthRequestConsumption.Expired>(
            harness.store.consumePendingOAuthRequest(CLIENT_STATE, NOW, TTL),
        )
        assertIs<PendingOAuthRequestConsumption.NoPendingRequest>(
            harness.store.consumePendingOAuthRequest(CLIENT_STATE, NOW, TTL),
        )
    }

    @Test
    fun `accepted request is consumed exactly once`() {
        val harness = PendingStoreHarness()
        harness.store.savePendingOAuthRequest(PENDING_REQUEST)

        val accepted = assertIs<PendingOAuthRequestConsumption.Accepted>(
            harness.store.consumePendingOAuthRequest(CLIENT_STATE, NOW, TTL),
        )

        assertEquals(CODE_VERIFIER, accepted.codeVerifier)
        assertIs<PendingOAuthRequestConsumption.NoPendingRequest>(
            harness.store.consumePendingOAuthRequest(CLIENT_STATE, NOW, TTL),
        )
    }

    @Test
    fun `state comparison accepts only the exact value`() {
        assertTrue(constantTimeEquals(CLIENT_STATE, CLIENT_STATE))
        assertFalse(constantTimeEquals(CLIENT_STATE, "t".repeat(43)))
        assertFalse(constantTimeEquals(CLIENT_STATE, "s".repeat(42)))
    }

    private class PendingStoreHarness {
        private var pending: PendingOAuthRequest? = null
        val store = OAuthPendingRequestCoordinator(
            read = { pending },
            write = { pending = it },
        )
    }

    companion object {
        private const val NOW = 1_700_000_000_000L
        private const val TTL = 10 * 60 * 1000L
        private val CODE_VERIFIER = "v".repeat(43)
        private val CLIENT_STATE = "s".repeat(43)
        private val PENDING_REQUEST = PendingOAuthRequest(
            codeVerifier = CODE_VERIFIER,
            clientState = CLIENT_STATE,
            createdAtEpochMillis = NOW - 1_000L,
        )
    }
}
