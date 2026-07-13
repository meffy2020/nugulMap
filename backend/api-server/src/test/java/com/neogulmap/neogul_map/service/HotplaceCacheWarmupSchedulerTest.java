package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.NeogulMapApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class HotplaceCacheWarmupSchedulerTest {

    @Test
    void applicationEnablesScheduling() {
        assertThat(AnnotatedElementUtils.hasAnnotation(NeogulMapApplication.class, EnableScheduling.class)).isTrue();
    }

    @Test
    void initialScheduledWarmupRunsAfterContextStarts() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of(
                    "external.insights.hotplace-warmup.enabled=true",
                    "external.insights.hotplace-warmup.initial-delay-ms=0",
                    "external.insights.hotplace-warmup.interval-ms=3600000"
            ).applyTo(context);
            context.register(SchedulingTestConfig.class);
            context.refresh();

            verify(context.getBean(HotplaceService.class), timeout(2000).atLeastOnce()).warmHotplaceCache();
        }
    }

    @Test
    void defaultPollingCadenceMatchesFiveMinuteProviderUpdateCadence() throws Exception {
        Scheduled scheduled = HotplaceCacheWarmupScheduler.class
                .getDeclaredMethod("warmCache")
                .getAnnotation(Scheduled.class);
        assertThat(scheduled.fixedDelayString())
                .isEqualTo("${external.insights.hotplace-warmup.interval-ms:300000}");

        Object cacheTtl = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .getFirst()
                .getProperty("external.insights.cache-ttl-seconds");
        assertThat(cacheTtl).isEqualTo("${INSIGHTS_CACHE_TTL_SECONDS:300}");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @Import(HotplaceCacheWarmupScheduler.class)
    static class SchedulingTestConfig {

        @Bean
        HotplaceService hotplaceService() {
            return mock(HotplaceService.class);
        }
    }
}
