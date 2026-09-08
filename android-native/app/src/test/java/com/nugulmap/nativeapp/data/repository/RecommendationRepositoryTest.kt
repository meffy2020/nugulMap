package com.nugulmap.nativeapp.data.repository

import com.nugulmap.nativeapp.core.auth.AuthSessionStore
import com.nugulmap.nativeapp.data.dto.ApiEnvelope
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
import com.nugulmap.nativeapp.data.dto.MAX_PERO_RECOMMENDATIONS
import com.nugulmap.nativeapp.data.dto.RefreshTokenResponse
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecommendationRepositoryTest {
    private val bounds = MapBounds(
        minLat = 37.50,
        maxLat = 37.60,
        minLng = 126.90,
        maxLng = 127.10,
    )

    @Test
    fun `guest place request is public and returns only eight usable visible candidates`() = runBlocking {
        val api = FakeRecommendationApi().apply {
            places = (1..10).map { index -> place("$index", 37.55, 127.0) } +
                place("outside", 37.70, 127.0) +
                place("invalid", Double.NaN, 127.0)
        }
        val repository = RecommendationRepository(api)

        val payload = repository.loadPlaces(RecommendationIntent.CAFE, bounds)

        assertEquals(null, api.placeAuthorization)
        assertEquals("CAFE", api.placeIntent)
        assertEquals(37.55, api.placeLatitude, absoluteTolerance = 0.000_001)
        assertEquals(127.0, api.placeLongitude, absoluteTolerance = 0.000_001)
        assertEquals(MAX_PERO_RECOMMENDATIONS, api.placeLimit)
        assertTrue(api.placeRadiusMeters in 100..20_000)
        assertEquals((1..8).map(Int::toString), payload.items.map(RecommendationPlaceDto::id))
    }

    @Test
    fun `signed in place request uses bearer token`() = runBlocking {
        val api = FakeRecommendationApi().apply { places = listOf(place("one")) }
        val repository = RecommendationRepository(api, authenticatedExecutor())

        repository.loadPlaces(RecommendationIntent.DATE, bounds)

        assertEquals("Bearer access-token", api.placeAuthorization)
    }

    @Test
    fun `context only mode never uploads interaction`() = runBlocking {
        val api = FakeRecommendationApi()
        val repository = RecommendationRepository(api, authenticatedExecutor())
        val item = place("one")
        val payload = payload(item, personalizationMode = "CONTEXT_ONLY")

        assertFalse(repository.recordInteraction(payload, item, RecommendationEventType.NOT_INTERESTED))
        assertEquals(0, api.interactionCalls)
    }

    @Test
    fun `personalized mode uploads interaction with recommendation request identity`() = runBlocking {
        val api = FakeRecommendationApi()
        val repository = RecommendationRepository(api, authenticatedExecutor())
        val item = place("one")
        val payload = payload(item, personalizationMode = "PERSONALIZED")

        assertTrue(repository.recordInteraction(payload, item, RecommendationEventType.ROUTE))

        assertEquals(1, api.interactionCalls)
        assertEquals("Bearer access-token", api.interactionAuthorization)
        assertEquals("request-1", api.interactionRequest?.requestId)
        assertEquals("model-1", api.interactionRequest?.modelVersion)
        assertEquals("ROUTE", api.interactionRequest?.eventType)
        assertEquals("KAKAO_LOCAL", api.interactionRequest?.provider)
        assertEquals("one", api.interactionRequest?.itemId)
    }

    @Test
    fun `request gate accepts only newest viewport response`() {
        val gate = RecommendationRequestGate()
        val first = gate.begin("DATE:first")
        val second = gate.begin("DATE:second")

        assertFalse(gate.isCurrent(first, "DATE:first"))
        assertTrue(gate.isCurrent(second, "DATE:second"))

        gate.invalidate()
        assertFalse(gate.isCurrent(second, "DATE:second"))
    }

    @Test
    fun `saved items and preference remain available outside personalized mode`() = runBlocking {
        val api = FakeRecommendationApi()
        val repository = RecommendationRepository(api, authenticatedExecutor())
        val item = place("saved")

        repository.saveItem(item)
        repository.deleteSavedItem(item)
        val preference = repository.updatePreference(
            RecommendationPreferenceUpdateRequest(personalizationEnabled = false),
        )

        assertEquals(1, api.saveCalls)
        assertEquals(1, api.deleteCalls)
        assertEquals("KAKAO_LOCAL", api.savedRequest?.provider)
        assertEquals("saved", api.deletedItemId)
        assertFalse(preference.personalizationEnabled)
        assertEquals(false, api.preferenceRequest?.personalizationEnabled)
    }

    private fun authenticatedExecutor(): AuthenticatedRequestExecutor = AuthenticatedRequestExecutor(
        tokenStore = FakeAuthSessionStore(),
        refreshSession = {
            RefreshTokenResponse(success = true, accessToken = "unused", refreshToken = "unused")
        },
    )

    private fun payload(
        item: RecommendationPlaceDto,
        personalizationMode: String,
    ) = RecommendationPlacesPayload(
        requestId = "request-1",
        modelVersion = "model-1",
        dataStatus = "READY",
        personalizationMode = personalizationMode,
        intent = "DATE",
        items = listOf(item),
    )

    private fun place(
        id: String,
        latitude: Double = 37.55,
        longitude: Double = 127.0,
    ) = RecommendationPlaceDto(
        id = id,
        provider = "KAKAO_LOCAL",
        name = "장소 $id",
        category = "카페",
        address = "서울",
        latitude = latitude,
        longitude = longitude,
        sourceUrl = "https://place.map.kakao.com/$id",
    )

    private class FakeAuthSessionStore : AuthSessionStore {
        private var access: String? = "access-token"
        private var refresh: String? = "refresh-token"

        override fun save(accessToken: String, refreshToken: String?) {
            access = accessToken
            refresh = refreshToken
        }

        override fun accessToken(): String? = access

        override fun refreshToken(): String? = refresh

        override fun clear() {
            access = null
            refresh = null
        }
    }

    private class FakeRecommendationApi : RecommendationApi {
        var places: List<RecommendationPlaceDto> = emptyList()
        var placeAuthorization: String? = null
        var placeIntent: String? = null
        var placeLatitude: Double = Double.NaN
        var placeLongitude: Double = Double.NaN
        var placeRadiusMeters: Int = -1
        var placeLimit: Int = -1
        var interactionCalls: Int = 0
        var interactionAuthorization: String? = null
        var interactionRequest: RecommendationInteractionRequest? = null
        var saveCalls: Int = 0
        var deleteCalls: Int = 0
        var savedRequest: SavedRecommendationRequest? = null
        var deletedItemId: String? = null
        var preferenceRequest: RecommendationPreferenceUpdateRequest? = null

        override suspend fun getPlaces(
            authorization: String?,
            intent: String,
            latitude: Double,
            longitude: Double,
            radiusMeters: Int,
            limit: Int,
        ): ApiEnvelope<RecommendationPlacesPayload> {
            placeAuthorization = authorization
            placeIntent = intent
            placeLatitude = latitude
            placeLongitude = longitude
            placeRadiusMeters = radiusMeters
            placeLimit = limit
            return ApiEnvelope(
                success = true,
                data = RecommendationPlacesPayload(
                    requestId = "request-1",
                    modelVersion = "model-1",
                    dataStatus = "READY",
                    intent = intent,
                    items = places,
                ),
            )
        }

        override suspend fun recordInteraction(
            authorization: String,
            request: RecommendationInteractionRequest,
        ): ApiEnvelope<RecommendationInteractionResponse> {
            interactionCalls += 1
            interactionAuthorization = authorization
            interactionRequest = request
            return ApiEnvelope(success = true, data = RecommendationInteractionResponse())
        }

        override suspend fun getSavedItems(
            authorization: String,
        ): ApiEnvelope<List<SavedRecommendationDto>> = ApiEnvelope(success = true, data = emptyList())

        override suspend fun saveItem(
            authorization: String,
            request: SavedRecommendationRequest,
        ): ApiEnvelope<SavedRecommendationDto> {
            saveCalls += 1
            savedRequest = request
            return ApiEnvelope(
                success = true,
                data = SavedRecommendationDto(provider = request.provider, itemId = request.itemId),
            )
        }

        override suspend fun deleteSavedItem(
            authorization: String,
            provider: String,
            itemId: String,
        ) {
            deleteCalls += 1
            deletedItemId = itemId
        }

        override suspend fun getPreference(
            authorization: String,
        ): ApiEnvelope<RecommendationPreferenceDto> = ApiEnvelope(
            success = true,
            data = RecommendationPreferenceDto(),
        )

        override suspend fun updatePreference(
            authorization: String,
            request: RecommendationPreferenceUpdateRequest,
        ): ApiEnvelope<RecommendationPreferenceDto> {
            preferenceRequest = request
            return ApiEnvelope(
                success = true,
                data = RecommendationPreferenceDto(
                    personalizationEnabled = request.personalizationEnabled,
                ),
            )
        }
    }
}
