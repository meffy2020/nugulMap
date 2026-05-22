package com.nugulmap.nativeapp.core.auth

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

class PkcePair(
    val verifier: String,
    val challenge: String,
) {
    companion object {
        fun create(): PkcePair {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            val verifier = bytes.base64UrlNoPadding()
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8))
            return PkcePair(verifier = verifier, challenge = digest.base64UrlNoPadding())
        }
    }
}

private fun ByteArray.base64UrlNoPadding(): String = Base64.encodeToString(
    this,
    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
)
