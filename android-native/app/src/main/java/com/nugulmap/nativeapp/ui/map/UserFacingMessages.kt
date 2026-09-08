package com.nugulmap.nativeapp.ui.map

internal fun Throwable.userFacingMessage(fallback: String): String = fallback

internal fun zoneReportFailureMessage(httpStatus: Int?): String = when (httpStatus) {
    401, 403 -> "장소 신고는 로그인이 필요합니다."
    409 -> "이미 신고한 장소입니다."
    else -> "장소 신고에 실패했습니다. 잠시 후 다시 시도해 주세요."
}

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
        "api",
        "provider",
        "configured",
        " key",
        "http ",
        "ssl",
        "socket",
        "exception",
        "kakao",
        "키가",
        "연결",
    ).any { lowered.contains(it) }

    return if (isTechnical) fallback else message
}

internal fun firstStatusMessage(vararg messages: String?): String? =
    messages.firstOrNull { !it.isNullOrBlank() }
