package com.nugulmap.nativeapp.ui.map

import com.nugulmap.nativeapp.data.dto.MAX_PERO_RECOMMENDATIONS
import com.nugulmap.nativeapp.data.dto.MapBounds
import com.nugulmap.nativeapp.data.dto.RecommendationPlaceDto
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InsightMapPickerTest {
    private val bounds = MapBounds(
        minLat = 37.50,
        maxLat = 37.60,
        minLng = 126.90,
        maxLng = 127.10,
    )

    @Test
    fun `picker uses only current visible map coordinates`() {
        val candidates = visibleInsightPickerCandidates(
            mode = Season2LayerMode.Pero,
            bounds = bounds,
            recommendations = listOf(
                recommendation("inside", 37.55, 127.0),
                recommendation("outside", 37.70, 127.0),
            ),
        )

        assertEquals(listOf("recommendation:kakao_local:inside"), candidates.map(InsightPickerCandidate::key))
    }

    @Test
    fun `recommendation CTA and moving overlay use drawn system-style icons only`() {
        val source = File("src/main/java/com/nugulmap/nativeapp/ui/map/MapScreen.kt").readText()

        assertTrue(source.contains("private fun RecommendationSparkIcon"))
        assertTrue(source.contains("private fun RecommendationToken"))
        assertFalse(source.contains("R.drawable.pero_dice_marker"))
        listOf("🎲", "⚀", "⚁", "⚂", "⚃", "⚄", "⚅").forEach { emoji ->
            assertFalse(source.contains(emoji), "Emoji fallback must not remain: $emoji")
        }
    }

    @Test
    fun `picker is exclusive to Pero and has no insight fallback`() {
        val recommendations = listOf(recommendation("place", 37.55, 127.0))

        listOf(
            Season2LayerMode.Zones,
            Season2LayerMode.Hotplaces,
            Season2LayerMode.Events,
        ).forEach { mode ->
            assertTrue(visibleInsightPickerCandidates(mode, bounds, recommendations).isEmpty())
            assertFalse(mode.showsPeroPicker)
        }
        assertEquals(
            listOf("recommendation:kakao_local:place"),
            visibleInsightPickerCandidates(Season2LayerMode.Pero, bounds, recommendations)
                .map(InsightPickerCandidate::key),
        )
        assertTrue(visibleInsightPickerCandidates(Season2LayerMode.Pero, bounds, emptyList()).isEmpty())
        assertTrue(Season2LayerMode.Pero.showsPeroPicker)
    }

    @Test
    fun `picker cannot choose insight rows beyond the rendered marker limit`() {
        val recommendations = (1..(MAX_PERO_RECOMMENDATIONS + 1)).map { index ->
            recommendation("place-$index", 37.55, 127.0)
        }

        val candidates = visibleInsightPickerCandidates(
            Season2LayerMode.Pero,
            bounds,
            recommendations,
        )

        assertEquals(MAX_PERO_RECOMMENDATIONS, candidates.size)
        assertFalse(candidates.any { it.id == "kakao_local:place-${MAX_PERO_RECOMMENDATIONS + 1}" })
    }

    @Test
    fun `picker avoids all recent choices while another candidate exists`() {
        val candidates = listOf(candidate("a"), candidate("b"), candidate("c"), candidate("d"))

        val result = pickInsightCandidate(
            candidates = candidates,
            recentKeys = listOf("recommendation:kakao_local:a", "recommendation:kakao_local:b", "recommendation:kakao_local:c"),
            randomIndex = { 0 },
        )

        assertEquals("recommendation:kakao_local:d", result?.candidate?.key)
        assertEquals(
            listOf(
                "recommendation:kakao_local:d",
                "recommendation:kakao_local:a",
                "recommendation:kakao_local:b",
            ),
            result?.recentKeys,
        )
    }

    @Test
    fun `picker still avoids the immediate repeat after all candidates become recent`() {
        val candidates = listOf(candidate("a"), candidate("b"))

        val result = pickInsightCandidate(
            candidates = candidates,
            recentKeys = listOf("recommendation:kakao_local:a", "recommendation:kakao_local:b"),
            randomIndex = { 0 },
        )

        assertEquals("recommendation:kakao_local:b", result?.candidate?.key)
    }

    @Test
    fun `single candidate is selectable and empty candidates return null`() {
        val only = candidate("only")

        assertEquals(
            only,
            pickInsightCandidate(listOf(only), listOf(only.key), randomIndex = { 0 })?.candidate,
        )
        assertNull(pickInsightCandidate(emptyList(), emptyList()))
    }

    @Test
    fun `picker presentation covers loading empty rolling and accessibility states`() {
        val loading = insightPickerPresentation(Season2LayerMode.Pero, 0, isLoading = true, isRolling = false)
        val empty = insightPickerPresentation(Season2LayerMode.Pero, 0, isLoading = false, isRolling = false)
        val ready = insightPickerPresentation(Season2LayerMode.Pero, 4, isLoading = false, isRolling = false)
        val rolling = insightPickerPresentation(Season2LayerMode.Pero, 4, isLoading = false, isRolling = true)
        val regularLayer = insightPickerPresentation(Season2LayerMode.Hotplaces, 4, isLoading = false, isRolling = false)

        assertFalse(loading.enabled)
        assertTrue(loading.statusMessage.contains("불러오는 중"))
        assertFalse(empty.enabled)
        assertTrue(empty.contentDescription.contains("후보 없음"))
        assertTrue(ready.enabled)
        assertTrue(ready.contentDescription.contains("4곳"))
        assertFalse(rolling.enabled)
        assertEquals("추천 중", rolling.buttonLabel)
        assertEquals("한 곳 추천", ready.buttonLabel)
        assertFalse(regularLayer.enabled)
    }

    @Test
    fun `provider status is explicit without replacing loading state`() {
        assertNull(recommendationDataStatusMessage("READY", isLoading = false))
        assertNull(recommendationDataStatusMessage("PROVIDER_UNAVAILABLE", isLoading = true))
        assertEquals(
            "추천 데이터 연결이 필요합니다.",
            recommendationDataStatusMessage("PROVIDER_NOT_CONFIGURED", isLoading = false),
        )
        assertEquals(
            "추천 데이터를 불러올 수 없습니다.",
            recommendationDataStatusMessage("PROVIDER_UNAVAILABLE", isLoading = false),
        )
    }

    @Test
    fun `pero target point maps coordinates and clamps to safe map overlay bounds`() {
        val centered = peroDiceTargetPoint(bounds, candidate("center"))
        val outside = peroDiceTargetPoint(
            bounds,
            candidate("outside").copy(latitude = 38.0, longitude = 128.0),
        )

        assertEquals(0.5, centered.x, absoluteTolerance = 0.0001)
        assertEquals(0.5, centered.y, absoluteTolerance = 0.0001)
        assertEquals(0.88, outside.x)
        assertEquals(0.14, outside.y)
    }

    @Test
    fun `pero bounce path preserves wall run and exact four step landing`() {
        val target = NormalizedMapPoint(0.64, 0.40)
        val path = peroDiceBouncePath(
            target = target,
            count = PERO_DICE_INTERVALS_MILLIS.size,
            wallPoints = PERO_DICE_WALL_POINTS,
        )

        assertEquals(PERO_DICE_INTERVALS_MILLIS.size, path.size)
        assertEquals(PERO_DICE_WALL_POINTS, path.take(PERO_DICE_WALL_POINTS.size))
        assertPointEquals(NormalizedMapPoint(0.41, 0.60), path[path.lastIndex - 3])
        assertPointEquals(NormalizedMapPoint(0.54, 0.28), path[path.lastIndex - 2])
        assertPointEquals(NormalizedMapPoint(0.68, 0.45), path[path.lastIndex - 1])
        assertEquals(target, path.last())
    }

    @Test
    fun `disabled animations use fast path and reduced scale shortens pero pacing`() {
        assertTrue(scaledPeroDiceIntervals(0f).isEmpty())
        assertEquals(
            PERO_DICE_INTERVALS_MILLIS.map(Int::toLong),
            scaledPeroDiceIntervals(1f),
        )
        assertEquals(28L, scaledPeroDiceIntervals(0.5f).first())
    }

    @Test
    fun `selected marker highlights and camera focus runs once per selection key`() {
        val focus = MapInsightFocus("recommendation:kakao_local:a", 37.55, 127.0)

        assertTrue(isSelectedInsightMarker("recommendation:kakao_local:a", focus.key))
        assertFalse(isSelectedInsightMarker("recommendation:kakao_local:b", focus.key))
        assertTrue(shouldMoveCameraToInsight(lastFocusedInsightKey = null, focus = focus))
        assertFalse(shouldMoveCameraToInsight(lastFocusedInsightKey = "recommendation:kakao_local:a", focus = focus))
        assertFalse(shouldMoveCameraToInsight(lastFocusedInsightKey = null, focus = null))
    }

    private fun candidate(id: String) = InsightPickerCandidate(
        key = "recommendation:kakao_local:$id",
        id = "kakao_local:$id",
        type = InsightPickerType.Recommendation,
        title = id,
        latitude = 37.55,
        longitude = 127.0,
    )

    private fun recommendation(id: String, latitude: Double, longitude: Double) = RecommendationPlaceDto(
        id = id,
        provider = "KAKAO_LOCAL",
        name = id,
        latitude = latitude,
        longitude = longitude,
    )

    private fun assertPointEquals(expected: NormalizedMapPoint, actual: NormalizedMapPoint) {
        assertEquals(expected.x, actual.x, absoluteTolerance = 0.0001)
        assertEquals(expected.y, actual.y, absoluteTolerance = 0.0001)
    }
}
