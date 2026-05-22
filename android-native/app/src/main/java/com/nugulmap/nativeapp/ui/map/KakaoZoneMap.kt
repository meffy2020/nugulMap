package com.nugulmap.nativeapp.ui.map

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
import com.kakao.vectormap.label.LabelTextBuilder
import com.nugulmap.nativeapp.BuildConfig
import com.nugulmap.nativeapp.data.dto.ZoneDto

@Composable
fun KakaoZoneMap(
    zones: List<ZoneDto>,
    selectedZoneId: Int?,
    onZoneSelected: (Int) -> Unit,
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
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var shouldShowFallback by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

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
                factory = { context ->
                    MapView(context).also { view ->
                        mapView = view
                        view.start(
                            object : MapLifeCycleCallback() {
                                override fun onMapDestroy() = Unit

                                override fun onMapError(exception: Exception) {
                                    Log.w(KAKAO_MAP_TAG, "Kakao map runtime failed (${exception::class.java.simpleName}); showing quiet fallback")
                                    shouldShowFallback = true
                                }
                            },
                            object : KakaoMapReadyCallback() {
                                override fun getPosition(): LatLng = firstZonePosition(latestZones)

                                override fun getZoomLevel(): Int = 15

                                override fun onMapReady(map: KakaoMap) {
                                    kakaoMap = map
                                    map.setOnLabelClickListener { _, _, label ->
                                        (label.tag as? Int)?.let(latestOnZoneSelected)
                                        true
                                    }
                                }
                            },
                        )
                    }
                },
            )
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.resume()
                Lifecycle.Event.ON_PAUSE -> mapView?.pause()
                Lifecycle.Event.ON_DESTROY -> mapView?.finish()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.finish()
        }
    }

    LaunchedEffect(kakaoMap, zones, selectedZoneId) {
        kakaoMap?.renderZones(zones, selectedZoneId)
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

private fun KakaoMap.renderZones(zones: List<ZoneDto>, selectedZoneId: Int?) {
    val layer = labelManager?.layer ?: return
    layer.removeAll()
    if (zones.isEmpty()) {
        moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(SEOUL_LATITUDE, SEOUL_LONGITUDE), 14))
        return
    }

    zones.forEach { zone ->
        val options = LabelOptions
            .from("zone-${zone.id}", LatLng.from(zone.latitude, zone.longitude))
            .setClickable(true)
            .setTag(zone.id)
            .setTexts(LabelTextBuilder().setTexts(if (zone.id == selectedZoneId) "● ${zone.title}" else zone.title))
        layer.addLabel(options)
    }

    val selected = zones.firstOrNull { it.id == selectedZoneId } ?: zones.first()
    moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(selected.latitude, selected.longitude), 15))
}

private const val SEOUL_LATITUDE = 37.5665
private const val SEOUL_LONGITUDE = 126.9780
private const val KAKAO_MAP_TAG = "KakaoZoneMap"
