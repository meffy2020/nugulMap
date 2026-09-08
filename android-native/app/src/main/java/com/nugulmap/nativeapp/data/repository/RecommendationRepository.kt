package com.nugulmap.nativeapp.data.repository

import com.nugulmap.nativeapp.core.auth.AuthSessionStore
import com.nugulmap.nativeapp.data.api.NugulApiClient
import com.nugulmap.nativeapp.data.api.NugulApiService
import com.nugulmap.nativeapp.data.dto.ApiEnvelope
import com.nugulmap.nativeapp.data.dto.MAX_PERO_RECOMMENDATIONS
import com.nugulmap.nativeapp.data.dto.MapBounds
import com.nugulmap.nativeapp.data.dto.RecommendationEventType
import com.nugulmap.nativeapp.data.dto.RecommendationInteractionRequest
import com.nugulmap.nativeapp.data.dto.RecommendationInteractionResponse
import com.nugulmap.nativeapp.data.dto.RecommendationIntent
import com.nugulmap.nativeapp.data.dto.RecommendationPlaceDto
import com.nugulmap.nativeapp.data.dto.RecommendationPlacesPayload
import com.nugulmap.nativeapp.data.dto.RecommendationPreferenceDto
import com.nugulmap.nativeapp.data.dto.RecommendationPreferenceUpdateRequest
import com.nugulmap.nativeapp.data.dto.SavedRecommendationDto
import com.nugulmap.nativeapp.data.dto.SavedRecommendationRequest
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class RecommendationRepository internal constructor(
    private val api: RecommendationApi,
    private val authenticatedRequests: AuthenticatedRequestExecutor? = null,
) {
    constructor(
        apiService: NugulApiService = NugulApiClient.service,
        tokenStore: AuthSessionStore? = null,
        authenticatedRequests: AuthenticatedRequestExecutor? =
            tokenStore?.let { AuthenticatedRequestExecutor(it, apiService) },
    ) : this(
        api = RetrofitRecommendationApi(apiService),
        authenticatedRequests = authenticatedRequests,
    )

    fun hasSession(): Boolean = authenticatedRequests?.hasSession() == true

    suspend fun loadPlaces(
        intent: RecommendationIntent,
        bounds: MapBounds,
    ): RecommendationPlacesPayload {
        val viewport = RecommendationViewport.from(bounds, intent)
        val response = optionalAuthentication { authorization ->
            api.getPlaces(
                authorization = authorization,
                intent = intent.apiValue,
                latitude = viewport.latitude,
                longitude = viewport.longitude,
                radiusMeters = viewport.radiusMeters,
                limit = MAX_PERO_RECOMMENDATIONS,
            )
        }
        if (!response.success) {
            error(response.message ?: "맞춤 추천을 불러오지 못했습니다.")
        }
        val payload = response.data ?: error("맞춤 추천 응답이 올바르지 않습니다.")
        if (payload.intent != intent.apiValue) {
            error("요청한 목적과 다른 추천 응답이 도착했습니다.")
        }
        return payload.copy(
            items = payload.items
                .asSequence()
                .filter(RecommendationPlaceDto::isUsable)
                .filter { bounds.contains(it.latitude, it.longitude) }
                .distinctBy(RecommendationPlaceDto::stableKey)
                .take(MAX_PERO_RECOMMENDATIONS)
                .toList(),
        )
    }

    suspend fun getSavedItems(): List<SavedRecommendationDto> {
        val response = authenticated { authorization -> api.getSavedItems(authorization) }
        if (!response.success) {
            error(response.message ?: "저장한 장소를 불러오지 못했습니다.")
        }
        return response.data.orEmpty()
            .filter { it.provider.isNotBlank() && it.itemId.isNotBlank() }
            .distinctBy(SavedRecommendationDto::stableKey)
    }

    suspend fun saveItem(item: RecommendationPlaceDto): SavedRecommendationDto {
        require(item.isUsable) { "저장할 추천 장소 정보가 올바르지 않습니다." }
        val response = authenticated { authorization ->
            api.saveItem(
                authorization,
                SavedRecommendationRequest(
                    provider = item.provider,
                    itemId = item.id,
                    title = item.name,
                    category = item.category,
                    lat = item.latitude,
                    lng = item.longitude,
                    sourceUrl = item.sourceUrl.trim().takeIf(String::isNotEmpty),
                ),
            )
        }
        if (!response.success) {
            error(response.message ?: "장소를 저장하지 못했습니다.")
        }
        return response.data ?: error("장소 저장 응답이 올바르지 않습니다.")
    }

    suspend fun deleteSavedItem(item: RecommendationPlaceDto) {
        authenticated { authorization ->
            api.deleteSavedItem(
                authorization = authorization,
                provider = item.provider,
                itemId = item.id,
            )
        }
    }

    suspend fun getPreference(): RecommendationPreferenceDto {
        val response = authenticated { authorization -> api.getPreference(authorization) }
        if (!response.success) {
            error(response.message ?: "추천 설정을 불러오지 못했습니다.")
        }
        return response.data ?: RecommendationPreferenceDto()
    }

    suspend fun updatePreference(
        request: RecommendationPreferenceUpdateRequest,
    ): RecommendationPreferenceDto {
        val response = authenticated { authorization -> api.updatePreference(authorization, request) }
        if (!response.success) {
            error(response.message ?: "추천 설정을 저장하지 못했습니다.")
        }
        return response.data ?: error("추천 설정 응답이 올바르지 않습니다.")
    }

    suspend fun recordInteraction(
        recommendation: RecommendationPlacesPayload,
        item: RecommendationPlaceDto,
        eventType: RecommendationEventType,
    ): Boolean {
        if (!recommendation.isPersonalized) {
            return false
        }
        require(recommendation.requestId.isNotBlank() && recommendation.modelVersion.isNotBlank()) {
            "개인화 추천 요청 정보가 올바르지 않습니다."
        }
        require(recommendation.items.any { it.stableKey == item.stableKey }) {
            "현재 추천에 없는 장소의 행동은 기록할 수 없습니다."
        }
        val response = authenticated { authorization ->
            api.recordInteraction(
                authorization,
                RecommendationInteractionRequest(
                    requestId = recommendation.requestId,
                    provider = item.provider,
                    itemId = item.id,
                    eventType = eventType.name,
                    intent = recommendation.intent,
                    modelVersion = recommendation.modelVersion,
                ),
            )
        }
        if (!response.success) {
            error(response.message ?: "추천 행동을 기록하지 못했습니다.")
        }
        return true
    }

    private suspend fun <T> optionalAuthentication(request: suspend (String?) -> T): T {
        val executor = authenticatedRequests
        if (executor?.hasSession() != true) {
            return request(null)
        }
        return try {
            executor.execute { accessToken -> request(accessToken.bearer()) }
        } catch (_: AuthSessionExpiredException) {
            request(null)
        }
    }

    private suspend fun <T> authenticated(request: suspend (String) -> T): T =
        authenticatedRequests?.execute { accessToken -> request(accessToken.bearer()) }
            ?: error("로그인이 필요합니다.")

    private fun String.bearer(): String = "Bearer $this"
}

