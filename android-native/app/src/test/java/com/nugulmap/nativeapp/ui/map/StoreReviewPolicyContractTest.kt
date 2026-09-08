package com.nugulmap.nativeapp.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals

class StoreReviewPolicyContractTest {
    @Test
    fun `settings connect to the public social login management route`() {
        assertEquals("https://nugulmap.com/connected-accounts", LOGIN_CONNECTIONS_URL)
    }
}
