package com.nugulmap.nativeapp.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nugulmap.nativeapp.core.auth.AuthSessionStore
import com.nugulmap.nativeapp.data.dto.AccountDeletionPayload
import com.nugulmap.nativeapp.data.dto.ApiEnvelope
import com.nugulmap.nativeapp.data.dto.RefreshTokenResponse
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountDeletionContractTest {
    @Test
    fun `server deletion response parses the boolean revocation flag`() {
        val type = object : TypeToken<ApiEnvelope<AccountDeletionPayload>>() {}.type
        val response: ApiEnvelope<AccountDeletionPayload> = Gson().fromJson(
            """
            {
              "success": true,
              "message": "계정 삭제가 완료되었습니다.",
              "data": {
                "deletedUserId": 4294967296,
                "manualAppleRevocationRequired": true
              }
            }
            """.trimIndent(),
            type,
        )

        assertTrue(response.success)
        assertEquals(4_294_967_296L, response.data?.deletedUserId)
        assertTrue(response.data?.manualAppleRevocationRequired == true)
    }

    @Test
    fun `successful deletion clears the local session`() = runBlocking {
        val store = FakeAuthSessionStore(access = "access-token", refresh = "refresh-token")

        deleteAccountSession(
            authenticatedRequests = executor(store),
            deleteCurrentUser = { authorization ->
                assertEquals("Bearer access-token", authorization)
                ApiEnvelope(
                    success = true,
                    data = AccountDeletionPayload(
                        deletedUserId = 42L,
                        manualAppleRevocationRequired = false,
                    ),
                )
            },
            clearSession = store::clear,
        )

        assertNull(store.accessToken())
        assertNull(store.refreshToken())
    }

    @Test
    fun `rejected deletion preserves the local session`() = runBlocking {
        val store = FakeAuthSessionStore(access = "access-token", refresh = "refresh-token")

        assertFailsWith<IllegalStateException> {
            deleteAccountSession(
                authenticatedRequests = executor(store),
                deleteCurrentUser = {
                    ApiEnvelope<AccountDeletionPayload>(
                        success = false,
                        message = "계정 삭제에 실패했습니다.",
                    )
                },
                clearSession = store::clear,
            )
        }

        assertEquals("access-token", store.accessToken())
        assertEquals("refresh-token", store.refreshToken())
    }

    @Test
    fun `network failure preserves the local session`() = runBlocking {
        val store = FakeAuthSessionStore(access = "access-token", refresh = "refresh-token")

        assertFailsWith<IOException> {
            deleteAccountSession(
                authenticatedRequests = executor(store),
                deleteCurrentUser = { throw IOException("offline") },
                clearSession = store::clear,
            )
        }

        assertEquals("access-token", store.accessToken())
        assertEquals("refresh-token", store.refreshToken())
    }

    private fun executor(store: AuthSessionStore) = AuthenticatedRequestExecutor(
        tokenStore = store,
        refreshSession = { _: String ->
            RefreshTokenResponse(success = false)
        },
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
