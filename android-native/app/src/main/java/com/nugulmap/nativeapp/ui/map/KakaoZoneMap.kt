package com.nugulmap.nativeapp.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
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
    modifier: Modifier = Modifier,
) {
    if (BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank()) {
        MapFallback(
            zoneCount = zones.size,
            isLoading = isLoading,
            errorMessage = "KAKAO_NATIVE_APP_KEY가 없어 지도 SDK 대신 기본 화면을 표시합니다." +
                errorMessage?.let { "\n$it" }.orEmpty(),
            modifier = modifier,
        )
        return
    }

    val latestZones by rememberUpdatedState(zones)
    val latestOnZoneSelected by rememberUpdatedState(onZoneSelected)
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var mapError by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(28.dp))
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
                                mapError = exception.localizedMessage ?: "지도 SDK 초기화 실패"
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
                    .background(Color.White.copy(alpha = 0.88f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (mapError != null || errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
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
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFFE7F0E8)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("MAP", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isLoading) "구역 불러오는 중" else "운영 API에서 ${zoneCount}개 구역 로드",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
