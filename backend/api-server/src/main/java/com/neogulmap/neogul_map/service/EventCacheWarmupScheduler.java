package com.neogulmap.neogul_map.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "external.insights.event-warmup.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class EventCacheWarmupScheduler {

    private final EventInsightService eventInsightService;

    @Scheduled(
            initialDelayString = "${external.insights.event-warmup.initial-delay-ms:0}",
            fixedDelayString = "${external.insights.event-warmup.interval-ms:86400000}"
    )
    public void warmCache() {
        eventInsightService.warmEventCache();
    }
}
