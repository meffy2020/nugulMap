package com.nugulmap.nativeapp.ui.map

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapMarkerResourceContractTest {
    private val resourceRoot = File("src/main/res")
    private val densityDirectories = listOf(
        "drawable-mdpi",
        "drawable-hdpi",
        "drawable-xhdpi",
        "drawable-xxhdpi",
        "drawable-xxxhdpi",
    )
    private val kakaoLabelMarkers = listOf(
        "ic_zone_marker",
        "ic_zone_marker_selected",
        "ic_hotplace_marker",
        "ic_event_marker",
    )

    @Test
    fun `Kakao label markers are raster resources at every Android density`() {
        kakaoLabelMarkers.forEach { markerName ->
            assertFalse(
                File(resourceRoot, "drawable/$markerName.xml").exists(),
                "$markerName must not be a VectorDrawable because Kakao label images reject it",
            )
            densityDirectories.forEach { densityDirectory ->
                val marker = File(resourceRoot, "$densityDirectory/$markerName.png")
                assertTrue(
                    marker.isFile && marker.length() > 0,
                    "Missing raster marker: ${marker.path}",
                )
            }
        }
    }

    @Test
    fun `Kakao receives bitmap marker objects instead of raw Android resource ids`() {
        val source = File(
            "src/main/java/com/nugulmap/nativeapp/ui/map/KakaoZoneMap.kt",
        ).readText()

        assertTrue(source.contains("Bitmap.createBitmap"))
        assertTrue(source.contains("createMapMarkerBitmaps"))
        assertTrue(source.contains("LabelStyle.from(markerBitmaps."))
        assertTrue(source.contains(".setApplyDpScale(false)"))
        assertFalse(Regex("""LabelStyle\s*\.from\(R\.drawable\.""").containsMatchIn(source))
        assertFalse(Regex("""LabelStyle\s*\.from\(hotplaceMarkerResourceId\)""").containsMatchIn(source))
    }
}
