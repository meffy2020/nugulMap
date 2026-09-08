package com.nugulmap.nativeapp.core.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class PkcePair(
    val verifier: String,
    val challenge: String,
) {
    companion object {
        fun create(): PkcePair {
            val verifier = secureRandomBase64Url()
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            return PkcePair(verifier = verifier, challenge = digest.base64UrlNoPadding())
        }
    }
}

object OAuthClientState {
    fun create(): String = secureRandomBase64Url()
}

private val secureRandom = SecureRandom()

private fun secureRandomBase64Url(byteCount: Int = 32): String =
    ByteArray(byteCount)
        .also(secureRandom::nextBytes)
        .base64UrlNoPadding()

private fun ByteArray.base64UrlNoPadding(): String = Base64.getUrlEncoder()
    .withoutPadding()
    .encodeToString(this)
