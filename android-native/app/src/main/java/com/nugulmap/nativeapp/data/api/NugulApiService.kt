package com.nugulmap.nativeapp.data.api

import com.nugulmap.nativeapp.data.dto.ApiEnvelope
import com.nugulmap.nativeapp.data.dto.AuthTokenResponse
import com.nugulmap.nativeapp.data.dto.MobileOAuthExchangeRequest
import com.nugulmap.nativeapp.data.dto.UserProfilePayload
import com.nugulmap.nativeapp.data.dto.ZoneBoundsPayload
import com.nugulmap.nativeapp.data.dto.ZoneCreatePayload
import com.nugulmap.nativeapp.data.dto.ZoneDto
import com.nugulmap.nativeapp.data.dto.ZoneReviewCreateRequest
import com.nugulmap.nativeapp.data.dto.ZoneReviewDto
import com.nugulmap.nativeapp.data.dto.ZoneReviewPayload
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface NugulApiService {
    @GET("api/zones/bounds")
    suspend fun getZonesByBounds(
        @Query("minLat") minLat: Double,
        @Query("maxLat") maxLat: Double,
        @Query("minLng") minLng: Double,
        @Query("maxLng") maxLng: Double,
    ): ApiEnvelope<ZoneBoundsPayload>

    @POST("api/auth/mobile/exchange")
    suspend fun exchangeMobileOAuthCode(
        @Body request: MobileOAuthExchangeRequest,
    ): ApiEnvelope<AuthTokenResponse>

    @GET("api/auth/me")
    suspend fun getCurrentUser(
        @Header("Authorization") authorization: String,
    ): ApiEnvelope<UserProfilePayload>

    @Multipart
    @POST("api/users/profile-setup")
    suspend fun completeProfileSetup(
        @Header("Authorization") authorization: String,
        @Part("nickname") nickname: RequestBody,
    ): ApiEnvelope<UserProfilePayload>

    @GET("api/zones/my")
    suspend fun getMyZones(
        @Header("Authorization") authorization: String,
    ): ApiEnvelope<ZoneBoundsPayload>

    @Multipart
    @POST("api/zones")
    suspend fun createZone(
        @Header("Authorization") authorization: String,
        @Part("data") data: RequestBody,
    ): ApiEnvelope<ZoneCreateResponsePayload>

    @GET("api/zones/{zoneId}/reviews")
    suspend fun getZoneReviews(
        @Path("zoneId") zoneId: Int,
    ): ApiEnvelope<ZoneReviewPayload>

    @POST("api/zones/{zoneId}/reviews")
    suspend fun createZoneReview(
        @Header("Authorization") authorization: String,
        @Path("zoneId") zoneId: Int,
        @Body request: ZoneReviewCreateRequest,
    ): ApiEnvelope<ZoneReviewCreateResponsePayload>
}

data class ZoneCreateResponsePayload(
    val zone: ZoneDto? = null,
)

data class ZoneReviewCreateResponsePayload(
    val review: ZoneReviewDto? = null,
)
