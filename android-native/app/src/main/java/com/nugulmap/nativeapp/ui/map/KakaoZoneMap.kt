package com.nugulmap.nativeapp.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.KakaoMapSdk
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.label.LabelTextBuilder
import com.nugulmap.nativeapp.BuildConfig
import com.nugulmap.nativeapp.data.dto.HotplaceDto
import com.nugulmap.nativeapp.data.dto.MapBounds
import com.nugulmap.nativeapp.data.dto.RecommendationPlaceDto
import com.nugulmap.nativeapp.data.dto.TrendEventDto
import com.nugulmap.nativeapp.data.dto.ZoneDto
import kotlin.math.roundToInt

data class MapInsightFocus(
    val key: String,
    val latitude: Double,
    val longitude: Double,
)

data class MapCurrentLocationFocus(
    val requestId: Long,
    val latitude: Double,
    val longitude: Double,
)

@Composable
fun KakaoZoneMap(
    zones: List<ZoneDto>,
    hotplaces: List<HotplaceDto>,
    events: List<TrendEventDto>,
    recommendations: List<RecommendationPlaceDto>,
    selectedZoneId: Int?,
    selectedInsightFocus: MapInsightFocus?,
    currentLocationFocus: MapCurrentLocationFocus?,
    onZoneSelected: (Int) -> Unit,
    onHotplaceSelected: (String) -> Unit,
    onEventSelected: (String) -> Unit,
    onRecommendationSelected: (String) -> Unit,
    onVisibleBoundsChanged: (MapBounds) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val markerAccessibilityActions = buildMapAccessibilityActions(
        zones = zones,
        hotplaces = hotplaces,
        events = events,
        recommendations = recommendations,
        onZoneSelected = onZoneSelected,
        onHotplaceSelected = onHotplaceSelected,
        onEventSelected = onEventSelected,
        onRecommendationSelected = onRecommendationSelected,
    )
    val accessibleMapModifier = modifier.semantics(mergeDescendants = true) {
        isTraversalGroup = true
        contentDescription = mapAccessibilityDescription(
            zoneCount = zones.size,
            hotplaceCount = hotplaces.size,
            eventCount = events.size,
            recommendationCount = recommendations.size,
        )
        customActions = markerAccessibilityActions
    }

    if (BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank()) {
        MapFallback(
            zoneCount = zones.size,
            modifier = accessibleMapModifier,
        )
        return
    }

    val context = LocalContext.current
    val markerBitmaps = remember(context) { createMapMarkerBitmaps(context) }
    val latestZones by rememberUpdatedState(zones)
    val latestOnZoneSelected by rememberUpdatedState(onZoneSelected)
    val latestOnHotplaceSelected by rememberUpdatedState(onHotplaceSelected)
    val latestOnEventSelected by rememberUpdatedState(onEventSelected)
    val latestOnRecommendationSelected by rememberUpdatedState(onRecommendationSelected)
    val latestOnVisibleBoundsChanged by rememberUpdatedState(onVisibleBoundsChanged)
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    var shouldShowFallback by remember { mutableStateOf(false) }
    var lastFocusedInsightLayer by remember { mutableStateOf<InsightMapLayer?>(null) }
    var lastFocusedInsightKey by remember { mutableStateOf<String?>(null) }
    var lastCurrentLocationRequestId by remember { mutableStateOf<Long?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember(context) { MapView(context) }

    LaunchedEffect(BuildConfig.KAKAO_NATIVE_APP_KEY) {
        runCatching { KakaoMapSdk.init(context.applicationContext, BuildConfig.KAKAO_NATIVE_APP_KEY.trim()) }
            .onFailure { throwable ->
                Log.w(KAKAO_MAP_TAG, "Kakao map SDK init failed (${throwable::class.java.simpleName}); showing quiet fallback")
                shouldShowFallback = true
            }
    }

    Box(
        modifier = accessibleMapModifier
            .background(Color(0xFFE7F0E8)),
    ) {
        if (shouldShowFallback) {
            MapFallback(
                zoneCount = zones.size,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            AndroidView(
                modifier = Modifier
                    .matchParentSize()
                    .clearAndSetSemantics {},
                factory = { mapView },
            )
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() = Unit

                override fun onMapError(exception: Exception) {
                    Log.w(KAKAO_MAP_TAG, "Kakao map runtime failed (${exception::class.java.simpleName}); showing quiet fallback")
                    shouldShowFallback = true
                }

                override fun onMapResumed() = Unit

                override fun onMapPaused() = Unit
            },
            object : KakaoMapReadyCallback() {
                override fun getPosition(): LatLng = firstZonePosition(latestZones)

                override fun getZoomLevel(): Int = 15

                override fun onMapReady(map: KakaoMap) {
                    kakaoMap = map
                    map.setOnLabelClickListener { _, _, label ->
                        when (val tag = label.tag) {
                            is Int -> latestOnZoneSelected(tag)
                            is String -> {
                                when {
                                    tag.startsWith("hot:") -> latestOnHotplaceSelected(tag.removePrefix("hot:"))
                                    tag.startsWith("event:") -> latestOnEventSelected(tag.removePrefix("event:"))
                                    tag.startsWith("recommendation:") -> {
                                        latestOnRecommendationSelected(tag.removePrefix("recommendation:"))
                                    }
                                }
                            }
                        }
                        true
                    }
                    map.setOnCameraMoveEndListener { movedMap, _, _ ->
                        movedMap.visibleMapBounds()?.let(latestOnVisibleBoundsChanged)
                    }
                    map.setOnViewportChangeListener { changedMap, _ ->
                        changedMap.visibleMapBounds()?.let(latestOnVisibleBoundsChanged)
                    }
                    map.visibleMapBounds()?.let(latestOnVisibleBoundsChanged)
                }
            },
        )

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.resume()
                Lifecycle.Event.ON_PAUSE -> mapView.pause()
                Lifecycle.Event.ON_DESTROY -> mapView.finish()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.resume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.finish()
        }
    }

    LaunchedEffect(
        kakaoMap,
        zones,
        hotplaces,
        events,
        recommendations,
        selectedZoneId,
        selectedInsightFocus,
        currentLocationFocus,
        markerBitmaps,
    ) {
        kakaoMap?.let { map ->
            runCatching {
                map.renderMapLabels(
                    zones = zones,
                    hotplaces = hotplaces,
                    events = events,
                    recommendations = recommendations,
                    selectedZoneId = selectedZoneId,
                    selectedInsightKey = selectedInsightFocus?.key,
                    markerBitmaps = markerBitmaps,
                )
                if (selectedInsightFocus == null) {
                    lastFocusedInsightKey = null
                }
                if (shouldMoveCameraToCurrentLocation(lastCurrentLocationRequestId, currentLocationFocus)) {
                    lastCurrentLocationRequestId = currentLocationFocus?.requestId
                    currentLocationFocus?.let { focus ->
                        map.moveCamera(
                            CameraUpdateFactory.newCenterPosition(
                                LatLng.from(focus.latitude, focus.longitude),
                                CURRENT_LOCATION_ZOOM_LEVEL,
                            ),
                        )
                    }
                } else when {
                    selectedInsightFocus != null -> {
                        lastFocusedInsightLayer = when {
                            selectedInsightFocus.key.startsWith("hot:") -> InsightMapLayer.Hotplaces
                            selectedInsightFocus.key.startsWith("event:") -> InsightMapLayer.Events
                            selectedInsightFocus.key.startsWith("recommendation:") -> InsightMapLayer.Recommendations
                            else -> lastFocusedInsightLayer
                        }
                        if (shouldMoveCameraToInsight(lastFocusedInsightKey, selectedInsightFocus)) {
                            lastFocusedInsightKey = selectedInsightFocus.key
                            map.moveCamera(
                                CameraUpdateFactory.newCenterPosition(
                                    LatLng.from(
                                        selectedInsightFocus.latitude,
                                        selectedInsightFocus.longitude,
                                    ),
                                    INSIGHT_ZOOM_LEVEL,
                                ),
                            )
                        }
                    }

                    recommendations.isNotEmpty() && zones.isEmpty() && hotplaces.isEmpty() && events.isEmpty() -> {
                        if (lastFocusedInsightLayer != InsightMapLayer.Recommendations) {
                            lastFocusedInsightLayer = InsightMapLayer.Recommendations
                            val first = recommendations.first()
                            map.moveCamera(
                                CameraUpdateFactory.newCenterPosition(
                                    LatLng.from(first.latitude, first.longitude),
                                    INSIGHT_ZOOM_LEVEL,
                                ),
                            )
                        }
                    }

                    hotplaces.isNotEmpty() && zones.isEmpty() && events.isEmpty() && recommendations.isEmpty() -> {
                        if (lastFocusedInsightLayer != InsightMapLayer.Hotplaces) {
                            lastFocusedInsightLayer = InsightMapLayer.Hotplaces
                            val first = hotplaces.first()
                            map.moveCamera(
                                CameraUpdateFactory.newCenterPosition(
                                    LatLng.from(first.latitude, first.longitude),
                                    INSIGHT_ZOOM_LEVEL,
                                ),
                            )
                        }
                    }

                    events.isNotEmpty() && zones.isEmpty() && hotplaces.isEmpty() && recommendations.isEmpty() -> {
                        if (lastFocusedInsightLayer != InsightMapLayer.Events) {
                            lastFocusedInsightLayer = InsightMapLayer.Events
                            val first = events.first()
                            map.moveCamera(
                                CameraUpdateFactory.newCenterPosition(
                                    LatLng.from(first.latitude, first.longitude),
                                    INSIGHT_ZOOM_LEVEL,
                                ),
                            )
                        }
                    }

                    zones.isNotEmpty() -> lastFocusedInsightLayer = null
                }
            }
                .onFailure { throwable ->
                    Log.w(KAKAO_MAP_TAG, "Kakao map label render failed (${throwable::class.java.simpleName})")
                }
        }
    }
}

