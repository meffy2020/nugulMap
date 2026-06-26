package com.neogulmap.neogul_map.dto;

import java.time.Instant;

public record InsightStatusResponse(
        boolean seoulCityDataKeyConfigured,
        boolean telecomCrowdKeyConfigured,
        boolean telecomCrowdUrlTemplateConfigured,
        boolean ktoTourApiKeyConfigured,
        boolean seoulCultureApiKeyConfigured,
        String hotplaceMode,
        String eventMode,
        ProviderStatus seoulCityData,
        ProviderStatus telecomCrowd,
        ProviderStatus ktoTourApi,
        ProviderStatus seoulCultureApi,
        PopupTrendStatus popupTrends,
        Instant checkedAt
) {
    public record ProviderStatus(
            boolean configured,
            String qualityStatus,
            Instant lastSuccessAt,
            Instant lastFailureAt,
            String detail
    ) {
        public static ProviderStatus notConfigured(String detail) {
            return new ProviderStatus(false, "NOT_CONFIGURED", null, null, detail);
        }

        public static ProviderStatus configuredUnverified(String detail) {
            return new ProviderStatus(true, "CONFIGURED_UNVERIFIED", null, null, detail);
        }

        public static ProviderStatus ok(Instant lastSuccessAt, String detail) {
            return new ProviderStatus(true, "OK", lastSuccessAt, null, detail);
        }

        public static ProviderStatus error(Instant lastSuccessAt, Instant lastFailureAt, String detail) {
            return new ProviderStatus(true, "ERROR", lastSuccessAt, lastFailureAt, detail);
        }
    }

    public record PopupTrendStatus(
            boolean fileConfigured,
            boolean fileExists,
            int recordCount,
            Instant latestCollectedAt,
            String qualityStatus,
            String detail
    ) {
    }
}
