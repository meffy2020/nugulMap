package com.nugulmap.nativeapp.ui.map

import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HotplaceMarkerResourceTest {
    @Test
    fun `map categories use distinct flat marker colors`() {
        val colors = listOf(
            ZONE_MARKER_COLOR,
            SELECTED_MARKER_COLOR,
            HOTPLACE_MARKER_COLOR,
            EVENT_MARKER_COLOR,
            RECOMMENDATION_MARKER_COLOR,
        )
        assertTrue(colors.all { it ushr 24 == 0xFFL })
        assertTrue(colors.distinct().size == colors.size)
    }

    @Test
    fun `recommendation marker is not reused as the hotplace marker`() {
        assertNotEquals(HOTPLACE_MARKER_COLOR, RECOMMENDATION_MARKER_COLOR)
    }
}
