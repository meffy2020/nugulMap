package com.nugulmap.nativeapp.data.repository

import com.google.gson.Gson
import com.nugulmap.nativeapp.core.auth.AuthTokenStore
import com.nugulmap.nativeapp.data.api.NugulApiClient
import com.nugulmap.nativeapp.data.api.NugulApiService
import com.nugulmap.nativeapp.data.dto.MapBounds
import com.nugulmap.nativeapp.data.dto.MapInsightPayload
import com.nugulmap.nativeapp.data.dto.ReviewReportRequest
import com.nugulmap.nativeapp.data.dto.ZoneCreatePayload
import com.nugulmap.nativeapp.data.dto.ZoneDto
import com.nugulmap.nativeapp.data.dto.ZoneReviewCreateRequest
import com.nugulmap.nativeapp.data.dto.ZoneReviewDto
import com.nugulmap.nativeapp.data.dto.ZoneReportRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class ZoneRepository(
    private val apiService: NugulApiService = NugulApiClient.service,
    private val tokenStore: AuthTokenStore? = null,
    private val gson: Gson = Gson(),
    private val authenticatedRequests: AuthenticatedRequestExecutor? =
        tokenStore?.let { AuthenticatedRequestExecutor(it, apiService) },
) {
    suspend fun searchZones(keyword: String): List<ZoneDto> {
        val response = apiService.searchZones(keyword.trim())
        if (!response.success) error(response.message ?: "흡연구역 검색에 실패했습니다.")
        return response.data?.zones.orEmpty()
    }

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
        val response = withAuthentication { token -> apiService.getMyZones(token.bearer()) }
        if (!response.success) {
            error(response.message ?: "내 구역 조회에 실패했습니다.")
        }
        return response.data?.zones.orEmpty()
    }

    suspend fun createZone(payload: ZoneCreatePayload): ZoneDto {
        val response = withAuthentication { token ->
            apiService.createZone(
                authorization = token.bearer(),
                data = gson.toJson(payload).toRequestBody(MULTIPART_JSON),
            )
        }
        if (!response.success) {
            error(response.message ?: "구역 등록에 실패했습니다.")
        }
        return response.data?.zone ?: error("구역 등록 응답이 올바르지 않습니다.")
    }

    suspend fun loadReviews(zoneId: Int): List<ZoneReviewDto> {
        val response = if (authenticatedRequests?.hasSession() == true) {
            authenticatedRequests.execute { token ->
                apiService.getZoneReviews(authorization = token.bearer(), zoneId = zoneId)
            }
        } else {
            apiService.getZoneReviews(authorization = null, zoneId = zoneId)
        }
        if (!response.success) {
            error(response.message ?: "리뷰 조회에 실패했습니다.")
        }
        return response.data?.reviews.orEmpty()
    }

    suspend fun createReview(zoneId: Int, content: String): ZoneReviewDto {
        val normalized = content.trim()
        require(normalized.isNotBlank()) { "리뷰 내용을 입력해 주세요." }
        val response = withAuthentication { token ->
            apiService.createZoneReview(
                authorization = token.bearer(),
                zoneId = zoneId,
                request = ZoneReviewCreateRequest(content = normalized),
            )
        }
        if (!response.success) {
            error(response.message ?: "리뷰 등록에 실패했습니다.")
        }
        return response.data?.review ?: error("리뷰 등록 응답이 올바르지 않습니다.")
    }

    suspend fun reportZone(zoneId: Int, reason: String, details: String? = null) {
        val response = withAuthentication { token ->
            apiService.reportZone(
                authorization = token.bearer(),
                zoneId = zoneId,
                request = ZoneReportRequest(
                    reason = reason,
                    details = details?.trim()?.takeIf { it.isNotBlank() },
                ),
            )
        }
        if (!response.success) {
            error(response.message ?: "장소 신고에 실패했습니다.")
        }
    }

    suspend fun reportReview(zoneId: Int, reviewId: Int, reason: String, details: String? = null) {
        val response = withAuthentication { token ->
            apiService.reportZoneReview(
                authorization = token.bearer(),
                zoneId = zoneId,
                reviewId = reviewId,
                request = ReviewReportRequest(
                    reason = reason,
                    details = details?.trim()?.takeIf { it.isNotBlank() },
                ),
            )
        }
        if (!response.success) {
            error(response.message ?: "리뷰 신고에 실패했습니다.")
        }
    }

    suspend fun blockUser(userId: Long) {
        val response = withAuthentication { token ->
            apiService.blockUser(authorization = token.bearer(), userId = userId)
        }
        if (!response.success) {
            error(response.message ?: "사용자 차단에 실패했습니다.")
        }
    }

    suspend fun unblockUser(userId: Long) {
        val response = withAuthentication { token ->
            apiService.unblockUser(authorization = token.bearer(), userId = userId)
        }
        if (!response.success) {
            error(response.message ?: "사용자 차단 해제에 실패했습니다.")
        }
    }

    private suspend fun <T> withAuthentication(request: suspend (String) -> T): T {
        return authenticatedRequests?.execute(request) ?: error("로그인이 필요합니다.")
    }

    private fun String.bearer(): String = "Bearer $this"

    companion object {
        private val MULTIPART_JSON = "application/json; charset=utf-8".toMediaType()
    }
}