internal fun buildMapAccessibilityActions(
    zones: List<ZoneDto>,
    hotplaces: List<HotplaceDto>,
    events: List<TrendEventDto>,
    recommendations: List<RecommendationPlaceDto>,
    onZoneSelected: (Int) -> Unit,
    onHotplaceSelected: (String) -> Unit,
    onEventSelected: (String) -> Unit,
    onRecommendationSelected: (String) -> Unit,
): List<CustomAccessibilityAction> = buildList {
        zones.forEach { zone ->
            add(
                CustomAccessibilityAction(
                    label = "흡연구역 ${zone.title}",
                    action = {
                        onZoneSelected(zone.id)
                        true
                    },
                ),
            )
        }
        hotplaces.take(MAX_RENDERED_INSIGHT_MARKERS).forEach { place ->
            add(
                CustomAccessibilityAction(
                    label = listOf("실시간 핫플", place.name, place.crowdLabel)
                        .filter(String::isNotBlank)
                        .joinToString(", "),
                    action = {
                        onHotplaceSelected(place.id)
                        true
                    },
                ),
            )
        }
        events.take(MAX_RENDERED_INSIGHT_MARKERS).forEach { event ->
            add(
                CustomAccessibilityAction(
                    label = "팝업 ${event.title}",
                    action = {
                        onEventSelected(event.id)
                        true
                    },
                ),
            )
        }
        recommendations.take(MAX_RENDERED_INSIGHT_MARKERS).forEach { place ->
            add(
                CustomAccessibilityAction(
                    label = listOf("추천 장소", place.name, place.category)
                        .filter(String::isNotBlank)
                        .joinToString(", "),
                    action = {
                        onRecommendationSelected(place.stableKey)
                        true
                    },
                ),
            )
        }
}

