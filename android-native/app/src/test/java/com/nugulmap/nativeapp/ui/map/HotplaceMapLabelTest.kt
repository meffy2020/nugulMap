package com.nugulmap.nativeapp.ui.map

import com.nugulmap.nativeapp.data.dto.HotplaceDto
import kotlin.test.Test
import kotlin.test.assertEquals

class HotplaceMapLabelTest {
    @Test
    fun `hotplace map label includes compact estimated people range`() {
        val label = formatHotplaceMapLabel(
            HotplaceDto(
                id = "lotte-world",
                name = "롯데월드·잠실",
                crowdLevel = "붐빔",
                estimatedMinPeople = 12_000,
                estimatedMaxPeople = 14_000,
                latitude = 37.5111,
                longitude = 127.0982,
            ),
        )

        assertEquals("롯데월드·잠실 1.2만-1.4만", label)
    }

    @Test
    fun `hotplace map label falls back to truncated name without people range`() {
        val label = formatHotplaceMapLabel(
            HotplaceDto(
                id = "seongsu",
                name = "성수동 카페거리",
                crowdLevel = "UNKNOWN",
                latitude = 37.5446,
                longitude = 127.0557,
            ),
        )

        assertEquals("성수동 카페거리", label)
    }
}
