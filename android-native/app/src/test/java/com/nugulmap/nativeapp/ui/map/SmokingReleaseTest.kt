package com.nugulmap.nativeapp.ui.map

import com.nugulmap.nativeapp.data.dto.ZoneDto
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmokingReleaseTest {
    private val zone = ZoneDto(id = 1, latitude = 37.5, longitude = 127.0)

    @Test fun `failed refresh retains known zones and selection`() {
        val current = MapUiState(zones = listOf(zone), selectedZoneId = 1, isLoading = true)
        val next = current.withSmokingZoneResult(Result.failure(IllegalStateException("offline")))
        assertEquals(current.zones, next.zones)
        assertEquals(1, next.selectedZoneId)
        assertFalse(next.isLoading)
        assertTrue(!next.errorMessage.isNullOrBlank())
    }

    @Test fun `empty result clears stale selection and explains recovery`() {
        val next = MapUiState(zones = listOf(zone), selectedZoneId = 1)
            .withSmokingZoneResult(Result.success(emptyList()))
        assertTrue(next.zones.isEmpty())
        assertNull(next.selectedZoneId)
        assertNull(next.errorMessage)
        assertTrue(next.actionMessage!!.contains("다른 지역"))
    }

    @Test fun `home and account refresh never activate excluded features`() {
        val screen = File("src/main/java/com/nugulmap/nativeapp/ui/map/MapScreen.kt").readText()
            .substringBefore("private enum class HomeSheet")
        listOf("MapCategorySelector(", "LocationLetterMapOverlay(", "loadPeroRecommendations(", "searchSeason3(", "PeroDiceDrawOverlay(").forEach {
            assertFalse(screen.contains(it), it)
        }
        assertTrue(screen.contains("directionsUriRequest(zone.latitude, zone.longitude, zone.title)"))
        val model = File("src/main/java/com/nugulmap/nativeapp/ui/map/MapViewModel.kt").readText()
        val refresh = model.substringAfter("fun refreshMapData(").substringBefore("fun refreshInsights(")
        assertTrue(refresh.contains("zoneRepository.loadZones(bounds)"))
        assertFalse(refresh.contains("loadMapInsights"))
        val profile = model.substringAfter("fun refreshProfileAndMyZones()").substringBefore("fun ")
        assertFalse(profile.contains("refreshRecommendationAccountData"))
    }
}
