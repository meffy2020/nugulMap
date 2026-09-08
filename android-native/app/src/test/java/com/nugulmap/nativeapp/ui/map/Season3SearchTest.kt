package com.nugulmap.nativeapp.ui.map

import com.nugulmap.nativeapp.data.dto.EventInsightPayload
import com.nugulmap.nativeapp.data.dto.HotplaceDto
import com.nugulmap.nativeapp.data.dto.HotplaceInsightPayload
import com.nugulmap.nativeapp.data.dto.MapInsightPayload
import com.nugulmap.nativeapp.data.dto.TrendEventDto
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Season3SearchTest {
    @Test
    fun `natural crowd question requests a citywide hotplace comparison`() {
        val request = parseSeason3Search("지금 어디가 붐비는지 알려줘")

        assertEquals(Season3SearchMode.Hotplaces, request?.mode)
        assertEquals("hot-now", request?.keyword)
        assertEquals("실시간 핫플", request?.displayKeyword)
        assertEquals(8, request?.hotplaceLimit)
        assertEquals(1, request?.eventLimit)
    }

    @Test
    fun `natural popup question keeps the place and removes question filler`() {
        val request = parseSeason3Search("성수 팝업 어디서 언제부터 언제까지 열려?")

        assertEquals(Season3SearchMode.Events, request?.mode)
        assertEquals("성수 팝업", request?.keyword)
        assertEquals(1, request?.hotplaceLimit)
        assertEquals(12, request?.eventLimit)
        assertFalse(request?.allowsNonPopupEvents ?: true)
    }

    @Test
    fun `explicit festival search may show non popup event kinds`() {
        val request = parseSeason3Search("여의도 축제")

        assertEquals(Season3SearchMode.Events, request?.mode)
        assertTrue(request?.allowsNonPopupEvents == true)
    }

    @Test
    fun `popup question normalizes Korean particles before calling the API`() {
        val request = parseSeason3Search("성수 팝업은 어디서 해?")

        assertEquals(Season3SearchMode.Events, request?.mode)
        assertEquals("성수 팝업", request?.keyword)
    }

    @Test
    fun `plain place search requests both crowd and event results`() {
        val request = parseSeason3Search("강남")

        assertEquals(Season3SearchMode.All, request?.mode)
        assertEquals("강남", request?.keyword)
    }

    @Test
    fun `blank natural search is ignored`() {
        assertNull(parseSeason3Search("   "))
    }

    @Test
    fun `successful hotplace search replaces only the requested comparison lane`() {
        val previousEvent = event("old-event")
        val current = MapUiState(
            hotplaceInsight = HotplaceInsightPayload(places = listOf(hotplace("old-hot"))),
            eventInsight = EventInsightPayload(events = listOf(previousEvent)),
            isInsightLoading = true,
        )
        val result = MapInsightPayload(
            hotplaces = HotplaceInsightPayload(places = listOf(hotplace("new-hot"))),
            events = EventInsightPayload(events = listOf(event("unrequested-event"))),
        )

        val updated = current.withSeason3SearchResult(
            request = Season3SearchRequest("hot-now", Season3SearchMode.Hotplaces),
            result = result,
        )

        assertEquals(listOf("new-hot"), updated.hotplaceInsight.places.map(HotplaceDto::id))
        assertEquals(listOf(previousEvent), updated.eventInsight.events)
        assertEquals(false, updated.isInsightLoading)
        assertEquals("‘실시간 핫플’ 결과 1개", updated.actionMessage)
    }

    @Test
    fun `failed refresh keeps the last crowd and popup results visible`() {
        val hotplaces = HotplaceInsightPayload(places = listOf(hotplace("kept-hot")))
        val events = EventInsightPayload(events = listOf(event("kept-event")))
        val current = MapUiState(
            hotplaceInsight = hotplaces,
            eventInsight = events,
            isInsightLoading = true,
        )

        val updated = current.withSeason3SearchFailure("네트워크 오류")

        assertEquals(hotplaces, updated.hotplaceInsight)
        assertEquals(events, updated.eventInsight)
        assertEquals(false, updated.isInsightLoading)
        assertEquals("네트워크 오류 · 이전 결과를 유지합니다.", updated.actionMessage)
    }

    @Test
    fun `event search collapses duplicate provider records for the same place and period`() {
        val duplicateA = event("api-event").copy(
            title = "성수 런타임 팝업",
            latitude = 37.5446,
            longitude = 127.0557,
            address = "서울 성동구 성수동",
            source = "SEOUL_CULTURE_API",
        )
        val duplicateB = duplicateA.copy(
            id = "crawler-event",
            source = "CRAWLED_POPUP_TREND",
        )
        val current = MapUiState(isInsightLoading = true)

        val updated = current.withSeason3SearchResult(
            request = Season3SearchRequest("성수 팝업", Season3SearchMode.Events),
            result = MapInsightPayload(events = EventInsightPayload(events = listOf(duplicateA, duplicateB))),
        )

        assertEquals(listOf("api-event"), updated.eventInsight.events.map(TrendEventDto::id))
        assertEquals("‘성수 팝업’ 결과 1개", updated.actionMessage)
    }

    @Test
    fun `event dedupe prefers a verified popup when an unverified provider arrives first`() {
        val verifiedPopup = event("verified-popup").copy(
            title = "성수 순서 보장 팝업",
            latitude = 37.5446,
            longitude = 127.0557,
            address = "서울 성동구 성수동",
        )
        val unverifiedPopup = verifiedPopup.copy(
            id = "unverified-popup",
            source = "CRAWLED_POPUP_TREND",
        )

        val updated = MapUiState(isInsightLoading = true).withSeason3SearchResult(
            request = Season3SearchRequest("성수 팝업", Season3SearchMode.Events),
            result = MapInsightPayload(
                events = EventInsightPayload(events = listOf(unverifiedPopup, verifiedPopup)),
            ),
        )

        assertEquals(listOf("verified-popup"), updated.eventInsight.events.map(TrendEventDto::id))
        assertEquals("‘성수 팝업’ 결과 1개", updated.actionMessage)
    }

    @Test
    fun `broad event dedupe prefers a verified popup over an earlier non popup duplicate`() {
        val verifiedPopup = event("verified-popup").copy(
            title = "성수 공급자 중복 팝업",
            address = "서울 성동구 성수동",
        )
        val genericProviderEvent = verifiedPopup.copy(
            id = "generic-provider-event",
            kind = "festival",
            source = "KTO_TOUR_API",
        )

        val updated = MapUiState(isInsightLoading = true).withSeason3SearchResult(
            request = Season3SearchRequest(
                keyword = "성수 행사",
                mode = Season3SearchMode.Events,
                allowsNonPopupEvents = true,
            ),
            result = MapInsightPayload(
                events = EventInsightPayload(events = listOf(genericProviderEvent, verifiedPopup)),
            ),
        )

        assertEquals(listOf("verified-popup"), updated.eventInsight.events.map(TrendEventDto::id))
    }

    @Test
    fun `popup layer always verifies popup rows and broad search adds only non popup events`() {
        val events = listOf(
            event("popup").copy(
                title = "성수 브랜드 팝업",
                kind = "popup",
                latitude = 37.5446,
                longitude = 127.0557,
                address = "서울 성동구 성수동",
            ),
            event("gangnam-popup").copy(
                title = "강남 브랜드 팝업",
                kind = "popup",
                latitude = 37.503822,
                longitude = 127.00453,
                address = "서울 서초구 신반포로",
            ),
            event("eungbong-popup").copy(
                title = "응봉 브랜드 팝업",
                kind = "popup",
                latitude = 37.5500,
                longitude = 127.0340,
                address = "서울 성동구 응봉동",
            ),
            event("festival").copy(kind = "festival"),
            event("generic").copy(kind = "event"),
            event("missing-link").copy(detailUrl = null, sourceContentId = "provider-id"),
            event("missing-date").copy(startDate = null),
            event("reversed-date").copy(startDate = "2026-08-22", endDate = "2026-08-21"),
            event("local-link").copy(detailUrl = "http://127.0.0.1/private"),
            event("credentialed-link").copy(detailUrl = "https://user:secret@example.com/private"),
        )

        assertEquals(
            listOf("popup"),
            filterEventsForPopupLayer(events, allowsNonPopupEvents = false).map(TrendEventDto::id),
        )
        assertEquals(
            listOf(
                "popup",
                "festival",
                "generic",
            ),
            filterEventsForPopupLayer(events, allowsNonPopupEvents = true).map(TrendEventDto::id),
        )
    }

    @Test
    fun `popup tab uses the fixed verified Seongsu request`() {
        val request = seongsuPopupSearchRequest()

        assertEquals("성수 팝업", request.keyword)
        assertEquals(Season3SearchMode.Events, request.mode)
        assertFalse(request.allowsNonPopupEvents)
    }

    @Test
    fun `verified Seongsu popup fails closed for expiry provenance and tobacco metadata`() {
        val expired = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1).toString()
        val valid = event("verified")

        assertTrue(valid.isVerifiedSeongsuPopup)
        assertFalse(valid.copy(startDate = expired, endDate = expired).isVerifiedSeongsuPopup)
        assertFalse(valid.copy(source = null).isVerifiedSeongsuPopup)
        assertFalse(valid.copy(source = "CRAWLED_POPUP_TREND").isVerifiedSeongsuPopup)
        assertFalse(valid.copy(attribution = null).isVerifiedSeongsuPopup)
        assertFalse(valid.copy(attribution = "아이코스 제공").isVerifiedSeongsuPopup)
        assertFalse(valid.copy(sourceUrl = null).isVerifiedSeongsuPopup)
        assertFalse(valid.copy(sourceUrl = "http://2130706433/private").isVerifiedSeongsuPopup)
        assertFalse(valid.copy(license = null).isVerifiedSeongsuPopup)
        assertFalse(valid.copy(license = "출처 불명").isVerifiedSeongsuPopup)
        assertFalse(valid.copy(licenseUrl = "http://[::ffff:127.0.0.1]/private").isVerifiedSeongsuPopup)
        assertFalse(valid.copy(detailUrl = "https://example.com/vape/popup").isVerifiedSeongsuPopup)
    }

    private fun hotplace(id: String) = HotplaceDto(
        id = id,
        name = id,
        latitude = 37.5,
        longitude = 127.0,
    )

    private fun event(id: String) = TrendEventDto(
        id = id,
        title = "성수 $id 팝업",
        kind = "popup",
        startDate = LocalDate.now(ZoneId.of("Asia/Seoul")).toString(),
        endDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(8).toString(),
        latitude = 37.5446,
        longitude = 127.0557,
        address = "서울 성동구 성수동",
        detailUrl = "https://example.com/popups/$id",
        source = "SEOUL_CULTURE_API",
        attribution = "서울특별시 문화행사 정보",
        sourceUrl = "https://data.seoul.go.kr/dataList/OA-15486/S/1/datasetView.do",
        license = "공공누리 제1유형",
        licenseUrl = "https://www.kogl.or.kr/info/licenseType1.do",
    )
}
