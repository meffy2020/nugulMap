package com.nugulmap.nativeapp.data.api

import com.nugulmap.nativeapp.data.dto.ApiEnvelope
import com.nugulmap.nativeapp.data.dto.AccountDeletionPayload
import com.nugulmap.nativeapp.data.dto.AuthTokenResponse
import com.nugulmap.nativeapp.data.dto.MapInsightPayload
import com.nugulmap.nativeapp.data.dto.LocationLetterCreateRequest
import com.nugulmap.nativeapp.data.dto.LocationLetterLikeState
import com.nugulmap.nativeapp.data.dto.LocationLetterNearbyPayload
import com.nugulmap.nativeapp.data.dto.LocationLetterPayload
import com.nugulmap.nativeapp.data.dto.LocationLetterReportPayload
import com.nugulmap.nativeapp.data.dto.MobileOAuthExchangeRequest
import com.nugulmap.nativeapp.data.dto.ReviewReportPayload
import com.nugulmap.nativeapp.data.dto.ReviewReportRequest
import com.nugulmap.nativeapp.data.dto.RefreshTokenRequest
import com.nugulmap.nativeapp.data.dto.RefreshTokenResponse
import com.nugulmap.nativeapp.data.dto.RecommendationInteractionRequest
import com.nugulmap.nativeapp.data.dto.RecommendationInteractionResponse
import com.nugulmap.nativeapp.data.dto.RecommendationPlacesPayload
import com.nugulmap.nativeapp.data.dto.RecommendationPreferenceDto
import com.nugulmap.nativeapp.data.dto.RecommendationPreferenceUpdateRequest
import com.nugulmap.nativeapp.data.dto.UserBlockPayload
import com.nugulmap.nativeapp.data.dto.UserProfilePayload
import com.nugulmap.nativeapp.data.dto.SavedRecommendationDto
import com.nugulmap.nativeapp.data.dto.SavedRecommendationRequest
import com.nugulmap.nativeapp.data.dto.ZoneBoundsPayload
import com.nugulmap.nativeapp.data.dto.ZoneCreatePayload
import com.nugulmap.nativeapp.data.dto.ZoneDto
import com.nugulmap.nativeapp.data.dto.ZoneReviewCreateRequest
import com.nugulmap.nativeapp.data.dto.ZoneReviewDto
import com.nugulmap.nativeapp.data.dto.ZoneReviewPayload
import com.nugulmap.nativeapp.data.dto.ZoneReportPayload
import com.nugulmap.nativeapp.data.dto.ZoneReportRequest
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.PUT

interface NugulApiService {
    @GET("api/zones/search")
    suspend fun searchZones(
        @Query("keyword") keyword: String,
        @Query("limit") limit: Int = 100,
    ): ApiEnvelope<ZoneBoundsPayload>

    @GET("api/location-letters")
    suspend fun getLocationLetters(
        @Header("Authorization") authorization: String? = null,
        @Query("lat") latitude: Double,
        @Query("lng") longitude: Double,
        @Query("limit") limit: Int = 30,
    ): ApiEnvelope<LocationLetterNearbyPayload>

    @POST("api/location-letters")
    suspend fun createLocationLetter(
        @Header("Authorization") authorization: String,
        @Body request: LocationLetterCreateRequest,
    ): ApiEnvelope<LocationLetterPayload>

    @DELETE("api/location-letters/{letterId}")
    suspend fun deleteLocationLetter(
        @Header("Authorization") authorization: String,
        @Path("letterId") letterId: Long,
    )

    @PUT("api/location-letters/{letterId}/like")
    suspend fun likeLocationLetter(
        @Header("Authorization") authorization: String,
        @Path("letterId") letterId: Long,
    ): ApiEnvelope<LocationLetterLikeState>

    @DELETE("api/location-letters/{letterId}/like")
    suspend fun unlikeLocationLetter(
        @Header("Authorization") authorization: String,
        @Path("letterId") letterId: Long,
    ): ApiEnvelope<LocationLetterLikeState>

    @POST("api/location-letters/{letterId}/reports")
    suspend fun reportLocationLetter(
        @Header("Authorization") authorization: String,
        @Path("letterId") letterId: Long,
        @Body request: ReviewReportRequest,
    ): ApiEnvelope<LocationLetterReportPayload>

    @GET("api/zones/bounds")
    suspend fun getZonesByBounds(
        @Query("minLat") minLat: Double,
        @Query("maxLat") maxLat: Double,
        @Query("minLng") minLng: Double,
        @Query("maxLng") maxLng: Double,
    ): ApiEnvelope<ZoneBoundsPayload>

