package com.nugulmap.nativeapp.data.repository

import com.google.gson.Gson
import com.nugulmap.nativeapp.core.auth.AuthTokenStore
import com.nugulmap.nativeapp.data.api.NugulApiClient
import com.nugulmap.nativeapp.data.api.NugulApiService
import com.nugulmap.nativeapp.data.dto.MapBounds
import com.nugulmap.nativeapp.data.dto.MapInsightPayload
import com.nugulmap.nativeapp.data.dto.ZoneCreatePayload
import com.nugulmap.nativeapp.data.dto.ZoneDto
import com.nugulmap.nativeapp.data.dto.ZoneReviewCreateRequest
import com.nugulmap.nativeapp.data.dto.ZoneReviewDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class ZoneRepository(
    private val apiService: NugulApiService = NugulApiClient.service,
    private val tokenStore: AuthTokenStore? = null,
    private val gson: Gson = Gson(),
) {
    suspend fun loadCentralSeoulZones(): List<ZoneDto> {
        return loadZones(MapBounds.centralSeoul)
    }

    suspend fun loadZones(bounds: MapBounds): List<ZoneDto> {
        val response = apiService.getZonesByBounds(
            minLat = bounds.minLat,
            maxLat = bounds.maxLat,
            minLng = bounds.minLng,
            maxLng = bounds.maxLng,
        )

        if (!response.success) {
            error(response.message ?: "구역 조회에 실패했습니다.")
        }

        return response.data?.zones.orEmpty()
    }

    suspend fun loadMapInsights(
        bounds: MapBounds,
        keyword: String? = null,
        hotplaceLimit: Int = 8,
        eventLimit: Int = 8,
    ): MapInsightPayload {
        val response = apiService.getMapInsights(
            keyword = keyword?.trim()?.takeIf { it.isNotBlank() },
            hotplaceLimit = hotplaceLimit,
            eventLimit = eventLimit,
            minLat = bounds.minLat,
            maxLat = bounds.maxLat,
            minLng = bounds.minLng,
            maxLng = bounds.maxLng,
        )
        if (!response.success) {
            error(response.message ?: "시즌2 지도 인사이트 조회에 실패했습니다.")
        }
        return response.data ?: MapInsightPayload()
    }

    suspend fun loadMyZones(): List<ZoneDto> {
        val token = requireAccessToken()
        val response = apiService.getMyZones(token.bearer())
        if (!response.success) {
            error(response.message ?: "내 구역 조회에 실패했습니다.")
        }
        return response.data?.zones.orEmpty()
    }

    suspend fun createZone(payload: ZoneCreatePayload): ZoneDto {
        val token = requireAccessToken()
        val response = apiService.createZone(
            authorization = token.bearer(),
            data = gson.toJson(payload).toRequestBody(MULTIPART_JSON),
        )
        if (!response.success) {
            error(response.message ?: "구역 등록에 실패했습니다.")
        }
        return response.data?.zone ?: error("구역 등록 응답이 올바르지 않습니다.")
    }

    suspend fun loadReviews(zoneId: Int): List<ZoneReviewDto> {
        val response = apiService.getZoneReviews(zoneId)
        if (!response.success) {
            error(response.message ?: "리뷰 조회에 실패했습니다.")
        }
        return response.data?.reviews.orEmpty()
    }

    suspend fun createReview(zoneId: Int, content: String): ZoneReviewDto {
        val normalized = content.trim()
        require(normalized.isNotBlank()) { "리뷰 내용을 입력해 주세요." }
        val token = requireAccessToken()
        val response = apiService.createZoneReview(
            authorization = token.bearer(),
            zoneId = zoneId,
            request = ZoneReviewCreateRequest(content = normalized),
        )
        if (!response.success) {
            error(response.message ?: "리뷰 등록에 실패했습니다.")
        }
        return response.data?.review ?: error("리뷰 등록 응답이 올바르지 않습니다.")
    }

    private fun requireAccessToken(): String = tokenStore?.accessToken()?.takeIf { it.isNotBlank() }
        ?: error("로그인이 필요합니다.")

    private fun String.bearer(): String = "Bearer $this"

    companion object {
        private val MULTIPART_JSON = "application/json; charset=utf-8".toMediaType()
    }
}
