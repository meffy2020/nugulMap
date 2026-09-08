package com.nugulmap.nativeapp.data.repository

import com.nugulmap.nativeapp.data.api.NugulApiService
import com.nugulmap.nativeapp.data.dto.ApiEnvelope
import com.nugulmap.nativeapp.data.dto.ZoneBoundsPayload
import com.nugulmap.nativeapp.data.dto.ZoneDto
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import retrofit2.http.GET

class SmokingZoneSearchTest {
    @Test fun `guest search uses public smoking endpoint without coordinates or credentials`() = runBlocking {
        val zone = ZoneDto(1, latitude = 37.5, longitude = 127.0)
        val service = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(NugulApiService::class.java)) { _, method, args ->
            assertEquals("searchZones", method.name)
            assertEquals("api/zones/search", requireNotNull(method.getAnnotation(GET::class.java)).value)
            assertEquals("성수", args[0])
            assertEquals(100, args[1])
            ApiEnvelope(success = true, data = ZoneBoundsPayload(listOf(zone)))
        } as NugulApiService
        assertEquals(listOf(zone), ZoneRepository(service).searchZones(" 성수 "))
    }

    @Test fun `failed search is not interpreted as an empty map`() = runBlocking {
        val service = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(NugulApiService::class.java)) { _, _, _ ->
            ApiEnvelope<ZoneBoundsPayload>(success = false, message = "Unavailable")
        } as NugulApiService
        assertFailsWith<IllegalStateException> { ZoneRepository(service).searchZones("성수") }
        Unit
    }
}