@Composable
private fun MapFallback(
    zoneCount: Int,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    Box(
        modifier = modifier
            .background(Color(0xFFEAF2EC)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridColor = Color.White.copy(alpha = 0.34f)
            val roadColor = Color(0xFFD6DED7).copy(alpha = 0.62f)
            repeat(7) { index ->
                val y = size.height * (index + 1) / 8f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
            }
            repeat(5) { index ->
                val x = size.width * (index + 1) / 6f
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
            }
            drawLine(roadColor, Offset(size.width * 0.12f, size.height * 0.74f), Offset(size.width * 0.88f, size.height * 0.28f), strokeWidth = 18f)
            drawLine(roadColor, Offset(size.width * 0.06f, size.height * 0.42f), Offset(size.width * 0.96f, size.height * 0.56f), strokeWidth = 14f)

            val safeCount = zoneCount.coerceAtLeast(0)
            if (safeCount == 0) return@Canvas

            val markerColor = Color(0xFF8A4B20)
            val markerStroke = Color.White.copy(alpha = 0.9f)
            val columns = kotlin.math.ceil(kotlin.math.sqrt(safeCount.toDouble())).toInt().coerceAtLeast(1)
            val rows = (safeCount + columns - 1) / columns
            repeat(safeCount) { index ->
                val column = index % columns
                val row = index / columns
                val x = size.width * (column + 1) / (columns + 1)
                val y = size.height * (row + 1) / (rows + 1)
                val jitter = ((index % 7) - 3) * 4f
                drawCircle(markerStroke, radius = 13f, center = Offset(x + jitter, y))
                drawCircle(markerColor, radius = 9f, center = Offset(x + jitter, y))
            }
        }
    }
}

