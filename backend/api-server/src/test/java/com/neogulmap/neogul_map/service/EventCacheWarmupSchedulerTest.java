package com.neogulmap.neogul_map.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class EventCacheWarmupSchedulerTest {

    @Test
    void initialScheduledWarmupRunsAfterContextStarts() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of(
                    "external.insights.event-warmup.enabled=true",
                    "external.insights.event-warmup.initial-delay-ms=0",
                    "external.insights.event-warmup.interval-ms=3600000"
            ).applyTo(context);
            context.register(SchedulingTestConfig.class);
            context.refresh();

            verify(context.getBean(EventInsightService.class), timeout(2000).atLeastOnce()).warmEventCache();
        }
    }

    @Test
    void defaultPollingCadenceIsTwentyFourHours() throws Exception {
        Scheduled scheduled = EventCacheWarmupScheduler.class
                .getDeclaredMethod("warmCache")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.fixedDelayString())
                .isEqualTo("${external.insights.event-warmup.interval-ms:86400000}");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @Import(EventCacheWarmupScheduler.class)
    static class SchedulingTestConfig {

        @Bean
        EventInsightService eventInsightService() {
            return mock(EventInsightService.class);
        }
    }
}
