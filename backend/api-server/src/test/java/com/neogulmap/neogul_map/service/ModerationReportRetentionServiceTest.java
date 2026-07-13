package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.domain.enums.ModerationStatus;
import com.neogulmap.neogul_map.repository.ZoneReportRepository;
import com.neogulmap.neogul_map.repository.ZoneReviewReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModerationReportRetentionServiceTest {

    @Mock private ZoneReviewReportRepository reviewReportRepository;
    @Mock private ZoneReportRepository zoneReportRepository;

    private ModerationReportRetentionService service;

    @BeforeEach
    void setUp() {
        service = new ModerationReportRetentionService(
                reviewReportRepository,
                zoneReportRepository,
                Clock.fixed(Instant.parse("2026-07-12T08:15:00Z"), ZoneOffset.UTC)
        );
        ReflectionTestUtils.setField(service, "closedReportRetentionDays", 14L);
    }

    @Test
    void scheduledPurgeDeletesExpiredResolvedAndDismissedReportsUsingConfiguredRetention() {
        List<ModerationStatus> terminalStatuses = List.of(
                ModerationStatus.RESOLVED,
                ModerationStatus.DISMISSED
        );
        LocalDateTime cutoff = LocalDateTime.parse("2026-06-28T08:15:00");
        when(reviewReportRepository.deleteByStatusInAndResolvedAtBefore(terminalStatuses, cutoff))
                .thenReturn(2L);
        when(zoneReportRepository.deleteByStatusInAndResolvedAtBefore(terminalStatuses, cutoff))
                .thenReturn(3L);

        long purged = service.purgeExpiredClosedReports();

        assertThat(purged).isEqualTo(5L);
        verify(reviewReportRepository).deleteByStatusInAndResolvedAtBefore(terminalStatuses, cutoff);
        verify(zoneReportRepository).deleteByStatusInAndResolvedAtBefore(terminalStatuses, cutoff);
    }
}