private fun firstZonePosition(zones: List<ZoneDto>): LatLng {
    val first = zones.firstOrNull()
    return LatLng.from(first?.latitude ?: SEOUL_LATITUDE, first?.longitude ?: SEOUL_LONGITUDE)
}

private fun KakaoMap.renderMapLabels(
    zones: List<ZoneDto>,
    hotplaces: List<HotplaceDto>,
    events: List<TrendEventDto>,
    recommendations: List<RecommendationPlaceDto>,
    selectedZoneId: Int?,
    selectedInsightKey: String?,
    markerBitmaps: MapMarkerBitmaps,
) {
    val labelManager = labelManager ?: return
    val layer = labelManager.layer ?: return
    layer.removeAll()
    if (zones.isEmpty() && hotplaces.isEmpty() && events.isEmpty() && recommendations.isEmpty()) {
        return
    }

    val normalStyle = labelManager.addLabelStyles(
        LabelStyles.from(
            LabelStyle.from(markerBitmaps.zone)
                .setApplyDpScale(false)
                .setAnchorPoint(0.5f, 1.0f)
                .setTextStyles(12, AndroidColor.rgb(22, 22, 22), 3, AndroidColor.WHITE),
        ),
    )
    val selectedStyle = labelManager.addLabelStyles(
        LabelStyles.from(
            LabelStyle.from(markerBitmaps.selectedZone)
                .setApplyDpScale(false)
                .setAnchorPoint(0.5f, 1.0f)
                .setTextStyles(13, AndroidColor.rgb(10, 10, 10), 4, AndroidColor.WHITE),
        ),
    )
    val hotplaceStyle = labelManager.addLabelStyles(
        LabelStyles.from(
            LabelStyle.from(markerBitmaps.hotplace)
                .setApplyDpScale(false)
                .setAnchorPoint(0.5f, 1.0f)
                .setTextStyles(12, AndroidColor.rgb(36, 36, 36), 3, AndroidColor.WHITE),
        ),
    )
    val eventStyle = labelManager.addLabelStyles(
        LabelStyles.from(
            LabelStyle.from(markerBitmaps.event)
                .setApplyDpScale(false)
                .setAnchorPoint(0.5f, 1.0f)
                .setTextStyles(12, AndroidColor.rgb(36, 36, 36), 3, AndroidColor.WHITE),
        ),
    )
    val recommendationStyle = labelManager.addLabelStyles(
        LabelStyles.from(
            LabelStyle.from(markerBitmaps.recommendation)
                .setApplyDpScale(false)
                .setAnchorPoint(0.5f, 1.0f)
                .setTextStyles(12, AndroidColor.rgb(22, 22, 22), 3, AndroidColor.WHITE),
        ),
    )
    val selectedInsightStyle = labelManager.addLabelStyles(
        LabelStyles.from(
            LabelStyle.from(markerBitmaps.selectedZone)
                .setApplyDpScale(false)
                .setAnchorPoint(0.5f, 1.0f)
                .setTextStyles(13, AndroidColor.rgb(10, 10, 10), 4, AndroidColor.WHITE),
        ),
    )

    zones.forEach { zone ->
        val isSelected = zone.id == selectedZoneId
        val options = LabelOptions
            .from("zone-${zone.id}", LatLng.from(zone.latitude, zone.longitude))
            .setClickable(true)
            .setTag(zone.id)
            .setStyles(if (isSelected) selectedStyle else normalStyle)
            .setTexts(LabelTextBuilder().setTexts(if (isSelected) zone.title else ""))
        layer.addLabel(options)
    }

    hotplaces.take(MAX_RENDERED_INSIGHT_MARKERS).forEach { place ->
        val isSelected = isSelectedInsightMarker("hot:${place.id}", selectedInsightKey)
        val options = LabelOptions
            .from("hot-${place.id}", LatLng.from(place.latitude, place.longitude))
            .setClickable(true)
            .setTag("hot:${place.id}")
            .setStyles(if (isSelected) selectedInsightStyle else hotplaceStyle)
            .setTexts(LabelTextBuilder().setTexts(formatHotplaceMapLabel(place)))
        layer.addLabel(options)
    }

    events.take(MAX_RENDERED_INSIGHT_MARKERS).forEach { event ->
        val isSelected = isSelectedInsightMarker("event:${event.id}", selectedInsightKey)
        val options = LabelOptions
            .from("event-${event.id}", LatLng.from(event.latitude, event.longitude))
            .setClickable(true)
            .setTag("event:${event.id}")
            .setStyles(if (isSelected) selectedInsightStyle else eventStyle)
            .setTexts(LabelTextBuilder().setTexts(event.title.take(12)))
        layer.addLabel(options)
    }

    recommendations.take(MAX_RENDERED_INSIGHT_MARKERS).forEach { place ->
        val isSelected = isSelectedInsightMarker(place.mapKey, selectedInsightKey)
        val options = LabelOptions
            .from("recommendation-${place.stableKey}", LatLng.from(place.latitude, place.longitude))
            .setClickable(true)
            .setTag(place.mapKey)
            .setStyles(if (isSelected) selectedInsightStyle else recommendationStyle)
            .setTexts(LabelTextBuilder().setTexts(place.name.take(12)))
        layer.addLabel(options)
    }
}

