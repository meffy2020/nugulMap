package com.nugulmap.nativeapp.ui.map

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CurrentLocationProviderTest {
    @Test
    fun `next provider is tried when the first provider fails`() = runBlocking {
        val requestedProviders = mutableListOf<String>()

        val location = firstSuccessfulLocationProvider(listOf("network", "gps")) { provider ->
            requestedProviders += provider
            if (provider == "network") {
                error("network fix unavailable")
            }
            CurrentMapLocation(latitude = 37.5665, longitude = 126.9780)
        }

        assertEquals(listOf("network", "gps"), requestedProviders)
        assertEquals(CurrentMapLocation(37.5665, 126.9780), location)
    }

    @Test
    fun `all provider failures become one user facing location error`() = runBlocking {
        val failure = assertFailsWith<IllegalStateException> {
            firstSuccessfulLocationProvider(listOf("network", "gps")) { provider ->
                error("$provider unavailable")
            }
        }

        assertEquals("현재 위치를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.", failure.message)
        assertIs<IllegalStateException>(failure.cause)
        assertEquals("gps unavailable", failure.cause?.message)
    }
}
