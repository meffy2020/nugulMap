package com.neogulmap.neogul_map.service.recommendation;

import com.neogulmap.neogul_map.repository.recommendation.RecommendationInteractionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationInteractionRetentionServiceTest {

    @Test
    void purgeUsesConfiguredRetentionAndServerClock() {
        RecommendationInteractionRepository repository = mock(RecommendationInteractionRepository.class);
        RecommendationInteractionRetentionService service = new RecommendationInteractionRetentionService(
                repository,
                Clock.fixed(Instant.parse("2026-09-01T04:15:00Z"), ZoneOffset.UTC)
        );
        ReflectionTestUtils.setField(service, "retentionDays", 180L);
        LocalDateTime cutoff = LocalDateTime.parse("2026-03-05T04:15:00");
        when(repository.deleteExpiredBefore(cutoff)).thenReturn(12L);

        assertThat(service.purgeExpiredInteractions()).isEqualTo(12L);
        verify(repository).deleteExpiredBefore(cutoff);
    }
}
