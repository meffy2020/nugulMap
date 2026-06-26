package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.dto.EventInsightResponse;
import com.neogulmap.neogul_map.dto.HotplaceResponse;
import com.neogulmap.neogul_map.dto.InsightStatusResponse;
import com.neogulmap.neogul_map.service.EventInsightService;
import com.neogulmap.neogul_map.service.HotplaceService;
import com.neogulmap.neogul_map.service.InsightStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InsightControllerTest {

    private final HotplaceService hotplaceService = mock(HotplaceService.class);
    private final EventInsightService eventInsightService = mock(EventInsightService.class);
    private final InsightStatusService insightStatusService = mock(InsightStatusService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new InsightController(hotplaceService, eventInsightService, insightStatusService))
            .build();

    @Test
    void getMapInsightsCombinesHotplacesEventsAndStatusForViewport() throws Exception {
        when(hotplaceService.getHotplaces(eq("요즘 핫한 팝업 행사"), eq(6), any(), any(), any(), any()))
                .thenReturn(new HotplaceResponse(
                        List.of(new HotplaceResponse.HotplaceItem(
                                "seongsu-cafe-street",
                                "성수동 카페거리",
                                "hotplace",
                                "약간 붐빔",
                                "방문자가 늘고 있습니다.",
                                7000,
                                9000,
                                37.5446,
                                127.0557,
                                "서울 성동구 성수동",
                                "TELECOM_CROWD",
                                "carrier-seongsu",
                                "2026-06-18T12:00:00Z"
                        )),
                        "LIVE_OR_PARTIAL",
                        Instant.parse("2026-06-18T12:00:00Z"),
                        List.of("통신사 장소 혼잡도")
                ));
        when(eventInsightService.getEvents(eq("요즘 핫한 팝업 행사"), eq(7), any(), any(), any(), any()))
                .thenReturn(new EventInsightResponse(
                        List.of(new EventInsightResponse.EventInsightItem(
                                "seoul-culture-popup",
                                "성수 브랜드 팝업",
                                "popup",
                                "2026-07-01-2026-07-14",
                                "2026-07-01",
                                "2026-07-14",
                                37.5446,
                                127.0557,
                                "서울 성동구 성수동",
                                null,
                                "SEOUL_CULTURE_API",
                                "https://culture.seoul.go.kr/event"
                        )),
                        "LIVE_OR_PARTIAL",
                        Instant.parse("2026-06-18T12:01:00Z"),
                        List.of("서울 팝업·핫플 후보 레지스트리")
                ));
        when(insightStatusService.getStatus())
                .thenReturn(new InsightStatusResponse(
                        false,
                        true,
                        true,
                        false,
                        true,
                        "LIVE_READY",
                        "LIVE_OR_CRAWLED_READY",
                        InsightStatusResponse.ProviderStatus.notConfigured("SEOUL_CITYDATA_API_KEY is not configured"),
                        InsightStatusResponse.ProviderStatus.ok(
                                Instant.parse("2026-06-18T12:00:00Z"),
                                "통신사 혼잡도 조회 성공"
                        ),
                        InsightStatusResponse.ProviderStatus.notConfigured("KTO_TOUR_API_KEY is not configured"),
                        InsightStatusResponse.ProviderStatus.ok(
                                Instant.parse("2026-06-18T12:01:00Z"),
                                "서울 문화행사 API 조회 성공"
                        ),
                        new InsightStatusResponse.PopupTrendStatus(
                                true,
                                true,
                                8,
                                Instant.parse("2026-06-18T00:00:00Z"),
                                "OK",
                                "Popup trends file is readable"
                        ),
                        Instant.parse("2026-06-18T12:02:00Z")
                ));

        mockMvc.perform(get("/insights/map")
                        .param("keyword", "요즘 핫한 팝업 행사")
                        .param("hotplaceLimit", "6")
                        .param("eventLimit", "7")
                        .param("minLat", "37.48")
                        .param("maxLat", "37.60")
                        .param("minLng", "126.88")
                        .param("maxLng", "127.12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.hotplaces.dataFreshness").value("LIVE_OR_PARTIAL"))
                .andExpect(jsonPath("$.data.hotplaces.places[0].source").value("TELECOM_CROWD"))
                .andExpect(jsonPath("$.data.events.dataFreshness").value("LIVE_OR_PARTIAL"))
                .andExpect(jsonPath("$.data.events.events[0].source").value("SEOUL_CULTURE_API"))
                .andExpect(jsonPath("$.data.status.hotplaceMode").value("LIVE_READY"))
                .andExpect(jsonPath("$.data.status.seoulCultureApiKeyConfigured").value(true))
                .andExpect(jsonPath("$.data.status.seoulCultureApi.qualityStatus").value("OK"))
                .andExpect(jsonPath("$.data.status.popupTrends.recordCount").value(8));

        verify(hotplaceService).getHotplaces(eq("요즘 핫한 팝업 행사"), eq(6), eq(37.48), eq(37.60), eq(126.88), eq(127.12));
        verify(eventInsightService).getEvents(eq("요즘 핫한 팝업 행사"), eq(7), eq(37.48), eq(37.60), eq(126.88), eq(127.12));
        verify(insightStatusService).getStatus();
    }

}