internal interface RecommendationApi {
    suspend fun getPlaces(
        authorization: String?,
        intent: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        limit: Int,
    ): ApiEnvelope<RecommendationPlacesPayload>

    suspend fun recordInteraction(
        authorization: String,
        request: RecommendationInteractionRequest,
    ): ApiEnvelope<RecommendationInteractionResponse>

    suspend fun getSavedItems(authorization: String): ApiEnvelope<List<SavedRecommendationDto>>

    suspend fun saveItem(
        authorization: String,
        request: SavedRecommendationRequest,
    ): ApiEnvelope<SavedRecommendationDto>

    suspend fun deleteSavedItem(authorization: String, provider: String, itemId: String)

    suspend fun getPreference(authorization: String): ApiEnvelope<RecommendationPreferenceDto>

    suspend fun updatePreference(
        authorization: String,
        request: RecommendationPreferenceUpdateRequest,
    ): ApiEnvelope<RecommendationPreferenceDto>
}

private class RetrofitRecommendationApi(
    private val service: NugulApiService,
) : RecommendationApi {
    override suspend fun getPlaces(
        authorization: String?,
        intent: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        limit: Int,
    ) = service.getRecommendationPlaces(
        authorization = authorization,
        intent = intent,
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters,
        limit = limit,
    )

    override suspend fun recordInteraction(
        authorization: String,
        request: RecommendationInteractionRequest,
    ) = service.recordRecommendationInteraction(authorization, request)

    override suspend fun getSavedItems(authorization: String) =
        service.getSavedRecommendations(authorization)

    override suspend fun saveItem(authorization: String, request: SavedRecommendationRequest) =
        service.saveRecommendation(authorization, request)

    override suspend fun deleteSavedItem(authorization: String, provider: String, itemId: String) =
        service.deleteSavedRecommendation(
            authorization = authorization,
            provider = provider,
            itemId = itemId,
        )

    override suspend fun getPreference(authorization: String) =
        service.getRecommendationPreferences(authorization)

    override suspend fun updatePreference(
        authorization: String,
        request: RecommendationPreferenceUpdateRequest,
    ) = service.updateRecommendationPreferences(authorization, request)
}

