package com.nugulmap.nativeapp.ui.map

import com.nugulmap.nativeapp.data.api.NugulApiService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import retrofit2.http.POST

class ZoneReportContractTest {
    @Test
    fun `zone report endpoint matches the backend contract`() {
        val method = NugulApiService::class.java.methods.single { it.name == "reportZone" }

        assertEquals(
            "api/zones/{zoneId}/reports",
            requireNotNull(method.getAnnotation(POST::class.java)).value,
        )
    }

    @Test
    fun `zone report reasons match the backend moderation contract`() {
        assertEquals(
            listOf("INACCURATE", "DUPLICATE", "CLOSED", "OFFENSIVE", "PERSONAL_INFORMATION", "OTHER"),
            ZoneReportReason.entries.map(ZoneReportReason::apiValue),
        )
    }

    @Test
    fun `zone report errors stay actionable without technical details`() {
        assertEquals("장소 신고는 로그인이 필요합니다.", zoneReportFailureMessage(401))
        assertEquals("장소 신고는 로그인이 필요합니다.", zoneReportFailureMessage(403))
        assertEquals("이미 신고한 장소입니다.", zoneReportFailureMessage(409))
        assertEquals(
            "장소 신고에 실패했습니다. 잠시 후 다시 시도해 주세요.",
            zoneReportFailureMessage(500),
        )
    }

    @Test
    fun `zone report entry has a screen reader label`() {
        assertTrue(ZONE_REPORT_ACCESSIBILITY_LABEL.isNotBlank())
    }

    @Test
    fun `latest zone report status can take priority over an older login status`() {
        assertEquals(
            "장소 신고가 접수되었습니다.",
            firstStatusMessage("장소 신고가 접수되었습니다.", "로그인 완료"),
        )
    }
}
