package com.nugulmap.nativeapp.ui.map

import com.nugulmap.nativeapp.data.dto.ZoneReviewDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UgcPolicyGateTest {
    @Test
    fun `review submission requires login target content policy acceptance and idle state`() {
        assertTrue(
            canSubmitUgc(
                isSignedIn = true,
                hasTarget = true,
                content = "깨끗하게 관리되고 있어요.",
                policyAccepted = true,
                isBusy = false,
            ),
        )
        assertFalse(canSubmitUgc(true, true, "리뷰", false, false))
        assertFalse(canSubmitUgc(false, true, "리뷰", true, false))
        assertFalse(canSubmitUgc(true, false, "리뷰", true, false))
        assertFalse(canSubmitUgc(true, true, "   ", true, false))
        assertFalse(canSubmitUgc(true, true, "리뷰", true, true))
    }

    @Test
    fun `public review author never falls back to email`() {
        assertEquals(
            "익명",
            ZoneReviewDto(
                id = 1,
                zoneId = 2,
                authorNickname = null,
                content = "리뷰",
            ).displayAuthor,
        )
    }

    @Test
    fun `public zone submission requires policy acceptance`() {
        assertFalse(canSubmitZoneUgc(policyAccepted = false, isBusy = false))
        assertFalse(canSubmitZoneUgc(policyAccepted = true, isBusy = true))
        assertTrue(canSubmitZoneUgc(policyAccepted = true, isBusy = false))
    }
}
