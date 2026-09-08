package com.nugulmap.nativeapp.ui.map

import com.nugulmap.nativeapp.data.dto.UserProfileDto
import com.nugulmap.nativeapp.data.dto.ZoneDto
import com.nugulmap.nativeapp.data.repository.AuthSessionExpiredException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthenticationStateReducerTest {
    @Test
    fun `expired session atomically clears authenticated UI state and loading flags`() {
        val current = MapUiState(
            isSignedIn = true,
            currentUser = UserProfileDto(id = 7, email = "reviewer@example.com"),
            myZones = listOf(ZoneDto(id = 9, latitude = 37.5, longitude = 127.0)),
            isAuthLoading = true,
            isActionLoading = true,
            isReviewLoading = true,
            isReviewSubmitting = true,
            isReviewModerating = true,
            reviewModerationMessage = "처리 중",
        )

        var fallbackCalled = false
        val updated = current.reduceAuthenticatedFailure(AuthSessionExpiredException()) {
            fallbackCalled = true
            it
        }

        assertFalse(fallbackCalled)
        assertFalse(updated.isSignedIn)
        assertNull(updated.currentUser)
        assertTrue(updated.myZones.isEmpty())
        assertFalse(updated.isAuthLoading)
        assertFalse(updated.isActionLoading)
        assertFalse(updated.isReviewLoading)
        assertFalse(updated.isReviewSubmitting)
        assertFalse(updated.isReviewModerating)
        assertNull(updated.reviewModerationMessage)
        assertEquals("로그인이 만료되었습니다. 다시 로그인해 주세요.", updated.authMessage)
        assertEquals("로그인이 만료되었습니다. 다시 로그인해 주세요.", updated.reviewErrorMessage)
    }

    @Test
    fun `non authentication failure keeps the caller specific reduction`() {
        val current = MapUiState(isSignedIn = true, isActionLoading = true)

        val updated = current.reduceAuthenticatedFailure(IllegalStateException("offline")) {
            it.copy(isActionLoading = false, actionMessage = "다시 시도해 주세요.")
        }

        assertTrue(updated.isSignedIn)
        assertFalse(updated.isActionLoading)
        assertEquals("다시 시도해 주세요.", updated.actionMessage)
    }
}
