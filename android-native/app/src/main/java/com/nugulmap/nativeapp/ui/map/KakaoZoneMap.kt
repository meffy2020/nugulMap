package com.nugulmap.nativeapp.ui.map

import android.graphics.Color as AndroidColor
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.nugulmap.nativeapp.R
import com.nugulmap.nativeapp.data.dto.HotplaceDto
import com.nugulmap.nativeapp.data.dto.MapBounds
import com.nugulmap.nativeapp.data.dto.TrendEventDto
import com.nugulmap.nativeapp.data.dto.ZoneDto

@Composable
fun KakaoZoneMap(
    zones: List<ZoneDto>,
    hotplaces: List<HotplaceDto>,
    events: List<TrendEventDto>,
    selectedZoneId: Int?,
    onZoneSelected: (Int) -> Unit,
    onHotplaceSelected: (String) -> Unit,
    onEventSelected: (String) -> Unit,
    onVisibleBoundsChanged: (MapBounds) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    if (BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank()) {
        MapFallback(
            zoneCount = zones.size,
            modifier = modifier,
        )
        return
    }

    val context = LocalContext.current
    val latestZones by rememberUpdatedState(zones)
    val latestOnZoneSelected by rememberUpdatedState(onZoneSelected)
    val latestOnHotplaceSelected by rememberUpdatedState(onHotplaceSelected)
    val latestOnEventSelected by rememberUpdatedState(onEventSelected)
    val latestOnVisibleBoundsChanged by rememberUpdatedState(onVisibleBoundsChanged)
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    var shouldShowFallback by remember { mutableStateOf(false) }
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
        modifier = modifier
            .background(Color(0xFFE7F0E8)),
    ) {
        if (shouldShowFallback) {
            MapFallback(
                zoneCount = zones.size,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            AndroidView(
                modifier = Modifier.matchParentSize(),
                factory = { mapView },
            )
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        Log.i(KAKAO_MAP_TAG, "Starting Kakao MapView")
        mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() {
                    Log.i(KAKAO_MAP_TAG, "Kakao map destroyed")
                }

                override fun onMapError(exception: Exception) {
                    Log.w(KAKAO_MAP_TAG, "Kakao map runtime failed (${exception::class.java.simpleName}); showing quiet fallback")
                    shouldShowFallback = true
                }

                override fun onMapResumed() {
                    Log.i(KAKAO_MAP_TAG, "Kakao map resumed")
                }

                override fun onMapPaused() {
                    Log.i(KAKAO_MAP_TAG, "Kakao map paused")
                }
            },
            object : KakaoMapReadyCallback() {
                override fun getPosition(): LatLng = firstZonePosition(latestZones)

                override fun getZoomLevel(): Int = 15

                override fun onMapReady(map: KakaoMap) {
                    Log.i(KAKAO_MAP_TAG, "Kakao map ready")
                    kakaoMap = map
                    map.setOnLabelClickListener { _, _, label ->
                        when (val tag = label.tag) {
                            is Int -> latestOnZoneSelected(tag)
                            is String -> {
                                when {
                                    tag.startsWith("hot:") -> latestOnHotplaceSelected(tag.removePrefix("hot:"))
                                    tag.startsWith("event:") -> latestOnEventSelected(tag.removePrefix("event:"))
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

    LaunchedEffect(kakaoMap, zones, hotplaces, events, selectedZoneId) {
        kakaoMap?.let { map ->
            runCatching { map.renderMapLabels(zones, hotplaces, events, selectedZoneId) }
                .onFailure { throwable ->
                    Log.w(KAKAO_MAP_TAG, "Kakao map label render failed (${throwable::class.java.simpleName})")
                }
        }
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
    selectedZoneId: Int?,
) {
    val labelManager = labelManager ?: return
    val layer = labelManager.layer ?: return
    layer.removeAll()
    if (zones.isEmpty() && hotplaces.isEmpty() && events.isEmpty()) {
        return
    }

    val normalStyle = labelManager.addLabelStyles(
        LabelStyles.from(
            LabelStyle
                .from(R.drawable.ic_zone_marker)
                .setAnchorPoint(0.5f, 1.0f)
                .setTextStyles(12, AndroidColor.rgb(22, 22, 22), 3, AndroidColor.WHITE),
        ),
    )
    val selectedStyle = labelManager.addLabelStyles(
        LabelStyles.from(
            LabelStyle
                .from(R.drawable.ic_zone_marker_selected)
                .setAnchorPoint(0.5f, 1.0f)
                .setTextStyles(13, AndroidColor.rgb(10, 10, 10), 4, AndroidColor.WHITE),
        ),
    )
    val hotplaceStyle = labelManager.addLabelStyles(
        LabelStyles.from(
            LabelStyle
                .from(R.drawable.ic_hotplace_marker)
                .setAnchorPoint(0.5f, 0.5f)
                .setTextStyles(12, AndroidColor.rgb(36, 36, 36), 3, AndroidColor.WHITE),
        ),
    )
    val eventStyle = labelManager.addLabelStyles(
        LabelStyles.from(
            LabelStyle
                .from(R.drawable.ic_event_marker)
                .setAnchorPoint(0.5f, 0.5f)
                .setTextStyles(12, AndroidColor.rgb(36, 36, 36), 3, AndroidColor.WHITE),
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

    hotplaces.take(8).forEach { place ->
        val options = LabelOptions
            .from("hot-${place.id}", LatLng.from(place.latitude, place.longitude))
            .setClickable(true)
            .setTag("hot:${place.id}")
            .setStyles(hotplaceStyle)
            .setTexts(LabelTextBuilder().setTexts(formatHotplaceMapLabel(place)))
        layer.addLabel(options)
    }

    events.take(8).forEach { event ->
        val options = LabelOptions
            .from("event-${event.id}", LatLng.from(event.latitude, event.longitude))
            .setClickable(true)
            .setTag("event:${event.id}")
            .setStyles(eventStyle)
            .setTexts(LabelTextBuilder().setTexts(event.title.take(12)))
        layer.addLabel(options)
    }
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
