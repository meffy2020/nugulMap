package com.nugulmap.nativeapp.ui.map

internal fun Throwable.userFacingMessage(fallback: String): String = fallback

internal fun String?.toUserFacingStatus(fallback: String): String {
    val message = this?.trim().orEmpty()
    if (message.isBlank()) return fallback

    val lowered = message.lowercase()
    val isTechnical = listOf(
        "unknownhost",
        "unknown host",
        "name or service not known",
        "unable to resolve",
        "connection",
        "dns",
        "failed to connect",
        "timed out",
        "java.net",
        "retrofit",
        "okhttp",
        "http ",
        "ssl",
        "socket",
        "exception",
        "kakao",
    ).any { lowered.contains(it) }

    return if (isTechnical) fallback else message
}

internal fun firstStatusMessage(vararg messages: String?): String? =
    messages.firstOrNull { !it.isNullOrBlank() }
