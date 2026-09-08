package com.nugulmap.nativeapp.ui.map

import com.google.gson.Gson
import com.nugulmap.nativeapp.data.dto.EventInsightPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrendEventLinkTest {
    @Test
    fun `event payload decodes attribution as a friendly source label`() {
        val payload = Gson().fromJson(
            """
            {
              "events": [
                {
                  "id": "popup-1",
                  "title": "성수 여름 팝업",
                  "latitude": 37.543,
                  "longitude": 127.052,
                  "source": "SEOUL_CULTURE_API",
                  "attribution": "서울특별시 문화행사 정보",
                  "sourceUrl": "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
                  "license": "공공누리 제1유형",
                  "licenseUrl": "https://www.kogl.or.kr/info/licenseType1.do"
                }
              ]
            }
            """.trimIndent(),
            EventInsightPayload::class.java,
        )

        val event = payload.events.single()
        assertEquals("서울특별시 문화행사 정보", event.attribution)
        assertEquals("서울특별시 문화행사 정보", event.sourceLabel)
        assertEquals("https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do", event.sourceUrl)
        assertEquals("공공누리 제1유형", event.license)
        assertEquals("https://www.kogl.or.kr/info/licenseType1.do", event.licenseUrl)
    }

    @Test
    fun `detail URL is preferred over a legacy source URL`() {
        assertEquals(
            "https://example.com/events/current",
            safeEventSourceUrl(
                detailUrl = "https://example.com/events/current",
                sourceContentId = "https://legacy.example.com/events/old",
            ),
        )
    }

    @Test
    fun `legacy source URL remains available during contract migration`() {
        assertEquals(
            "https://legacy.example.com/events/old",
            safeEventSourceUrl(
                detailUrl = null,
                sourceContentId = "https://legacy.example.com/events/old",
            ),
        )
    }

    @Test
    fun `non web and credentialed URLs are rejected`() {
        assertNull(safeEventSourceUrl("javascript:alert(1)", null))
        assertNull(safeEventSourceUrl("https://user:secret@example.com/event", null))
        assertNull(safeEventSourceUrl(null, "event-content-id-123"))
    }

    @Test
    fun `public cleartext event URLs are rejected`() {
        assertNull(safeEventSourceUrl("http://example.com/events/current", null))
        assertNull(safeEventSourceUrl(null, "http://legacy.example.com/events/old"))
    }

    @Test
    fun `event detail exposes user relevant dates and friendly attribution`() {
        assertEquals(
            "시작 2026-07-10 · 종료 2026-07-20 · 출처: 서울특별시 문화행사 정보 · 이용조건: 공공누리 제1유형",
            formatEventDetail(
                startDate = "2026-07-10",
                endDate = "2026-07-20",
                attribution = "서울특별시 문화행사 정보",
                license = "공공누리 제1유형",
            ),
        )
    }

    @Test
    fun `popup list label shows its actual start and end dates`() {
        assertEquals(
            "팝업 · 2026-07-10 ~ 2026-07-20",
            formatEventListLabel(
                kind = "popup",
                period = "기간 정보",
                startDate = "2026-07-10",
                endDate = "2026-07-20",
            ),
        )
        assertEquals(
            "축제 · 2026-07-10",
            formatEventListLabel(
                kind = "festival",
                period = null,
                startDate = "2026-07-10",
                endDate = "2026-07-10",
            ),
        )
    }

    @Test
    fun `local source URLs are not exposed as original links`() {
        assertNull(safeEventSourceUrl("http://localhost:18080/admin", null))
        assertNull(safeEventSourceUrl("http://127.0.0.1/private", null))
        assertNull(safeEventSourceUrl("http://10.0.2.2/private", null))
    }

    @Test
    fun `obfuscated IPv4 and IPv6 local URLs are not exposed`() {
        listOf(
            "http://2130706433/private",
            "http://167772161/private",
            "http://0177.0.0.1/private",
            "http://0x7f.0x0.0x0.0x1/private",
            "http://[::1]/private",
            "http://[::ffff:127.0.0.1]/private",
        ).forEach { url ->
            assertNull(safeEventSourceUrl(url, null), url)
        }
    }
}
