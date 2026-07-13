package com.neogulmap.neogul_map.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "external.insights.hotplace-warmup.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class HotplaceCacheWarmupScheduler {

    private final HotplaceService hotplaceService;

    @Scheduled(
            initialDelayString = "${external.insights.hotplace-warmup.initial-delay-ms:0}",
            fixedDelayString = "${external.insights.hotplace-warmup.interval-ms:300000}"
    )
    public void warmCache() {
        hotplaceService.warmHotplaceCache();
    }
}
