package com.neogulmap.neogul_map.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neogulmap.neogul_map.dto.InsightStatusResponse;
import com.neogulmap.neogul_map.dto.InsightStatusResponse.ProviderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InsightStatusService {

    private final ObjectMapper objectMapper;
    private final HotplaceService hotplaceService;
    private final EventInsightService eventInsightService;

    @Value("${external.seoul.citydata.api-key:${SEOUL_CITYDATA_API_KEY:}}")
    private String seoulCityDataApiKey;

    @Value("${external.telecom.crowd.api-key:${TELECOM_CROWD_API_KEY:}}")
    private String telecomCrowdApiKey;

    @Value("${external.telecom.crowd.url-template:${TELECOM_CROWD_URL_TEMPLATE:}}")
    private String telecomCrowdUrlTemplate;

    @Value("${external.kto.tour-api-key:${KTO_TOUR_API_KEY:}}")
    private String tourApiKey;

    @Value("${external.seoul.culture-api-key:${SEOUL_CULTURE_API_KEY:}}")
    private String seoulCultureApiKey;

    @Value("${external.popup-trends.file:${POPUP_TRENDS_FILE:}}")
    private String popupTrendsFile;

    @Value("${external.popup-trends.max-age-hours:${POPUP_TRENDS_MAX_AGE_HOURS:24}}")
    private long popupTrendsMaxAgeHours;

    public InsightStatusResponse getStatus() {
        boolean hasSeoulKey = hasText(seoulCityDataApiKey);
        boolean hasTelecomKey = hasText(telecomCrowdApiKey);
        boolean hasTelecomUrlTemplate = hasText(telecomCrowdUrlTemplate);
        boolean hasTourKey = hasText(tourApiKey);
        boolean hasSeoulCultureKey = hasText(seoulCultureApiKey);
        InsightStatusResponse.PopupTrendStatus popupStatus = inspectPopupTrends();
        ProviderStatus seoulCityDataStatus = hotplaceService.getSeoulCityDataProviderStatus();
        ProviderStatus telecomCrowdStatus = hotplaceService.getTelecomCrowdProviderStatus();
        ProviderStatus tourApiStatus = eventInsightService.getTourApiProviderStatus();
        ProviderStatus seoulCultureApiStatus = eventInsightService.getSeoulCultureApiProviderStatus();

        return new InsightStatusResponse(
                hasSeoulKey,
                hasTelecomKey,
                hasTelecomUrlTemplate,
                hasTourKey,
                hasSeoulCultureKey,
                hotplaceMode(hasSeoulKey, hasTelecomKey, seoulCityDataStatus, telecomCrowdStatus),
                eventMode(hasTourKey, hasSeoulCultureKey, tourApiStatus, seoulCultureApiStatus, popupStatus),
                seoulCityDataStatus,
                telecomCrowdStatus,
                tourApiStatus,
                seoulCultureApiStatus,
                popupStatus,
                Instant.now()
        );
    }

    private String hotplaceMode(
            boolean hasSeoulKey,
            boolean hasTelecomKey,
            ProviderStatus seoulCityDataStatus,
            ProviderStatus telecomCrowdStatus
    ) {
        if (!hasSeoulKey && !hasTelecomKey) {
            return "NO_VERIFIED_DATA";
        }
        if ("OK".equals(telecomCrowdStatus.qualityStatus()) || "OK".equals(seoulCityDataStatus.qualityStatus())) {
            return "LIVE_READY";
        }
        if ("ERROR".equals(telecomCrowdStatus.qualityStatus()) || "ERROR".equals(seoulCityDataStatus.qualityStatus())) {
            return "LIVE_CONFIGURED_ERROR";
        }
        return "LIVE_CONFIGURED_UNVERIFIED";
    }

    private String eventMode(
            boolean hasTourKey,
            boolean hasSeoulCultureKey,
            ProviderStatus tourApiStatus,
            ProviderStatus seoulCultureApiStatus,
            InsightStatusResponse.PopupTrendStatus popupStatus
    ) {
        if ("OK".equals(tourApiStatus.qualityStatus())
                || "OK".equals(seoulCultureApiStatus.qualityStatus())
                || ("OK".equals(popupStatus.qualityStatus()) && popupStatus.recordCount() > 0)) {
            return "LIVE_OR_CRAWLED_READY";
        }
        if (hasTourKey || hasSeoulCultureKey) {
            return "ERROR".equals(tourApiStatus.qualityStatus()) || "ERROR".equals(seoulCultureApiStatus.qualityStatus())
                    ? "LIVE_CONFIGURED_ERROR"
                    : "LIVE_CONFIGURED_UNVERIFIED";
        }
        return "NO_VERIFIED_DATA";
    }

    private InsightStatusResponse.PopupTrendStatus inspectPopupTrends() {
        if (!hasText(popupTrendsFile)) {
            return new InsightStatusResponse.PopupTrendStatus(
                    false,
                    false,
                    0,
                    null,
                    "NOT_CONFIGURED",
                    "POPUP_TRENDS_FILE is not configured"
            );
        }

        File file = new File(popupTrendsFile.trim());
        if (!file.isFile()) {
            return new InsightStatusResponse.PopupTrendStatus(
                    true,
                    false,
                    0,
                    null,
                    "MISSING_FILE",
                    "Configured popup trends file does not exist"
            );
        }

        try {
            Object payload = objectMapper.readValue(file, Object.class);
            List<?> items = readItems(payload);
            if (items.isEmpty()) {
                return new InsightStatusResponse.PopupTrendStatus(
                        true,
                        true,
                        0,
                        null,
                        "EMPTY",
                        "Popup trends file has no items"
                );
            }

            PopupInspection inspection = inspectItems(items);
            String qualityStatus = popupQualityStatus(inspection);
            return new InsightStatusResponse.PopupTrendStatus(
                    true,
                    true,
                    items.size(),
                    inspection.latestCollectedAt(),
                    qualityStatus,
                    popupQualityDetail(inspection, qualityStatus)
            );
        } catch (Exception error) {
            return new InsightStatusResponse.PopupTrendStatus(
                    true,
                    true,
                    0,
                    null,
                    "INVALID_JSON",
                    "Popup trends file cannot be parsed"
            );
        }
    }

    @SuppressWarnings("unchecked")
    private List<?> readItems(Object payload) {
        if (payload instanceof List<?> list) {
            return list;
        }
        if (payload instanceof Map<?, ?> map && map.get("items") instanceof List<?> list) {
            return list;
        }
        return List.of();
    }

    private PopupInspection inspectItems(List<?> items) {
        int invalidRecords = 0;
        int liveEligibleRecords = 0;
        Instant latestCollectedAt = null;
        Instant latestLiveCollectedAt = null;

        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                invalidRecords += 1;
                continue;
            }

            boolean manualSeed = isManualSeed(map);
            boolean invalidRecord = !hasText(map.get("id"))
                    || !hasText(map.get("title"))
                    || !hasText(map.get("kind"))
                    || !isNumber(map.get("latitude"))
                    || !isNumber(map.get("longitude"));
            if (!manualSeed && (!hasText(map.get("startDate"))
                    || !hasText(map.get("endDate"))
                    || !hasSafeDetailUrl(map))) {
                invalidRecord = true;
            }
            if (invalidRecord) {
                invalidRecords += 1;
            }

            Instant collectedAt = parseInstant(map.get("collectedAt"));
            if (collectedAt != null && (latestCollectedAt == null || collectedAt.isAfter(latestCollectedAt))) {
                latestCollectedAt = collectedAt;
            }
            if (!manualSeed && !invalidRecord) {
                liveEligibleRecords += 1;
                if (collectedAt != null && (latestLiveCollectedAt == null || collectedAt.isAfter(latestLiveCollectedAt))) {
                    latestLiveCollectedAt = collectedAt;
                }
            }
        }

        return new PopupInspection(invalidRecords, liveEligibleRecords, latestCollectedAt, latestLiveCollectedAt);
    }

    private String popupQualityStatus(PopupInspection inspection) {
        if (inspection.invalidRecords() > 0) {
            return "INVALID_RECORDS";
        }
        if (inspection.latestCollectedAt() == null) {
            return "MISSING_COLLECTED_AT";
        }
        if (inspection.liveEligibleRecords() == 0) {
            return "MANUAL_ONLY";
        }
        if (inspection.latestLiveCollectedAt() == null) {
            return "MISSING_COLLECTED_AT";
        }
        Instant staleBefore = Instant.now().minus(Duration.ofHours(normalizedPopupTrendsMaxAgeHours()));
        return inspection.latestLiveCollectedAt().isBefore(staleBefore) ? "STALE" : "OK";
    }

    private String popupQualityDetail(PopupInspection inspection, String qualityStatus) {
        return switch (qualityStatus) {
            case "INVALID_RECORDS" -> inspection.invalidRecords() + " popup trend records are missing required fields";
            case "MISSING_COLLECTED_AT" -> "Popup trends file has no valid collectedAt timestamp";
            case "MANUAL_ONLY" -> "Popup trends file contains only manual seed records";
            case "STALE" -> "Popup trends file is older than " + normalizedPopupTrendsMaxAgeHours() + " hours";
            default -> "Popup trends file is readable and fresh";
        };
    }

    private boolean isManualSeed(Map<?, ?> item) {
        return "MANUAL".equalsIgnoreCase(String.valueOf(item.get("collectionMode")))
                || "MANUAL_SEED".equalsIgnoreCase(String.valueOf(item.get("source")));
    }

    private boolean hasSafeDetailUrl(Map<?, ?> item) {
        Object value = item.get("detailUrl");
        if (!hasText(value) && item.get("collectionMode") == null) {
            value = item.get("sourceContentId");
        }
        if (!hasText(value)) {
            return false;
        }
        try {
            URI uri = URI.create(String.valueOf(value).trim());
            String scheme = uri.getScheme();
            return uri.isAbsolute()
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private long normalizedPopupTrendsMaxAgeHours() {
        return popupTrendsMaxAgeHours > 0 ? popupTrendsMaxAgeHours : 24L;
    }

    private boolean hasText(Object value) {
        return value != null && !String.valueOf(value).trim().isEmpty();
    }

    private boolean isNumber(Object value) {
        if (value instanceof Number) {
            return true;
        }
        if (!hasText(value)) {
            return false;
        }
        try {
            Double.parseDouble(String.valueOf(value).trim());
            return true;
        } catch (NumberFormatException error) {
            return false;
        }
    }

    private Instant parseInstant(Object value) {
        if (!hasText(value)) {
            return null;
        }

        String text = String.valueOf(value).trim();
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(text).toInstant();
            } catch (DateTimeParseException error) {
                return null;
            }
        }
    }

    private record PopupInspection(
            int invalidRecords,
            int liveEligibleRecords,
            Instant latestCollectedAt,
            Instant latestLiveCollectedAt
    ) {
    }
}
