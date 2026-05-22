package com.nugulmap.nativeapp.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    isLoading: Boolean,
    errorMessage: String?,
    onZoneSelected: (Int) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    if (BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank()) {
        MapFallback(
            zoneCount = zones.size,
            isLoading = isLoading,
            statusMessage = errorMessage,
            modifier = modifier,
        )
        return
    }

    val context = LocalContext.current
    val latestZones by rememberUpdatedState(zones)
    val latestOnZoneSelected by rememberUpdatedState(onZoneSelected)
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var mapError by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(BuildConfig.KAKAO_NATIVE_APP_KEY) {
        runCatching { KakaoMapSdk.init(context.applicationContext, BuildConfig.KAKAO_NATIVE_APP_KEY.trim()) }
            .onFailure { throwable -> mapError = throwable.localizedMessage ?: "지도를 불러오지 못했어요" }
    }

    Box(
        modifier = modifier
            .background(Color(0xFFE7F0E8)),
    ) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { context ->
                MapView(context).also { view ->
                    mapView = view
                    view.start(
                        object : MapLifeCycleCallback() {
                            override fun onMapDestroy() = Unit

                            override fun onMapError(exception: Exception) {
                                mapError = exception.localizedMessage ?: "지도를 불러오지 못했어요"
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

        if (isLoading || mapError != null || errorMessage != null) {
            Text(
                text = mapError ?: errorMessage ?: "구역 불러오는 중",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
                    .background(Color.White.copy(alpha = 0.90f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    isLoading: Boolean,
    statusMessage: String?,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    Box(
        modifier = modifier
            .background(Color(0xFFEAF2EC)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridColor = Color.White.copy(alpha = 0.42f)
            val roadColor = Color(0xFFD8E1D9).copy(alpha = 0.7f)
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
            val markerColor = Color(0xFF8A4B20)
            val markerStroke = Color.White.copy(alpha = 0.9f)
            val previewCount = zoneCount.coerceIn(1, 36)
            repeat(previewCount) { index ->
                val column = index % 6
                val row = index / 6
                val x = size.width * (0.18f + column * 0.13f + ((row % 2) * 0.035f))
                val y = size.height * (0.30f + row * 0.075f)
                drawCircle(markerStroke, radius = 13f, center = Offset(x, y))
                drawCircle(markerColor, radius = 9f, center = Offset(x, y))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 88.dp, start = 22.dp, end = 22.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.90f))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("지도 미리보기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isLoading) "주변 흡연구역을 불러오는 중" else "서울 중심 ${zoneCount}개 구역을 표시 중",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!statusMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFF8A4B20)),
                    )
                    Spacer(modifier = Modifier.size(7.dp))
                    Text(statusMessage, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
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
