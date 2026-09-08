package com.nugulmap.nativeapp.ui.map

import com.nugulmap.nativeapp.data.api.NugulApiService
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

class LocationLetterContractTest {
    @Test
    fun `nearby read and write endpoints match the backend contract`() {
        val read = NugulApiService::class.java.methods.single { it.name == "getLocationLetters" }
        val write = NugulApiService::class.java.methods.single { it.name == "createLocationLetter" }
        val like = NugulApiService::class.java.methods.single { it.name == "likeLocationLetter" }
        val unlike = NugulApiService::class.java.methods.single { it.name == "unlikeLocationLetter" }

        assertEquals("api/location-letters", requireNotNull(read.getAnnotation(GET::class.java)).value)
        assertEquals("api/location-letters", requireNotNull(write.getAnnotation(POST::class.java)).value)
        assertEquals("api/location-letters/{letterId}/like", requireNotNull(like.getAnnotation(PUT::class.java)).value)
        assertEquals("api/location-letters/{letterId}/like", requireNotNull(unlike.getAnnotation(DELETE::class.java)).value)
    }

    @Test
    fun `smoking release does not expose location letters`() {
        val source = File("src/main/java/com/nugulmap/nativeapp/ui/map/MapScreen.kt").readText()

        assertFalse(source.contains("주변 편지 열기"))
        assertTrue(source.contains("LocationLetterEnvelopeIcon"))
    }

    @Test
    fun `letters stay on the map as one callout instead of a bottom sheet card stack`() {
        val source = File("src/main/java/com/nugulmap/nativeapp/ui/map/MapScreen.kt").readText()
        val overlay = source.substringAfter("private fun LocationLetterMapOverlay(")
            .substringBefore("private fun LocationLetterLoadingState()")

        assertTrue(source.contains("LocationLetterMapOverlay("))
        assertTrue(overlay.contains("LocationLetterCallout("))
        assertTrue(overlay.contains("selectedIndex"))
        assertTrue(overlay.contains("onToggleLike"))
        assertTrue(overlay.contains("LocationLetterHeartIcon"))
        assertTrue(overlay.contains("locationLetterDate"))
        assertTrue(overlay.contains("LocationLetterNavigationButton("))
        assertTrue(overlay.contains("이전 편지"))
        assertTrue(overlay.contains("다음 편지"))
        assertFalse(source.contains("HomeSheet.LocationLetters"))
        assertFalse(source.contains("private fun LocationLetterSheet("))
        assertFalse(overlay.contains("LocationLetterCard("))
        assertFalse(overlay.contains("warmBackground"))
        assertFalse(overlay.contains("지금 같은 자리를 지나간 사람들이 남긴 문장"))

        val callout = overlay.substringAfter("private fun LocationLetterCallout(")
            .substringBefore("private fun LocationLetterNavigationButton(")
        assertFalse(callout.contains("이곳의 편지"))
        assertFalse(callout.contains("현재 위치 주변"))
        assertFalse(callout.contains("한 문장 남기기"))
        assertFalse(callout.contains("로그인하고 편지 남기기"))
    }

    @Test
    fun `location provider accepts coarse permission and does not impose an accuracy gate`() {
        val source = File("src/main/java/com/nugulmap/nativeapp/ui/map/CurrentLocationProvider.kt").readText()

        assertTrue(source.contains("ACCESS_COARSE_LOCATION"))
        assertTrue(source.contains("currentLocation"))
        assertTrue(!source.contains("ACCURACY_THRESHOLD"))
    }
}
