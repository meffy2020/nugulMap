package com.nugulmap.nativeapp.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.concurrent.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class CurrentMapLocation(
    val latitude: Double,
    val longitude: Double,
)

class CurrentLocationProvider(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): CurrentMapLocation {
        check(hasPermission()) { "현재 위치를 보려면 위치 권한이 필요합니다." }

        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter(locationManager::isProviderEnabled)
        check(providers.isNotEmpty()) { "기기의 위치 서비스를 켜주세요." }

        val recentLocation = providers
            .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
            .filter { location -> System.currentTimeMillis() - location.time <= RECENT_LOCATION_MAX_AGE_MILLIS }
            .minByOrNull(Location::getAccuracy)
        if (recentLocation != null) {
            return recentLocation.toMapLocation()
        }

        val timeoutPerProvider = (LOCATION_REQUEST_TIMEOUT_MILLIS / providers.size)
            .coerceAtLeast(MIN_PROVIDER_TIMEOUT_MILLIS)
        return firstSuccessfulLocationProvider(providers) { provider ->
            withTimeout(timeoutPerProvider) {
                requestSingleLocation(provider)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleLocation(provider: String): CurrentMapLocation =
        suspendCancellableCoroutine { continuation ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val cancellationSignal = CancellationSignal()
                continuation.invokeOnCancellation { cancellationSignal.cancel() }
                locationManager.getCurrentLocation(
                    provider,
                    cancellationSignal,
                    appContext.mainExecutor,
                ) { location ->
                    if (!continuation.isActive) return@getCurrentLocation
                    if (location == null) {
                        continuation.resumeWithException(IllegalStateException("현재 위치를 확인하지 못했습니다."))
                    } else {
                        continuation.resume(location.toMapLocation())
                    }
                }
                return@suspendCancellableCoroutine
            }

            @Suppress("DEPRECATION")
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    if (continuation.isActive) {
                        continuation.resume(location.toMapLocation())
                    }
                }

                override fun onProviderDisabled(disabledProvider: String) {
                    if (disabledProvider != provider) return
                    locationManager.removeUpdates(this)
                    if (continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException("기기의 위치 서비스를 켜주세요."))
                    }
                }

                @Deprecated("Deprecated in Android")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }
            continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
            @Suppress("DEPRECATION")
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        }

    private fun Location.toMapLocation(): CurrentMapLocation = CurrentMapLocation(
        latitude = latitude,
        longitude = longitude,
    )
}

internal suspend fun <T> firstSuccessfulLocationProvider(
    providers: List<String>,
    request: suspend (String) -> T,
): T {
    require(providers.isNotEmpty()) { "사용 가능한 위치 제공자가 없습니다." }
    var lastFailure: Throwable? = null

    providers.forEach { provider ->
        try {
            return request(provider)
        } catch (throwable: Throwable) {
            if (throwable is CancellationException && throwable !is TimeoutCancellationException) {
                throw throwable
            }
            lastFailure = throwable
        }
    }

    throw IllegalStateException(
        "현재 위치를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.",
        lastFailure,
    )
}

private const val RECENT_LOCATION_MAX_AGE_MILLIS = 60_000L
private const val LOCATION_REQUEST_TIMEOUT_MILLIS = 10_000L
private const val MIN_PROVIDER_TIMEOUT_MILLIS = 3_000L