private data class MapMarkerBitmaps(
    val zone: Bitmap,
    val selectedZone: Bitmap,
    val hotplace: Bitmap,
    val event: Bitmap,
    val recommendation: Bitmap,
)

internal fun shouldMoveCameraToInsight(
    lastFocusedInsightKey: String?,
    focus: MapInsightFocus?,
): Boolean = focus != null && focus.key != lastFocusedInsightKey

internal fun shouldMoveCameraToCurrentLocation(
    lastRequestId: Long?,
    focus: MapCurrentLocationFocus?,
): Boolean = focus != null && focus.requestId != lastRequestId

internal fun isSelectedInsightMarker(
    markerKey: String,
    selectedInsightKey: String?,
): Boolean = markerKey == selectedInsightKey

private fun createMapMarkerBitmaps(context: Context): MapMarkerBitmaps = MapMarkerBitmaps(
    zone = createMapPinBitmap(context, ZONE_MARKER_COLOR, 30),
    selectedZone = createMapPinBitmap(context, SELECTED_MARKER_COLOR, 36),
    hotplace = createMapPinBitmap(context, HOTPLACE_MARKER_COLOR, 30),
    event = createMapPinBitmap(context, EVENT_MARKER_COLOR, 30),
    recommendation = createMapPinBitmap(context, RECOMMENDATION_MARKER_COLOR, 32),
)

private fun createMapPinBitmap(
    context: Context,
    color: Long,
    sizeDp: Int,
): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (sizeDp * density).roundToInt().coerceAtLeast(sizeDp)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val centerX = size / 2f
    val centerY = size * 0.37f
    val outerRadius = size * 0.29f
    val borderWidth = (2.2f * density).coerceAtMost(size * 0.08f)

    fun drawPin(fillColor: Int, radius: Float, pointY: Float) {
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { this.color = fillColor }
        val path = AndroidPath().apply {
            moveTo(centerX - radius * 0.68f, centerY + radius * 0.52f)
            lineTo(centerX + radius * 0.68f, centerY + radius * 0.52f)
            lineTo(centerX, pointY)
            close()
        }
        canvas.drawPath(path, paint)
        canvas.drawCircle(centerX, centerY, radius, paint)
    }

    drawPin(AndroidColor.WHITE, outerRadius, size * 0.96f)
    drawPin(color.toInt(), outerRadius - borderWidth, size * 0.88f)
    canvas.drawCircle(
        centerX,
        centerY,
        (outerRadius - borderWidth) * 0.32f,
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { this.color = AndroidColor.WHITE },
    )
    return bitmap
}

