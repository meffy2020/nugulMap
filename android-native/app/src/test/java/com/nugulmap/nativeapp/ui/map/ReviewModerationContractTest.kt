package com.nugulmap.nativeapp.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewModerationContractTest {
    @Test
    fun `report reasons match the backend moderation contract`() {
        assertEquals(
            listOf("OFFENSIVE", "HARASSMENT", "SPAM", "PERSONAL_INFORMATION", "OTHER"),
            ReviewReportReason.entries.map(ReviewReportReason::apiValue),
        )
    }

    @Test
    fun `every report reason has a user facing label`() {
        ReviewReportReason.entries.forEach { reason ->
            check(reason.label.isNotBlank())
        }
    }

    @Test
    fun `legacy review without an author can still be reported but cannot be blocked`() {
        assertTrue(canReportReview(authorId = null, currentUserId = 7L))
        assertFalse(canBlockReviewAuthor(authorId = null, currentUserId = 7L))
    }

    @Test
    fun `another identified author can be reported and blocked`() {
        assertTrue(canReportReview(authorId = 8L, currentUserId = 7L))
        assertTrue(canBlockReviewAuthor(authorId = 8L, currentUserId = 7L))
    }

    @Test
    fun `own review cannot be reported or blocked`() {
        assertFalse(canReportReview(authorId = 7L, currentUserId = 7L))
        assertFalse(canBlockReviewAuthor(authorId = 7L, currentUserId = 7L))
    }
}
