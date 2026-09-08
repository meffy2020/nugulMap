package com.nugulmap.nativeapp.ui.map

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CurrentLocationMapContractTest {
    @Test
    fun `camera focus runs once for each current location request`() {
        val focus = MapCurrentLocationFocus(
            requestId = 7L,
            latitude = 37.5665,
            longitude = 126.9780,
        )

        assertTrue(shouldMoveCameraToCurrentLocation(lastRequestId = null, focus = focus))
        assertFalse(shouldMoveCameraToCurrentLocation(lastRequestId = 7L, focus = focus))
        assertTrue(
            shouldMoveCameraToCurrentLocation(
                lastRequestId = 7L,
                focus = focus.copy(requestId = 8L),
            ),
        )
        assertFalse(shouldMoveCameraToCurrentLocation(lastRequestId = null, focus = null))
    }

    @Test
    fun `bottom map chrome exposes current location recenter action`() {
        val source = File("src/main/java/com/nugulmap/nativeapp/ui/map/MapScreen.kt").readText()

        assertTrue(source.contains("CURRENT_LOCATION_ACCESSIBILITY_LABEL = \"현재 위치로 지도 이동\""))
        assertTrue(source.contains("onCurrentLocation = moveMapToCurrentLocation"))
        assertTrue(source.contains("currentLocationFocus = currentLocationFocus"))
    }
}