internal const val ZONE_MARKER_COLOR: Long = 0xFF315A3D
internal const val SELECTED_MARKER_COLOR: Long = 0xFF111214
internal const val HOTPLACE_MARKER_COLOR: Long = 0xFFF0642D
internal const val EVENT_MARKER_COLOR: Long = 0xFF3478B8
internal const val RECOMMENDATION_MARKER_COLOR: Long = 0xFF202124
internal const val MAX_RENDERED_INSIGHT_MARKERS = 8
private const val INSIGHT_ZOOM_LEVEL = 15
private const val CURRENT_LOCATION_ZOOM_LEVEL = 16

internal fun mapAccessibilityDescription(
    zoneCount: Int,
    hotplaceCount: Int,
    eventCount: Int,
    recommendationCount: Int = 0,
): String {
    val visibleItems = listOfNotNull(
        zoneCount.takeIf { it > 0 }?.let { "흡연구역 ${it}개" },
        hotplaceCount.takeIf { it > 0 }?.let { "실시간 핫플 ${it}개" },
        eventCount.takeIf { it > 0 }?.let { "팝업 ${it}개" },
        recommendationCount.takeIf { it > 0 }?.let { "추천 장소 ${it}개" },
    )
    val summary = visibleItems.joinToString(", ").ifBlank { "표시할 장소 없음" }
    return "너굴맵 지도. $summary. 접근성 작업에서 장소를 선택하면 기존 상세 패널이 열립니다."
}

private enum class InsightMapLayer {
    Hotplaces,
    Events,
    Recommendations,
}

internal fun formatHotplaceMapLabel(place: HotplaceDto): String {
    val peopleRange = formatCompactPeopleRange(place.estimatedMinPeople, place.estimatedMaxPeople)
    return if (peopleRange == null) {
        place.name.take(12)
    } else {
        "${place.name.take(8)} $peopleRange"
    }
}

private fun formatCompactPeopleRange(minPeople: Int?, maxPeople: Int?): String? {
    if (minPeople == null || maxPeople == null || minPeople <= 0 || maxPeople <= 0) {
        return null
    }
    return if (minPeople == maxPeople) {
        formatCompactPeopleCount(minPeople)
    } else {
        "${formatCompactPeopleCount(minPeople)}-${formatCompactPeopleCount(maxPeople)}"
    }
}

private fun formatCompactPeopleCount(count: Int): String {
    return if (count >= 10_000) {
        val value = count / 10_000.0
        val rounded = kotlin.math.round(value * 10) / 10
        if (rounded % 1.0 == 0.0) {
            "${rounded.toInt()}만"
        } else {
            "${rounded}만"
        }
    } else {
        "${kotlin.math.max(1, kotlin.math.round(count / 1000.0).toInt())}천"
    }
}

private fun KakaoMap.visibleMapBounds(): MapBounds? {
    val viewport = viewport ?: return null
    if (viewport.width() <= 0 || viewport.height() <= 0) {
        return null
    }

    val points = listOfNotNull(
        fromScreenPoint(viewport.left, viewport.top),
        fromScreenPoint(viewport.right, viewport.top),
        fromScreenPoint(viewport.left, viewport.bottom),
        fromScreenPoint(viewport.right, viewport.bottom),
    )
    if (points.isEmpty()) {
        return null
    }

    val latitudes = points.map { it.latitude }
    val longitudes = points.map { it.longitude }
    return MapBounds(
        minLat = latitudes.minOrNull() ?: return null,
        maxLat = latitudes.maxOrNull() ?: return null,
        minLng = longitudes.minOrNull() ?: return null,
        maxLng = longitudes.maxOrNull() ?: return null,
    )
}

private const val SEOUL_LATITUDE = 37.5665
private const val SEOUL_LONGITUDE = 126.9780
private const val KAKAO_MAP_TAG = "KakaoZoneMap"
