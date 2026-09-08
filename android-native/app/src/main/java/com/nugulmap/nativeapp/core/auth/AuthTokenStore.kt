package com.nugulmap.nativeapp.core.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

interface AuthSessionStore {
    fun save(accessToken: String, refreshToken: String?)
    fun accessToken(): String?
    fun refreshToken(): String?
    fun clear()
}

interface OAuthPendingRequestStore {
    fun savePendingOAuthRequest(request: PendingOAuthRequest)

    fun consumePendingOAuthRequest(
        callbackState: String?,
        nowEpochMillis: Long,
        ttlMillis: Long,
    ): PendingOAuthRequestConsumption
}

interface AuthStore : AuthSessionStore, OAuthPendingRequestStore

data class PendingOAuthRequest(
    val codeVerifier: String,
    val clientState: String,
    val createdAtEpochMillis: Long,
)

sealed interface PendingOAuthRequestConsumption {
    data class Accepted(val codeVerifier: String) : PendingOAuthRequestConsumption
    data object NoPendingRequest : PendingOAuthRequestConsumption
    data object MissingState : PendingOAuthRequestConsumption
    data object StateMismatch : PendingOAuthRequestConsumption
    data object Expired : PendingOAuthRequestConsumption
}

internal class OAuthPendingRequestCoordinator(
    private val read: () -> PendingOAuthRequest?,
    private val write: (PendingOAuthRequest?) -> Unit,
) : OAuthPendingRequestStore {
    @Synchronized
    override fun savePendingOAuthRequest(request: PendingOAuthRequest) {
        require(request.codeVerifier.matches(PKCE_VERIFIER_PATTERN)) {
            "PKCE verifier must be 43-128 URL-safe characters."
        }
        require(request.clientState.matches(OAUTH_CLIENT_STATE_PATTERN)) {
            "OAuth client state must be 43-128 URL-safe characters."
        }
        require(request.createdAtEpochMillis > 0L) { "OAuth request creation time is invalid." }
        write(request)
    }

    @Synchronized
    override fun consumePendingOAuthRequest(
        callbackState: String?,
        nowEpochMillis: Long,
        ttlMillis: Long,
    ): PendingOAuthRequestConsumption {
        require(ttlMillis > 0L) { "OAuth request TTL must be positive." }
        val pending = read() ?: return PendingOAuthRequestConsumption.NoPendingRequest

        if (callbackState.isNullOrBlank()) {
            return PendingOAuthRequestConsumption.MissingState
        }

        val ageMillis = nowEpochMillis - pending.createdAtEpochMillis
        if (nowEpochMillis < pending.createdAtEpochMillis || ageMillis >= ttlMillis) {
            write(null)
            return PendingOAuthRequestConsumption.Expired
        }

        if (!constantTimeEquals(pending.clientState, callbackState)) {
            return PendingOAuthRequestConsumption.StateMismatch
        }

        write(null)
        return PendingOAuthRequestConsumption.Accepted(pending.codeVerifier)
    }
}

internal fun constantTimeEquals(expected: String, actual: String): Boolean = MessageDigest.isEqual(
    expected.toByteArray(Charsets.US_ASCII),
    actual.toByteArray(Charsets.US_ASCII),
)

class AuthTokenStore(context: Context) : AuthStore {
    private val appContext = context.applicationContext
    private val preferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            appContext,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
    private val pendingRequestCoordinator by lazy {
        OAuthPendingRequestCoordinator(
            read = {
                val verifier = preferences.getString(KEY_PENDING_CODE_VERIFIER, null)
                val state = preferences.getString(KEY_PENDING_CLIENT_STATE, null)
                val createdAt = preferences.getLong(KEY_PENDING_CREATED_AT, 0L)
                if (verifier.isNullOrBlank() || state.isNullOrBlank() || createdAt <= 0L) {
                    null
                } else {
                    PendingOAuthRequest(
                        codeVerifier = verifier,
                        clientState = state,
                        createdAtEpochMillis = createdAt,
                    )
                }
            },
            write = { request ->
                val editor = preferences.edit()
                    .remove(KEY_LEGACY_PENDING_PKCE_VERIFIER)
                if (request == null) {
                    editor
                        .remove(KEY_PENDING_CODE_VERIFIER)
                        .remove(KEY_PENDING_CLIENT_STATE)
                        .remove(KEY_PENDING_CREATED_AT)
                } else {
                    editor
                        .putString(KEY_PENDING_CODE_VERIFIER, request.codeVerifier)
                        .putString(KEY_PENDING_CLIENT_STATE, request.clientState)
                        .putLong(KEY_PENDING_CREATED_AT, request.createdAtEpochMillis)
                }
                check(editor.commit()) { "OAuth 로그인 요청을 안전하게 저장하지 못했습니다." }
            },
        )
    }

    override fun save(accessToken: String, refreshToken: String?) {
        preferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    override fun accessToken(): String? = preferences.getString(KEY_ACCESS_TOKEN, null)

    override fun refreshToken(): String? = preferences.getString(KEY_REFRESH_TOKEN, null)

    override fun savePendingOAuthRequest(request: PendingOAuthRequest) =
        pendingRequestCoordinator.savePendingOAuthRequest(request)

    override fun consumePendingOAuthRequest(
        callbackState: String?,
        nowEpochMillis: Long,
        ttlMillis: Long,
    ): PendingOAuthRequestConsumption = pendingRequestCoordinator.consumePendingOAuthRequest(
        callbackState = callbackState,
        nowEpochMillis = nowEpochMillis,
        ttlMillis = ttlMillis,
    )

    override fun clear() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val ENCRYPTED_PREFS_NAME = "nugul_auth_encrypted"
        private const val KEY_ACCESS_TOKEN = "accessToken"
        private const val KEY_REFRESH_TOKEN = "refreshToken"
        private const val KEY_PENDING_CODE_VERIFIER = "pendingOAuthCodeVerifier"
        private const val KEY_PENDING_CLIENT_STATE = "pendingOAuthClientState"
        private const val KEY_PENDING_CREATED_AT = "pendingOAuthCreatedAt"
        private const val KEY_LEGACY_PENDING_PKCE_VERIFIER = "pendingPkceVerifier"
    }
}

private val PKCE_VERIFIER_PATTERN = Regex("^[A-Za-z0-9._~-]{43,128}$")
private val OAUTH_CLIENT_STATE_PATTERN = Regex("^[A-Za-z0-9_-]{43,128}$")
