package com.neogulmap.neogul_map.config;

import com.neogulmap.neogul_map.controller.InsightController;
import com.neogulmap.neogul_map.controller.LocationLetterController;
import com.neogulmap.neogul_map.controller.RecommendationController;
import com.neogulmap.neogul_map.config.security.PublicInsightRateLimiter;
import com.neogulmap.neogul_map.service.*;
import com.neogulmap.neogul_map.service.recommendation.RecommendationDataService;
import com.neogulmap.neogul_map.service.recommendation.RecommendationQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SmokingOnlyReleaseTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(InsightController.class, LocationLetterController.class,
                    RecommendationController.class, HotplaceCacheWarmupScheduler.class,
                    EventCacheWarmupScheduler.class)
            .withBean(HotplaceService.class, () -> mock(HotplaceService.class))
            .withBean(EventInsightService.class, () -> mock(EventInsightService.class))
            .withBean(InsightStatusService.class, () -> mock(InsightStatusService.class))
            .withBean(PublicInsightRateLimiter.class, () -> mock(PublicInsightRateLimiter.class))
            .withBean(LocationLetterService.class, () -> mock(LocationLetterService.class))
            .withBean(RecommendationQueryService.class, () -> mock(RecommendationQueryService.class))
            .withBean(RecommendationDataService.class, () -> mock(RecommendationDataService.class));

    @Test
    void optionalDiscoveryControllersAndProviderJobsAreAbsentByDefault() {
        context.run(app -> {
            assertThat(app).hasNotFailed();
            assertThat(app).doesNotHaveBean(InsightController.class)
                    .doesNotHaveBean(LocationLetterController.class)
                    .doesNotHaveBean(RecommendationController.class)
                    .doesNotHaveBean(HotplaceCacheWarmupScheduler.class)
                    .doesNotHaveBean(EventCacheWarmupScheduler.class);
        });
    }

    @Test
    void historicalFeaturesRequireExplicitOptIn() {
        context.withPropertyValues("app.discovery.enabled=true",
                "external.insights.hotplace-warmup.enabled=true",
                "external.insights.event-warmup.enabled=true").run(app -> {
            assertThat(app).hasNotFailed();
            assertThat(app).hasSingleBean(InsightController.class)
                    .hasSingleBean(LocationLetterController.class)
                    .hasSingleBean(RecommendationController.class)
                    .hasSingleBean(HotplaceCacheWarmupScheduler.class)
                    .hasSingleBean(EventCacheWarmupScheduler.class);
        });
    }
}
