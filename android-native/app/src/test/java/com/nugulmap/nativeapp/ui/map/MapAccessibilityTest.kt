package com.nugulmap.nativeapp.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.nugulmap.nativeapp.data.dto.ZoneDto

class MapAccessibilityTest {
    @Test
    fun `compact controls keep 48dp interaction targets`() {
        assertEquals(48, MINIMUM_TOUCH_TARGET_DP)
        assertEquals(MINIMUM_TOUCH_TARGET_DP, SEARCH_FIELD_VISUAL_HEIGHT_DP)
        assertEquals(MINIMUM_TOUCH_TARGET_DP, FILTER_CHIP_VISUAL_HEIGHT_DP)
    }

    @Test
    fun `map description exposes visible layer counts and detail action`() {
        val description = mapAccessibilityDescription(
            zoneCount = 3,
            hotplaceCount = 2,
            eventCount = 1,
            recommendationCount = 4,
        )

        assertTrue(description.contains("흡연구역 3개"))
        assertTrue(description.contains("실시간 핫플 2개"))
        assertTrue(description.contains("팝업 1개"))
        assertTrue(description.contains("추천 장소 4개"))
        assertTrue(description.contains("상세 패널"))
    }

    @Test
    fun `empty map description does not announce zero count noise`() {
        val description = mapAccessibilityDescription(0, 0, 0)

        assertTrue(description.contains("표시할 장소 없음"))
        assertFalse(description.contains("0개"))
    }

    @Test
    fun `all visible smoking zones remain actionable beyond eight markers`() {
        val zones = (1..12).map { id ->
            ZoneDto(
                id = id,
                address = "흡연구역 $id",
                latitude = 37.5,
                longitude = 127.0,
            )
        }
        var selectedZoneId: Int? = null

        val actions = buildMapAccessibilityActions(
            zones = zones,
            hotplaces = emptyList(),
            events = emptyList(),
            recommendations = emptyList(),
            onZoneSelected = { selectedZoneId = it },
            onHotplaceSelected = {},
            onEventSelected = {},
            onRecommendationSelected = {},
        )

        assertEquals(12, actions.size)
        assertTrue(actions.last().action())
        assertEquals(12, selectedZoneId)
    }
}
