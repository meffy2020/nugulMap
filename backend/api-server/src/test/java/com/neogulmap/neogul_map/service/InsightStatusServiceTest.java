package com.neogulmap.neogul_map.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neogulmap.neogul_map.dto.InsightStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InsightStatusServiceTest {

    private final HotplaceService hotplaceService = mock(HotplaceService.class);
    private final EventInsightService eventInsightService = mock(EventInsightService.class);

    @BeforeEach
    void setUpProviderDefaults() {
        when(eventInsightService.getSeoulCultureApiProviderStatus())
                .thenReturn(InsightStatusResponse.ProviderStatus.notConfigured("SEOUL_CULTURE_API_KEY is not configured"));
    }

    @Test
    void getStatusReportsConfiguredKeysAndPopupTrendFile(@TempDir Path tempDir) throws Exception {
        Path trendFile = tempDir.resolve("popup-trends.json");
        Files.writeString(trendFile, """
                [
                  {
                    "id": "popup-seongsu",
                    "title": "성수 팝업",
                    "kind": "popup",
                    "latitude": 37.5446,
                    "longitude": 127.0557,
                    "source": "CRAWLED_POPUP_TREND",
                    "collectedAt": "2026-06-18T00:00:00Z"
                  }
                ]
                """);
        when(hotplaceService.getSeoulCityDataProviderStatus())
                .thenReturn(InsightStatusResponse.ProviderStatus.ok(
                        Instant.parse("2026-06-18T00:05:00Z"),
                        "서울 실시간 도시데이터 조회 성공"
                ));
        when(hotplaceService.getTelecomCrowdProviderStatus())
                .thenReturn(InsightStatusResponse.ProviderStatus.notConfigured("TELECOM_CROWD_API_KEY is not configured"));
        when(eventInsightService.getTourApiProviderStatus())
                .thenReturn(InsightStatusResponse.ProviderStatus.ok(
                        Instant.parse("2026-06-18T00:10:00Z"),
                        "한국관광공사 행사정보 조회 성공"
                ));
        InsightStatusService service = new InsightStatusService(new ObjectMapper(), hotplaceService, eventInsightService);
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "seoul-key");
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "");
        ReflectionTestUtils.setField(service, "telecomCrowdUrlTemplate", "");
        ReflectionTestUtils.setField(service, "tourApiKey", "tour-key");
        ReflectionTestUtils.setField(service, "seoulCultureApiKey", "culture-key");
        ReflectionTestUtils.setField(service, "popupTrendsFile", trendFile.toString());

        InsightStatusResponse response = service.getStatus();

        assertThat(response.seoulCityDataKeyConfigured()).isTrue();
        assertThat(response.telecomCrowdKeyConfigured()).isFalse();
        assertThat(response.telecomCrowdUrlTemplateConfigured()).isFalse();
        assertThat(response.ktoTourApiKeyConfigured()).isTrue();
        assertThat(response.seoulCultureApiKeyConfigured()).isTrue();
        assertThat(response.hotplaceMode()).isEqualTo("LIVE_READY");
        assertThat(response.eventMode()).isEqualTo("LIVE_OR_CRAWLED_READY");
        assertThat(response.seoulCityData().qualityStatus()).isEqualTo("OK");
        assertThat(response.ktoTourApi().qualityStatus()).isEqualTo("OK");
        assertThat(response.seoulCultureApi().qualityStatus()).isEqualTo("NOT_CONFIGURED");
        assertThat(response.popupTrends().fileConfigured()).isTrue();
        assertThat(response.popupTrends().fileExists()).isTrue();
        assertThat(response.popupTrends().recordCount()).isEqualTo(1);
        assertThat(response.popupTrends().qualityStatus()).isEqualTo("OK");
        assertThat(response.popupTrends().latestCollectedAt()).isNotNull();
    }

    @Test
    void getStatusReportsMissingPopupTrendConfig() {
        when(hotplaceService.getSeoulCityDataProviderStatus())
                .thenReturn(InsightStatusResponse.ProviderStatus.notConfigured("SEOUL_CITYDATA_API_KEY is not configured"));
        when(hotplaceService.getTelecomCrowdProviderStatus())
                .thenReturn(InsightStatusResponse.ProviderStatus.notConfigured("TELECOM_CROWD_API_KEY is not configured"));
        when(eventInsightService.getTourApiProviderStatus())
                .thenReturn(InsightStatusResponse.ProviderStatus.notConfigured("KTO_TOUR_API_KEY is not configured"));
        InsightStatusService service = new InsightStatusService(new ObjectMapper(), hotplaceService, eventInsightService);
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "");
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "");
        ReflectionTestUtils.setField(service, "telecomCrowdUrlTemplate", "");
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "seoulCultureApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", "");

        InsightStatusResponse response = service.getStatus();

        assertThat(response.hotplaceMode()).isEqualTo("STATIC_FALLBACK");
        assertThat(response.eventMode()).isEqualTo("STATIC_FALLBACK");
        assertThat(response.telecomCrowdKeyConfigured()).isFalse();
        assertThat(response.telecomCrowdUrlTemplateConfigured()).isFalse();
        assertThat(response.seoulCultureApiKeyConfigured()).isFalse();
        assertThat(response.seoulCityData().qualityStatus()).isEqualTo("NOT_CONFIGURED");
        assertThat(response.ktoTourApi().qualityStatus()).isEqualTo("NOT_CONFIGURED");
        assertThat(response.seoulCultureApi().qualityStatus()).isEqualTo("NOT_CONFIGURED");
        assertThat(response.popupTrends().fileConfigured()).isFalse();
        assertThat(response.popupTrends().qualityStatus()).isEqualTo("NOT_CONFIGURED");
    }

    @Test
    void getStatusReportsInvalidPopupRecords(@TempDir Path tempDir) throws Exception {
        Path trendFile = tempDir.resolve("popup-trends.json");
        Files.writeString(trendFile, """
                [
                  {
                    "id": "broken-popup",
                    "kind": "popup",
                    "latitude": "not-a-number",
                    "longitude": 127.0557
                  }
                ]
                """);
        when(hotplaceService.getSeoulCityDataProviderStatus())
                .thenReturn(InsightStatusResponse.ProviderStatus.notConfigured("SEOUL_CITYDATA_API_KEY is not configured"));
        when(hotplaceService.getTelecomCrowdProviderStatus())
                .thenReturn(InsightStatusResponse.ProviderStatus.notConfigured("TELECOM_CROWD_API_KEY is not configured"));
        when(eventInsightService.getTourApiProviderStatus())
                .thenReturn(InsightStatusResponse.ProviderStatus.notConfigured("KTO_TOUR_API_KEY is not configured"));
        InsightStatusService service = new InsightStatusService(new ObjectMapper(), hotplaceService, eventInsightService);
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "");
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "");
        ReflectionTestUtils.setField(service, "telecomCrowdUrlTemplate", "");
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", trendFile.toString());

        InsightStatusResponse response = service.getStatus();

        assertThat(response.eventMode()).isEqualTo("STATIC_FALLBACK");
        assertThat(response.popupTrends().recordCount()).isEqualTo(1);
        assertThat(response.popupTrends().qualityStatus()).isEqualTo("INVALID_RECORDS");
        assertThat(response.popupTrends().detail()).contains("missing required fields");
    }

    @Test
    void getStatusDoesNotTreatConfiguredButUnverifiedKeysAsLiveReady() {
        when(hotplaceService.getSeoulCityDataProviderStatus())
                .thenReturn(InsightStatusResponse.ProviderStatus.configuredUnverified("configured but not checked"));
        when(hotplaceService.getTelecomCrowdProviderStatus())
                .thenReturn(InsightStatusResponse.ProviderStatus.notConfigured("TELECOM_CROWD_API_KEY is not configured"));
        when(eventInsightService.getTourApiProviderStatus())
                .thenReturn(InsightStatusResponse.ProviderStatus.error(null, Instant.parse("2026-06-18T00:00:00Z"), "provider failed"));
        InsightStatusService service = new InsightStatusService(new ObjectMapper(), hotplaceService, eventInsightService);
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "seoul-key");
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "");
        ReflectionTestUtils.setField(service, "telecomCrowdUrlTemplate", "");
        ReflectionTestUtils.setField(service, "tourApiKey", "tour-key");
        ReflectionTestUtils.setField(service, "popupTrendsFile", "");

        InsightStatusResponse response = service.getStatus();

        assertThat(response.hotplaceMode()).isEqualTo("LIVE_CONFIGURED_UNVERIFIED");
        assertThat(response.eventMode()).isEqualTo("LIVE_CONFIGURED_ERROR");
        assertThat(response.seoulCityData().qualityStatus()).isEqualTo("CONFIGURED_UNVERIFIED");
        assertThat(response.ktoTourApi().qualityStatus()).isEqualTo("ERROR");
    }

    @Test
    void getStatusTreatsSeoulCultureApiOkAsEventReadyWithoutTourApiOrPopupFile() {
        when(hotplaceService.getSeoulCityDataProviderStatus())
                .thenReturn(InsightStatusResponse.ProviderStatus.notConfigured("SEOUL_CITYDATA_API_KEY is not configured"));
        when(hotplaceService.getTelecomCrowdProviderStatus())
                .thenReturn(InsightStatusResponse.ProviderStatus.notConfigured("TELECOM_CROWD_API_KEY is not configured"));
        when(eventInsightService.getTourApiProviderStatus())
                .thenReturn(InsightStatusResponse.ProviderStatus.notConfigured("KTO_TOUR_API_KEY is not configured"));
        when(eventInsightService.getSeoulCultureApiProviderStatus())
                .thenReturn(InsightStatusResponse.ProviderStatus.ok(
                        Instant.parse("2026-06-18T00:20:00Z"),
                        "서울 문화행사 API 조회 성공"
                ));
        InsightStatusService service = new InsightStatusService(new ObjectMapper(), hotplaceService, eventInsightService);
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "");
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "");
        ReflectionTestUtils.setField(service, "telecomCrowdUrlTemplate", "");
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "seoulCultureApiKey", "culture-key");
        ReflectionTestUtils.setField(service, "popupTrendsFile", "");

        InsightStatusResponse response = service.getStatus();

        assertThat(response.eventMode()).isEqualTo("LIVE_OR_CRAWLED_READY");
        assertThat(response.ktoTourApiKeyConfigured()).isFalse();
        assertThat(response.seoulCultureApiKeyConfigured()).isTrue();
        assertThat(response.ktoTourApi().qualityStatus()).isEqualTo("NOT_CONFIGURED");
        assertThat(response.seoulCultureApi().qualityStatus()).isEqualTo("OK");
        assertThat(response.popupTrends().qualityStatus()).isEqualTo("NOT_CONFIGURED");
    }

    @Test
    void getStatusTreatsTelecomCrowdOkAsLiveReadyEvenWhenSeoulCityDataIsMissing() {
        when(hotplaceService.getSeoulCityDataProviderStatus())
                .thenReturn(InsightStatusResponse.ProviderStatus.notConfigured("SEOUL_CITYDATA_API_KEY is not configured"));
        when(hotplaceService.getTelecomCrowdProviderStatus())
                .thenReturn(InsightStatusResponse.ProviderStatus.ok(
                        Instant.parse("2026-06-18T00:15:00Z"),
                        "통신사 혼잡도 조회 성공"
                ));
        when(eventInsightService.getTourApiProviderStatus())
                .thenReturn(InsightStatusResponse.ProviderStatus.notConfigured("KTO_TOUR_API_KEY is not configured"));
        InsightStatusService service = new InsightStatusService(new ObjectMapper(), hotplaceService, eventInsightService);
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "");
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "telecom-key");
        ReflectionTestUtils.setField(service, "telecomCrowdUrlTemplate", "https://telecom.example/crowd?place={placeId}");
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", "");

        InsightStatusResponse response = service.getStatus();

        assertThat(response.hotplaceMode()).isEqualTo("LIVE_READY");
        assertThat(response.telecomCrowdKeyConfigured()).isTrue();
        assertThat(response.telecomCrowdUrlTemplateConfigured()).isTrue();
        assertThat(response.telecomCrowd().qualityStatus()).isEqualTo("OK");
    }

    @Test
    void getStatusSeparatesTelecomKeyFromUrlTemplateConfiguration() {
        when(hotplaceService.getSeoulCityDataProviderStatus())
                .thenReturn(InsightStatusResponse.ProviderStatus.notConfigured("SEOUL_CITYDATA_API_KEY is not configured"));
        when(hotplaceService.getTelecomCrowdProviderStatus())
                .thenReturn(InsightStatusResponse.ProviderStatus.configuredUnverified("TELECOM_CROWD_URL_TEMPLATE is missing"));
        when(eventInsightService.getTourApiProviderStatus())
                .thenReturn(InsightStatusResponse.ProviderStatus.notConfigured("KTO_TOUR_API_KEY is not configured"));
        InsightStatusService service = new InsightStatusService(new ObjectMapper(), hotplaceService, eventInsightService);
        ReflectionTestUtils.setField(service, "seoulCityDataApiKey", "");
        ReflectionTestUtils.setField(service, "telecomCrowdApiKey", "telecom-key");
        ReflectionTestUtils.setField(service, "telecomCrowdUrlTemplate", "");
        ReflectionTestUtils.setField(service, "tourApiKey", "");
        ReflectionTestUtils.setField(service, "popupTrendsFile", "");

        InsightStatusResponse response = service.getStatus();

        assertThat(response.hotplaceMode()).isEqualTo("LIVE_CONFIGURED_UNVERIFIED");
        assertThat(response.telecomCrowdKeyConfigured()).isTrue();
        assertThat(response.telecomCrowdUrlTemplateConfigured()).isFalse();
        assertThat(response.telecomCrowd().qualityStatus()).isEqualTo("CONFIGURED_UNVERIFIED");
    }
}
