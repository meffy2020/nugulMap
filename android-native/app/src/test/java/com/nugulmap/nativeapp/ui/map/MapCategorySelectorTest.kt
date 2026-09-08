package com.nugulmap.nativeapp.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapCategorySelectorTest {
    @Test
    fun `map search and filter chips keep standard touch height`() {
        assertEquals(48, MINIMUM_TOUCH_TARGET_DP)
        assertEquals(48, SEARCH_FIELD_VISUAL_HEIGHT_DP)
    }

    @Test
    fun `map filters follow the reference order with recommendations near the front`() {
        assertEquals(
            listOf("흡연구역", "추천", "핫플", "팝업"),
            listOf(
                Season2LayerMode.Zones,
                Season2LayerMode.Pero,
                Season2LayerMode.Hotplaces,
                Season2LayerMode.Events,
            ).map(Season2LayerMode::filterLabel),
        )
    }

    @Test
    fun `status banner may wrap at the largest Android font scale`() {
        assertEquals(2, statusToastMaxLines(fontScale = 1f))
        assertEquals(4, statusToastMaxLines(fontScale = 2f))
    }

    @Test
    fun `selector exposes recommendations as a fourth independent map category`() {
        assertEquals(
            listOf("흡연구역", "실시간 핫플", "팝업", "추천"),
            Season2LayerMode.entries.map(Season2LayerMode::label),
        )
    }

    @Test
    fun `each category renders only its own marker type`() {
        assertTrue(Season2LayerMode.Zones.showsZones)
        assertFalse(Season2LayerMode.Zones.showsHotplaces)
        assertFalse(Season2LayerMode.Zones.showsEvents)
        assertFalse(Season2LayerMode.Zones.showsRecommendations)

        assertFalse(Season2LayerMode.Hotplaces.showsZones)
        assertTrue(Season2LayerMode.Hotplaces.showsHotplaces)
        assertFalse(Season2LayerMode.Hotplaces.showsEvents)
        assertFalse(Season2LayerMode.Hotplaces.showsRecommendations)

        assertFalse(Season2LayerMode.Events.showsZones)
        assertFalse(Season2LayerMode.Events.showsHotplaces)
        assertTrue(Season2LayerMode.Events.showsEvents)
        assertFalse(Season2LayerMode.Events.showsRecommendations)

        assertFalse(Season2LayerMode.Pero.showsZones)
        assertFalse(Season2LayerMode.Pero.showsHotplaces)
        assertFalse(Season2LayerMode.Pero.showsEvents)
        assertTrue(Season2LayerMode.Pero.showsRecommendations)
        assertEquals(
            listOf(Season2LayerMode.Pero),
            Season2LayerMode.entries.filter(Season2LayerMode::showsPeroPicker),
        )
    }

    @Test
    fun `search intent selects the matching marker category`() {
        assertEquals(
            Season2LayerMode.Hotplaces,
            Season3SearchMode.Hotplaces.toLayerMode(Season2LayerMode.Zones),
        )
        assertEquals(
            Season2LayerMode.Events,
            Season3SearchMode.Events.toLayerMode(Season2LayerMode.Hotplaces),
        )
        assertEquals(
            Season2LayerMode.Hotplaces,
            Season3SearchMode.All.toLayerMode(Season2LayerMode.Zones),
        )
        assertEquals(
            Season2LayerMode.Events,
            Season3SearchMode.All.toLayerMode(Season2LayerMode.Events),
        )
        assertEquals(
            Season2LayerMode.Hotplaces,
            Season3SearchMode.All.toLayerMode(Season2LayerMode.Pero),
        )
    }
}