internal data class RecommendationViewport(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val key: String,
) {
    companion object {
        fun from(bounds: MapBounds, intent: RecommendationIntent): RecommendationViewport {
            val latitude = (bounds.minLat + bounds.maxLat) / 2
            val longitude = (bounds.minLng + bounds.maxLng) / 2
            val radius = listOf(
                bounds.minLat to bounds.minLng,
                bounds.minLat to bounds.maxLng,
                bounds.maxLat to bounds.minLng,
                bounds.maxLat to bounds.maxLng,
            ).maxOf { (cornerLatitude, cornerLongitude) ->
                haversineMeters(latitude, longitude, cornerLatitude, cornerLongitude)
            }
            return RecommendationViewport(
                latitude = latitude,
                longitude = longitude,
                radiusMeters = ceil(radius).toInt().coerceIn(MIN_RECOMMENDATION_RADIUS, MAX_RECOMMENDATION_RADIUS),
                key = "${intent.apiValue}:${bounds.key()}",
            )
        }
    }
}

internal class RecommendationRequestGate {
    private var sequence = 0L
    private var activeKey: String? = null

    fun begin(key: String): Long {
        activeKey = key
        sequence += 1
        return sequence
    }

    fun isCurrent(requestSequence: Long, key: String): Boolean =
        requestSequence == sequence && key == activeKey

    fun invalidate() {
        activeKey = null
        sequence += 1
    }
}

private fun MapBounds.contains(latitude: Double, longitude: Double): Boolean =
    latitude in minLat..maxLat && longitude in minLng..maxLng

private fun haversineMeters(
    startLatitude: Double,
    startLongitude: Double,
    endLatitude: Double,
    endLongitude: Double,
): Double {
    val latitudeDelta = Math.toRadians(endLatitude - startLatitude)
    val longitudeDelta = Math.toRadians(endLongitude - startLongitude)
    val startLatitudeRadians = Math.toRadians(startLatitude)
    val endLatitudeRadians = Math.toRadians(endLatitude)
    val haversine = sin(latitudeDelta / 2).pow(2) +
        cos(startLatitudeRadians) * cos(endLatitudeRadians) * sin(longitudeDelta / 2).pow(2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(max(0.0, haversine)))
}

private const val MIN_RECOMMENDATION_RADIUS = 100
private const val MAX_RECOMMENDATION_RADIUS = 20_000
private const val EARTH_RADIUS_METERS = 6_371_000.0
