package com.nugulmap.nativeapp.data.repository

import com.nugulmap.nativeapp.core.auth.AuthSessionStore
import com.nugulmap.nativeapp.data.api.NugulApiClient
import com.nugulmap.nativeapp.data.api.NugulApiService
import com.nugulmap.nativeapp.data.dto.ApiEnvelope
import com.nugulmap.nativeapp.data.dto.LocationLetterCreateRequest
import com.nugulmap.nativeapp.data.dto.LocationLetterDto
import com.nugulmap.nativeapp.data.dto.LocationLetterLikeState
import com.nugulmap.nativeapp.data.dto.LocationLetterNearbyPayload
import com.nugulmap.nativeapp.data.dto.LocationLetterPayload
import com.nugulmap.nativeapp.data.dto.LocationLetterReportPayload
import com.nugulmap.nativeapp.data.dto.ReviewReportRequest

class LocationLetterRepository internal constructor(
    private val api: LocationLetterApi,
    private val authenticatedRequests: AuthenticatedRequestExecutor? = null,
) {
    constructor(
        apiService: NugulApiService = NugulApiClient.service,
        tokenStore: AuthSessionStore? = null,
        authenticatedRequests: AuthenticatedRequestExecutor? =
            tokenStore?.let { AuthenticatedRequestExecutor(it, apiService) },
    ) : this(
        api = RetrofitLocationLetterApi(apiService),
        authenticatedRequests = authenticatedRequests,
    )

    suspend fun loadNearby(
        latitude: Double,
        longitude: Double,
        limit: Int = DEFAULT_LETTER_LIMIT,
    ): LocationLetterNearbyPayload {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "위도 정보가 올바르지 않습니다." }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "경도 정보가 올바르지 않습니다." }

        val response = optionalAuthentication { authorization ->
            api.getNearby(
                authorization = authorization,
                latitude = latitude,
                longitude = longitude,
                limit = limit.coerceIn(1, MAX_LETTER_LIMIT),
            )
        }
        if (!response.success) {
            error(response.message ?: "이곳의 편지를 불러오지 못했습니다.")
        }
        return response.data ?: LocationLetterNearbyPayload.empty()
    }

    suspend fun create(latitude: Double, longitude: Double, content: String): LocationLetterDto {
        val normalized = content.trim()
        require(normalized.isNotBlank()) { "남길 문장을 입력해 주세요." }
        require(normalized.length <= MAX_LETTER_CONTENT_LENGTH) { "편지는 500자까지 남길 수 있습니다." }

        val response = authenticated { authorization ->
            api.create(
                authorization = authorization,
                request = LocationLetterCreateRequest(
                    latitude = latitude,
                    longitude = longitude,
                    content = normalized,
                ),
            )
        }
        if (!response.success) {
            error(response.message ?: "편지를 남기지 못했습니다.")
        }
        return response.data?.letter ?: error("편지 등록 응답이 올바르지 않습니다.")
    }

    suspend fun delete(letterId: Long) {
        authenticated { authorization -> api.delete(authorization, letterId) }
    }

    suspend fun setLiked(letterId: Long, liked: Boolean): LocationLetterLikeState {
        val response = authenticated { authorization ->
            api.setLiked(authorization, letterId, liked)
        }
        if (!response.success) {
            error(response.message ?: "좋아요를 반영하지 못했습니다.")
        }
        return response.data ?: error("좋아요 응답이 올바르지 않습니다.")
    }

    suspend fun report(letterId: Long, reason: String) {
        val response = authenticated { authorization ->
            api.report(
                authorization = authorization,
                letterId = letterId,
                request = ReviewReportRequest(reason = reason),
            )
        }
        if (!response.success) {
            error(response.message ?: "편지를 신고하지 못했습니다.")
        }
    }

    private suspend fun <T> optionalAuthentication(request: suspend (String?) -> T): T {
        val executor = authenticatedRequests
        if (executor?.hasSession() != true) {
            return request(null)
        }
        return try {
            executor.execute { token -> request(token.bearer()) }
        } catch (_: AuthSessionExpiredException) {
            request(null)
        }
    }

    private suspend fun <T> authenticated(request: suspend (String) -> T): T =
        authenticatedRequests?.execute { token -> request(token.bearer()) }
            ?: error("로그인이 필요합니다.")

    private fun String.bearer(): String = "Bearer $this"
}

internal interface LocationLetterApi {
    suspend fun getNearby(
        authorization: String?,
        latitude: Double,
        longitude: Double,
        limit: Int,
    ): ApiEnvelope<LocationLetterNearbyPayload>

    suspend fun create(
        authorization: String,
        request: LocationLetterCreateRequest,
    ): ApiEnvelope<LocationLetterPayload>

    suspend fun delete(authorization: String, letterId: Long)

    suspend fun report(
        authorization: String,
        letterId: Long,
        request: ReviewReportRequest,
    ): ApiEnvelope<LocationLetterReportPayload>

    suspend fun setLiked(
        authorization: String,
        letterId: Long,
        liked: Boolean,
    ): ApiEnvelope<LocationLetterLikeState>
}

private class RetrofitLocationLetterApi(
    private val service: NugulApiService,
) : LocationLetterApi {
    override suspend fun getNearby(
        authorization: String?,
        latitude: Double,
        longitude: Double,
        limit: Int,
    ) = service.getLocationLetters(authorization, latitude, longitude, limit)

    override suspend fun create(
        authorization: String,
        request: LocationLetterCreateRequest,
    ) = service.createLocationLetter(authorization, request)

    override suspend fun delete(authorization: String, letterId: Long) =
        service.deleteLocationLetter(authorization, letterId)

    override suspend fun report(
        authorization: String,
        letterId: Long,
        request: ReviewReportRequest,
    ) = service.reportLocationLetter(authorization, letterId, request)

    override suspend fun setLiked(
        authorization: String,
        letterId: Long,
        liked: Boolean,
    ) = if (liked) {
        service.likeLocationLetter(authorization, letterId)
    } else {
        service.unlikeLocationLetter(authorization, letterId)
    }
}

internal const val DEFAULT_LETTER_LIMIT = 30
internal const val MAX_LETTER_LIMIT = 50
internal const val MAX_LETTER_CONTENT_LENGTH = 500
