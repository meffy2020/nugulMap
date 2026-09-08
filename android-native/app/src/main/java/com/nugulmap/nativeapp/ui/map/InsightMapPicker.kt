package com.nugulmap.nativeapp.ui.map

import com.nugulmap.nativeapp.data.dto.MAX_PERO_RECOMMENDATIONS
import com.nugulmap.nativeapp.data.dto.MapBounds
import com.nugulmap.nativeapp.data.dto.RecommendationPlaceDto
import kotlin.random.Random

internal enum class InsightPickerType {
    Recommendation,
}

internal data class InsightPickerCandidate(
    val key: String,
    val id: String,
    val type: InsightPickerType,
    val title: String,
    val latitude: Double,
    val longitude: Double,
)

internal data class InsightPickerResult(
    val candidate: InsightPickerCandidate,
    val recentKeys: List<String>,
)

internal data class InsightPickerPresentation(
    val buttonLabel: String,
    val statusMessage: String,
    val contentDescription: String,
    val enabled: Boolean,
)

internal data class NormalizedMapPoint(
    val x: Double,
    val y: Double,
)

internal fun visibleInsightPickerCandidates(
    mode: Season2LayerMode,
    bounds: MapBounds,
    recommendations: List<RecommendationPlaceDto>,
): List<InsightPickerCandidate> = when (mode) {
    Season2LayerMode.Zones,
    Season2LayerMode.Hotplaces,
    Season2LayerMode.Events,
    -> emptyList()
    Season2LayerMode.Pero -> recommendations
        .take(MAX_PERO_RECOMMENDATIONS)
        .filter { bounds.contains(it.latitude, it.longitude) }
        .map { place ->
            InsightPickerCandidate(
                key = place.mapKey,
                id = place.stableKey,
                type = InsightPickerType.Recommendation,
                title = place.name,
                latitude = place.latitude,
                longitude = place.longitude,
            )
        }
}.distinctBy(InsightPickerCandidate::key)

internal fun pickInsightCandidate(
    candidates: List<InsightPickerCandidate>,
    recentKeys: List<String>,
    randomIndex: (Int) -> Int = { size -> Random.nextInt(size) },
): InsightPickerResult? {
    val uniqueCandidates = candidates.distinctBy(InsightPickerCandidate::key)
    if (uniqueCandidates.isEmpty()) {
        return null
    }

    val recentSet = recentKeys.take(RECENT_PICK_LIMIT).toSet()
    val withoutRecent = uniqueCandidates.filterNot { it.key in recentSet }
    val withoutImmediateRepeat = uniqueCandidates.filterNot { it.key == recentKeys.firstOrNull() }
    val pool = when {
        withoutRecent.isNotEmpty() -> withoutRecent
        withoutImmediateRepeat.isNotEmpty() -> withoutImmediateRepeat
        else -> uniqueCandidates
    }
    val selected = pool[randomIndex(pool.size).coerceIn(pool.indices)]
    return InsightPickerResult(
        candidate = selected,
        recentKeys = (listOf(selected.key) + recentKeys.filterNot { it == selected.key })
            .take(RECENT_PICK_LIMIT),
    )
}

internal fun insightPickerPresentation(
    mode: Season2LayerMode,
    candidateCount: Int,
    isLoading: Boolean,
    isRolling: Boolean,
): InsightPickerPresentation {
    val category = when (mode) {
        Season2LayerMode.Pero -> "추천 장소"
        Season2LayerMode.Zones,
        Season2LayerMode.Hotplaces,
        Season2LayerMode.Events,
        -> "장소"
    }
    val status = when {
        isRolling -> "현재 지도에서 추천 장소를 고르는 중입니다."
        isLoading -> "$category 후보를 불러오는 중입니다."
        candidateCount == 0 -> "현재 지도 화면에 고를 수 있는 $category 후보가 없습니다."
        else -> "현재 지도 화면의 $category ${candidateCount}곳 중 한 곳을 골라드려요."
    }
    val buttonLabel = if (isRolling) "추천 중" else "한 곳 추천"
    return InsightPickerPresentation(
        buttonLabel = buttonLabel,
        statusMessage = status,
        contentDescription = when {
            isRolling -> "$category 선택 진행 중"
            isLoading -> "$category 후보를 불러오는 중"
            candidateCount == 0 -> "현재 지도 화면에 $category 후보 없음"
            else -> "현재 지도 화면의 $category ${candidateCount}곳 중 한 곳 추천"
        },
        enabled = mode.showsPeroPicker && !isLoading && !isRolling && candidateCount > 0,
    )
}

internal fun peroDiceTargetPoint(
    bounds: MapBounds,
    candidate: InsightPickerCandidate,
): NormalizedMapPoint {
    val longitudeRange = (bounds.maxLng - bounds.minLng).coerceAtLeast(MINIMUM_COORDINATE_RANGE)
    val latitudeRange = (bounds.maxLat - bounds.minLat).coerceAtLeast(MINIMUM_COORDINATE_RANGE)
    val x = (candidate.longitude - bounds.minLng) / longitudeRange
    val y = 1 - ((candidate.latitude - bounds.minLat) / latitudeRange)
    return NormalizedMapPoint(
        x = x.coerceIn(0.12, 0.88),
        y = y.coerceIn(0.14, 0.78),
    )
}

internal fun peroDiceBouncePath(
    target: NormalizedMapPoint,
    count: Int,
    wallPoints: List<NormalizedMapPoint> = PERO_DICE_WALL_POINTS.shuffled(),
): List<NormalizedMapPoint> {
    if (count <= 0) return emptyList()
    val path = wallPoints.take((count - 4).coerceAtLeast(0)).toMutableList()
    path += NormalizedMapPoint(
        x = (target.x + 0.18) / 2,
        y = (target.y + 0.20).coerceIn(0.16, 0.80),
    )
    path += NormalizedMapPoint(
        x = (target.x - 0.10).coerceIn(0.12, 0.88),
        y = (target.y - 0.12).coerceIn(0.14, 0.78),
    )
    path += NormalizedMapPoint(
        x = (target.x + 0.04).coerceIn(0.12, 0.88),
        y = (target.y + 0.05).coerceIn(0.14, 0.78),
    )
    path += target
    return path.take(count)
}

internal fun scaledPeroDiceIntervals(animationDurationScale: Float): List<Long> {
    if (animationDurationScale <= 0f) return emptyList()
    val scale = animationDurationScale.coerceIn(0.05f, 1.5f)
    return PERO_DICE_INTERVALS_MILLIS.map { interval -> (interval * scale).toLong().coerceAtLeast(1L) }
}

private fun MapBounds.contains(latitude: Double, longitude: Double): Boolean =
    latitude in minLat..maxLat && longitude in minLng..maxLng

internal const val RECENT_PICK_LIMIT = 3
internal val PERO_DICE_INTERVALS_MILLIS = listOf(56, 48, 44, 50, 58, 66, 74, 84, 98, 116, 140, 176, 230)
internal val PERO_DICE_WALL_POINTS = listOf(
    NormalizedMapPoint(0.18, 0.16),
    NormalizedMapPoint(0.84, 0.22),
    NormalizedMapPoint(0.76, 0.72),
    NormalizedMapPoint(0.12, 0.64),
    NormalizedMapPoint(0.28, 0.28),
    NormalizedMapPoint(0.88, 0.52),
    NormalizedMapPoint(0.44, 0.76),
    NormalizedMapPoint(0.16, 0.34),
    NormalizedMapPoint(0.72, 0.18),
)
private const val MINIMUM_COORDINATE_RANGE = 0.000_001
