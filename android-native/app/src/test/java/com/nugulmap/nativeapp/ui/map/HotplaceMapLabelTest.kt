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

    @Test
    fun `hotplace freshness uses current only for current measurements`() {
        assertEquals("방금 업데이트 · 1분 경과", formatHotplaceFreshness("CURRENT", 90))
        assertEquals("측정 지연 · 10분 경과", formatHotplaceFreshness("DELAYED", 600))
        assertEquals("참고 후보", formatHotplaceFreshness("STATIC", null))
        assertEquals("참고 후보", formatHotplaceFreshness("UNKNOWN", null))
    }

    @Test
    fun `comparison label never calls delayed or static data current`() {
        val delayed = HotplaceDto(
            id = "delayed",
            name = "지연 장소",
            crowdLevel = "붐빔",
            latitude = 37.5,
            longitude = 127.0,
            freshnessStatus = "DELAYED",
            ageSeconds = 720,
        )
        val static = delayed.copy(id = "static", freshnessStatus = "STATIC", ageSeconds = null)

        assertEquals("붐빔 · 측정 지연 · 12분 경과", formatHotplaceComparisonLabel(delayed))
        assertEquals("붐빔 · 참고 후보", formatHotplaceComparisonLabel(static))
    }

}
