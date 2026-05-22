package com.nugulmap.nativeapp.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import com.nugulmap.nativeapp.BuildConfig
import com.nugulmap.nativeapp.data.dto.ZoneDto

@Composable
fun KakaoNativeMap(
    zones: List<ZoneDto>,
    selectedZone: ZoneDto?,
    isLoading: Boolean,
    errorMessage: String?,
    onMapError: (String) -> Unit,
) {
    val appKey = BuildConfig.KAKAO_NATIVE_APP_KEY.trim()
    if (appKey.isBlank()) {
        MapFallback(
            title = "Kakao Native App Key가 없습니다.",
            message = "android-native/local.properties에 KAKAO_NATIVE_APP_KEY를 설정하면 실제 Kakao 지도 SDK가 표시됩니다.",
        )
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var mapStarted by remember { mutableStateOf(false) }
    var mapReady by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(appKey) {
        runCatching { KakaoMapSdk.init(context.applicationContext, appKey) }
            .onFailure { throwable ->
                val message = throwable.localizedMessage ?: "Kakao 지도 SDK 초기화 실패"
                localError = message
                onMapError(message)
            }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFFE7F0E8)),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                MapView(viewContext).also { mapView ->
                    mapViewRef = mapView
                    if (!mapStarted) {
                        mapStarted = true
                        val initialZone = selectedZone ?: zones.firstOrNull()
                        mapView.start(
                            object : MapLifeCycleCallback() {
                                override fun onMapDestroy() = Unit

                                override fun onMapError(error: Exception) {
                                    val message = error.localizedMessage ?: "Kakao 지도 인증 또는 렌더링 실패"
                                    localError = message
                                    onMapError(message)
                                }
                            },
                            object : KakaoMapReadyCallback() {
                                override fun onMapReady(kakaoMap: KakaoMap) {
                                    mapReady = true
                                    localError = null
                                }

                                override fun getPosition(): LatLng = LatLng.from(
                                    initialZone?.latitude ?: DEFAULT_LATITUDE,
                                    initialZone?.longitude ?: DEFAULT_LONGITUDE,
                                )

                                override fun getZoomLevel(): Int = 15

                                override fun getViewName(): String = "NugulMapAndroidNative"
                            },
                        )
                    }
                }
            },
        )

        val overlayText = when {
            localError != null -> localError
            errorMessage != null -> errorMessage
            isLoading -> "구역 불러오는 중"
            !mapReady -> "Kakao 지도 SDK 시작 중"
            selectedZone != null -> selectedZone.title
            else -> "운영 API에서 ${zones.size}개 구역 로드"
        }
        Text(
            text = overlayText.orEmpty(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xCCFFFFFF))
                .padding(12.dp),
            color = if (localError != null || errorMessage != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }

    DisposableEffect(lifecycleOwner, mapViewRef) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef?.resume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef?.pause()
                Lifecycle.Event.ON_DESTROY -> mapViewRef?.finish()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewRef?.finish()
        }
    }
}

@Composable
private fun MapFallback(title: String, message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFFE7F0E8)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$title\n$message",
            modifier = Modifier.padding(20.dp),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private const val DEFAULT_LATITUDE = 37.5665
private const val DEFAULT_LONGITUDE = 126.9780
