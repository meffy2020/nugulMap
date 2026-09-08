package com.nugulmap.nativeapp.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalUriLauncherTest {
    @Test
    fun `directions fall back to an https map when the geo handler is unavailable`() {
        val request = directionsUriRequest(
            latitude = 37.5112,
            longitude = 127.0981,
            title = "롯데월드 흡연구역",
        )
        val opened = mutableListOf<String>()

        val result = openExternalUriSafely(request) { uri ->
            opened += uri
            if (uri.startsWith("geo:")) {
                throw IllegalArgumentException("No activity found")
            }
        }

        assertEquals(ExternalUriOpenResult.FallbackOpened, result)
        assertEquals(2, opened.size)
        assertTrue(opened.first().startsWith("geo:37.5112,127.0981"))
        assertTrue(opened.last().startsWith("https://www.google.com/maps/search/"))
    }

    @Test
    fun `external link failure is returned instead of escaping as an exception`() {
        val result = openExternalUriSafely(
            ExternalUriRequest(primaryUri = "https://nugulmap.com/privacy"),
        ) {
            throw SecurityException("blocked")
        }

        assertEquals(ExternalUriOpenResult.Failed, result)
    }

    @Test
    fun `successful primary link does not try its fallback`() {
        val opened = mutableListOf<String>()
        val result = openExternalUriSafely(
            ExternalUriRequest(
                primaryUri = "geo:37.5,127.0",
                fallbackUri = "https://example.com/fallback",
            ),
        ) { opened += it }

        assertEquals(ExternalUriOpenResult.PrimaryOpened, result)
        assertEquals(listOf("geo:37.5,127.0"), opened)
    }
}
