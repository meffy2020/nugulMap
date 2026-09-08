package com.nugulmap.nativeapp.ui.map

import java.net.URLEncoder

internal data class ExternalUriRequest(
    val primaryUri: String,
    val fallbackUri: String? = null,
)

internal enum class ExternalUriOpenResult {
    PrimaryOpened,
    FallbackOpened,
    Failed,
}

internal fun directionsUriRequest(
    latitude: Double,
    longitude: Double,
    title: String,
): ExternalUriRequest {
    val coordinates = "$latitude,$longitude"
    val encodedQuery = encodeUriQuery("$coordinates $title")
    return ExternalUriRequest(
        primaryUri = "geo:$coordinates?q=$encodedQuery",
        fallbackUri = "https://www.google.com/maps/search/?api=1&query=$encodedQuery",
    )
}

internal fun openExternalUriSafely(
    request: ExternalUriRequest,
    openUri: (String) -> Unit,
): ExternalUriOpenResult {
    if (tryOpenUri(request.primaryUri, openUri)) {
        return ExternalUriOpenResult.PrimaryOpened
    }
    if (request.fallbackUri?.let { tryOpenUri(it, openUri) } == true) {
        return ExternalUriOpenResult.FallbackOpened
    }
    return ExternalUriOpenResult.Failed
}

private fun tryOpenUri(uri: String, openUri: (String) -> Unit): Boolean {
    if (uri.isBlank()) return false
    return try {
        openUri(uri)
        true
    } catch (_: RuntimeException) {
        false
    }
}

private fun encodeUriQuery(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
