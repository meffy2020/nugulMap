package com.nugulmap.nativeapp.core.deeplink

import android.net.Uri

object OAuthDeepLink {
    const val SCHEME = "nugulmap"
    const val HOST = "oauth"
    const val PATH = "/callback"
    const val CALLBACK_URL = "$SCHEME://$HOST$PATH"

    fun isCallback(uri: Uri): Boolean = uri.scheme == SCHEME && uri.host == HOST && uri.path == PATH
}
