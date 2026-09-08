package com.nugulmap.nativeapp.ui.map

import com.nugulmap.nativeapp.data.dto.EventInsightPayload
import com.nugulmap.nativeapp.data.dto.HotplaceDto
import com.nugulmap.nativeapp.data.dto.HotplaceInsightPayload
import com.nugulmap.nativeapp.data.dto.MapInsightPayload
import com.nugulmap.nativeapp.data.dto.TrendEventDto
import com.nugulmap.nativeapp.data.dto.ZoneDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapRefreshIsolationTest {
    @Test
    fun `insight failure still publishes fresh smoking zones and keeps prior insight lanes`() {
        val previousHotplaces = HotplaceInsightPayload(places = listOf(hotplace("kept-hot")))
        val previousEvents = EventInsightPayload(events = listOf(event("kept-event")))
        val freshZones = listOf(zone(9))
        val current = MapUiState(
            zones = listOf(zone(1)),
            hotplaceInsight = previousHotplaces,
            eventInsight = previousEvents,
            isLoading = true,
            isInsightLoading = true,
        )

        val updated = current.withMapRefreshResults(
            zonesResult = Result.success(freshZones),
            insightResult = Result.failure(IllegalStateException("insight unavailable")),
        )

        assertEquals(freshZones, updated.zones)
        assertEquals(previousHotplaces, updated.hotplaceInsight)
        assertEquals(previousEvents, updated.eventInsight)
        assertNull(updated.errorMessage)
        assertEquals("핫플·팝업을 새로고침하지 못했습니다. 이전 결과를 유지합니다.", updated.actionMessage)
    }

    @Test
    fun `zone failure keeps prior zones while publishing fresh insight lanes`() {
        val previousZones = listOf(zone(1))
        val freshHotplaces = HotplaceInsightPayload(places = listOf(hotplace("fresh-hot")))
        val freshEvents = EventInsightPayload(events = listOf(event("fresh-event")))
        val current = MapUiState(
            zones = previousZones,
            hotplaceInsight = HotplaceInsightPayload(places = listOf(hotplace("old-hot"))),
            eventInsight = EventInsightPayload(events = listOf(event("old-event"))),
            isLoading = true,
            isInsightLoading = true,
        )

        val updated = current.withMapRefreshResults(
            zonesResult = Result.failure(IllegalStateException("zone unavailable")),
            insightResult = Result.success(
                MapInsightPayload(
                    hotplaces = freshHotplaces,
                    events = freshEvents,
                ),
            ),
        )

        assertEquals(previousZones, updated.zones)
        assertEquals(freshHotplaces, updated.hotplaceInsight)
        assertEquals(freshEvents, updated.eventInsight)
        assertEquals("흡연구역을 새로고침하지 못했습니다.", updated.errorMessage)
        assertNull(updated.actionMessage)
    }

    private fun zone(id: Int) = ZoneDto(
        id = id,
        address = "zone-$id",
        latitude = 37.5,
        longitude = 127.0,
    )

    private fun hotplace(id: String) = HotplaceDto(
        id = id,
        name = id,
        latitude = 37.5,
        longitude = 127.0,
    )

    private fun event(id: String) = TrendEventDto(
        id = id,
        title = id,
        latitude = 37.5446,
        longitude = 127.0557,
    )
}