    @GET("api/insights/map")
    suspend fun getMapInsights(
        @Query("keyword") keyword: String? = null,
        @Query("hotplaceLimit") hotplaceLimit: Int = 8,
        @Query("eventLimit") eventLimit: Int = 8,
        @Query("minLat") minLat: Double,
        @Query("maxLat") maxLat: Double,
        @Query("minLng") minLng: Double,
        @Query("maxLng") maxLng: Double,
    ): ApiEnvelope<MapInsightPayload>

    @GET("api/recommendations/places")
    suspend fun getRecommendationPlaces(
        @Header("Authorization") authorization: String? = null,
        @Query("intent") intent: String,
        @Query("lat") latitude: Double,
        @Query("lng") longitude: Double,
        @Query("radiusMeters") radiusMeters: Int,
        @Query("limit") limit: Int = 8,
    ): ApiEnvelope<RecommendationPlacesPayload>

    @POST("api/recommendations/interactions")
    suspend fun recordRecommendationInteraction(
        @Header("Authorization") authorization: String,
        @Body request: RecommendationInteractionRequest,
    ): ApiEnvelope<RecommendationInteractionResponse>

    @GET("api/recommendations/saved-items")
    suspend fun getSavedRecommendations(
        @Header("Authorization") authorization: String,
    ): ApiEnvelope<List<SavedRecommendationDto>>

    @POST("api/recommendations/saved-items")
    suspend fun saveRecommendation(
        @Header("Authorization") authorization: String,
        @Body request: SavedRecommendationRequest,
    ): ApiEnvelope<SavedRecommendationDto>

    @DELETE("api/recommendations/saved-items")
    suspend fun deleteSavedRecommendation(
        @Header("Authorization") authorization: String,
        @Query("itemType") itemType: String = "PLACE",
        @Query("provider") provider: String,
        @Query("itemId") itemId: String,
    )

    @GET("api/recommendations/preferences")
    suspend fun getRecommendationPreferences(
        @Header("Authorization") authorization: String,
    ): ApiEnvelope<RecommendationPreferenceDto>

    @PUT("api/recommendations/preferences")
    suspend fun updateRecommendationPreferences(
        @Header("Authorization") authorization: String,
        @Body request: RecommendationPreferenceUpdateRequest,
    ): ApiEnvelope<RecommendationPreferenceDto>

    @POST("api/auth/mobile/exchange")
    suspend fun exchangeMobileOAuthCode(
        @Body request: MobileOAuthExchangeRequest,
    ): ApiEnvelope<AuthTokenResponse>

    @POST("api/auth/refresh")
    suspend fun refreshSession(
        @Body request: RefreshTokenRequest,
    ): RefreshTokenResponse

    @GET("api/auth/me")
    suspend fun getCurrentUser(
        @Header("Authorization") authorization: String,
    ): ApiEnvelope<UserProfilePayload>

    @DELETE("api/users/me")
    suspend fun deleteCurrentUser(
        @Header("Authorization") authorization: String,
    ): ApiEnvelope<AccountDeletionPayload>

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
        @Header("Authorization") authorization: String? = null,
        @Path("zoneId") zoneId: Int,
    ): ApiEnvelope<ZoneReviewPayload>

    @POST("api/zones/{zoneId}/reviews")
    suspend fun createZoneReview(
        @Header("Authorization") authorization: String,
        @Path("zoneId") zoneId: Int,
        @Body request: ZoneReviewCreateRequest,
    ): ApiEnvelope<ZoneReviewCreateResponsePayload>

    @POST("api/zones/{zoneId}/reports")
    suspend fun reportZone(
        @Header("Authorization") authorization: String,
        @Path("zoneId") zoneId: Int,
        @Body request: ZoneReportRequest,
    ): ApiEnvelope<ZoneReportPayload>

    @POST("api/zones/{zoneId}/reviews/{reviewId}/reports")
    suspend fun reportZoneReview(
        @Header("Authorization") authorization: String,
        @Path("zoneId") zoneId: Int,
        @Path("reviewId") reviewId: Int,
        @Body request: ReviewReportRequest,
    ): ApiEnvelope<ReviewReportPayload>

    @POST("api/users/{userId}/block")
    suspend fun blockUser(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: Long,
    ): ApiEnvelope<UserBlockPayload>

    @DELETE("api/users/{userId}/block")
    suspend fun unblockUser(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: Long,
    ): ApiEnvelope<UserBlockPayload>
}

data class ZoneCreateResponsePayload(
    val zone: ZoneDto? = null,
)

data class ZoneReviewCreateResponsePayload(
    val review: ZoneReviewDto? = null,
)
