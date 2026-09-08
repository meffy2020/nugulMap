package com.nugulmap.nativeapp.data.repository

import com.nugulmap.nativeapp.core.auth.AuthSessionStore
import com.nugulmap.nativeapp.data.dto.ApiEnvelope
import com.nugulmap.nativeapp.data.dto.LocationLetterCreateRequest
import com.nugulmap.nativeapp.data.dto.LocationLetterDto
import com.nugulmap.nativeapp.data.dto.LocationLetterLikeState
import com.nugulmap.nativeapp.data.dto.LocationLetterNearbyPayload
import com.nugulmap.nativeapp.data.dto.LocationLetterPayload
import com.nugulmap.nativeapp.data.dto.LocationLetterReportPayload
import com.nugulmap.nativeapp.data.dto.LocationLetterReportDto
import com.nugulmap.nativeapp.data.dto.RefreshTokenResponse
import com.nugulmap.nativeapp.data.dto.ReviewReportRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LocationLetterRepositoryTest {
    @Test
    fun `guest can read nearby letters without an authorization header`() = runBlocking {
        val api = FakeLocationLetterApi()
        val repository = LocationLetterRepository(api)

        val payload = repository.loadNearby(37.5665, 126.9780)

        assertEquals(null, api.readAuthorization)
        assertEquals(37.5665, api.readLatitude)
        assertEquals(126.9780, api.readLongitude)
        assertEquals("잠시 쉬어가도 괜찮아요.", payload.letters.single().content)
    }

    @Test
    fun `signed in user leaves a trimmed letter at the current coordinates`() = runBlocking {
        val api = FakeLocationLetterApi()
        val repository = LocationLetterRepository(api, authenticatedExecutor())

        val letter = repository.create(37.5665, 126.9780, "  오늘도 잘 지나가길  ")

        assertEquals("Bearer access-token", api.writeAuthorization)
        assertEquals("오늘도 잘 지나가길", api.createRequest?.content)
        assertEquals(37.5665, api.createRequest?.latitude)
        assertEquals(126.9780, api.createRequest?.longitude)
        assertEquals(true, letter.mine)
    }

    @Test
    fun `letter length is capped before any network write`() = runBlocking {
        val api = FakeLocationLetterApi()
        val repository = LocationLetterRepository(api, authenticatedExecutor())

        assertFailsWith<IllegalArgumentException> {
            repository.create(37.5665, 126.9780, "가".repeat(MAX_LETTER_CONTENT_LENGTH + 1))
        }
        assertEquals(null, api.createRequest)
    }

    @Test
    fun `delete and report use authenticated letter endpoints`() = runBlocking {
        val api = FakeLocationLetterApi()
        val repository = LocationLetterRepository(api, authenticatedExecutor())

        repository.delete(41L)
        repository.report(42L, "SPAM")

        assertEquals("Bearer access-token", api.deleteAuthorization)
        assertEquals(41L, api.deletedLetterId)
        assertEquals("Bearer access-token", api.reportAuthorization)
        assertEquals(42L, api.reportedLetterId)
        assertEquals("SPAM", api.reportRequest?.reason)
    }

    @Test
    fun `like and unlike return the server count and selected state`() = runBlocking {
        val api = FakeLocationLetterApi()
        val repository = LocationLetterRepository(api, authenticatedExecutor())

        val liked = repository.setLiked(letterId = 41L, liked = true)
        val unliked = repository.setLiked(letterId = 41L, liked = false)

        assertEquals(listOf(true, false), api.likeRequests)
        assertEquals(13L, liked.likeCount)
        assertEquals(true, liked.likedByMe)
        assertEquals(12L, unliked.likeCount)
        assertEquals(false, unliked.likedByMe)
    }

    private fun authenticatedExecutor() = AuthenticatedRequestExecutor(
        tokenStore = FakeAuthSessionStore(),
        refreshSession = {
            RefreshTokenResponse(success = true, accessToken = "unused", refreshToken = "unused")
        },
    )

    private class FakeAuthSessionStore : AuthSessionStore {
        override fun save(accessToken: String, refreshToken: String?) = Unit
        override fun accessToken(): String = "access-token"
        override fun refreshToken(): String = "refresh-token"
        override fun clear() = Unit
    }

    private class FakeLocationLetterApi : LocationLetterApi {
        var readAuthorization: String? = null
        var readLatitude: Double? = null
        var readLongitude: Double? = null
        var writeAuthorization: String? = null
        var createRequest: LocationLetterCreateRequest? = null
        var deleteAuthorization: String? = null
        var deletedLetterId: Long? = null
        var reportAuthorization: String? = null
        var reportedLetterId: Long? = null
        var reportRequest: ReviewReportRequest? = null
        val likeRequests = mutableListOf<Boolean>()

        override suspend fun getNearby(
            authorization: String?,
            latitude: Double,
            longitude: Double,
            limit: Int,
        ): ApiEnvelope<LocationLetterNearbyPayload> {
            readAuthorization = authorization
            readLatitude = latitude
            readLongitude = longitude
            return ApiEnvelope(
                success = true,
                data = LocationLetterNearbyPayload(
                    letters = listOf(
                        LocationLetterDto(
                            id = 1L,
                            authorLabel = "하늘산책",
                            content = "잠시 쉬어가도 괜찮아요.",
                            likeCount = 12L,
                        ),
                    ),
                ),
            )
        }

        override suspend fun create(
            authorization: String,
            request: LocationLetterCreateRequest,
        ): ApiEnvelope<LocationLetterPayload> {
            writeAuthorization = authorization
            createRequest = request
            return ApiEnvelope(
                success = true,
                data = LocationLetterPayload(
                    letter = LocationLetterDto(
                        id = 2L,
                        authorLabel = "내가 남긴 편지",
                        content = request.content,
                        mine = true,
                    ),
                ),
            )
        }

        override suspend fun delete(authorization: String, letterId: Long) {
            deleteAuthorization = authorization
            deletedLetterId = letterId
        }

        override suspend fun report(
            authorization: String,
            letterId: Long,
            request: ReviewReportRequest,
        ): ApiEnvelope<LocationLetterReportPayload> {
            reportAuthorization = authorization
            reportedLetterId = letterId
            reportRequest = request
            return ApiEnvelope(
                success = true,
                data = LocationLetterReportPayload(
                    report = LocationLetterReportDto(
                        id = 3L,
                        letterId = letterId,
                        reason = request.reason,
                        status = "PENDING",
                    ),
                ),
            )
        }

        override suspend fun setLiked(
            authorization: String,
            letterId: Long,
            liked: Boolean,
        ): ApiEnvelope<LocationLetterLikeState> {
            likeRequests += liked
            return ApiEnvelope(
                success = true,
                data = LocationLetterLikeState(
                    letterId = letterId,
                    likeCount = if (liked) 13L else 12L,
                    likedByMe = liked,
                ),
            )
        }
    }
}
