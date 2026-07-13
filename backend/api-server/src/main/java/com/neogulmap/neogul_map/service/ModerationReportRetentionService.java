package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.domain.enums.ModerationStatus;
import com.neogulmap.neogul_map.repository.ZoneReportRepository;
import com.neogulmap.neogul_map.repository.ZoneReviewReportRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class ModerationReportRetentionService {

    private static final List<ModerationStatus> TERMINAL_STATUSES = List.of(
            ModerationStatus.RESOLVED,
            ModerationStatus.DISMISSED
    );

    private final ZoneReviewReportRepository reviewReportRepository;
    private final ZoneReportRepository zoneReportRepository;
    private final Clock clock;

    @Value("${app.moderation.closed-report-retention-days:30}")
    private long closedReportRetentionDays = 30;

    @Autowired
    public ModerationReportRetentionService(
            ZoneReviewReportRepository reviewReportRepository,
            ZoneReportRepository zoneReportRepository
    ) {
        this(reviewReportRepository, zoneReportRepository, Clock.systemUTC());
    }

    ModerationReportRetentionService(
            ZoneReviewReportRepository reviewReportRepository,
            ZoneReportRepository zoneReportRepository,
            Clock clock
    ) {
        this.reviewReportRepository = reviewReportRepository;
        this.zoneReportRepository = zoneReportRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.moderation.closed-report-purge-cron:0 45 3 * * *}", zone = "UTC")
    @Transactional
    public long purgeExpiredClosedReports() {
        long retentionDays = Math.max(1, closedReportRetentionDays);
        LocalDateTime cutoff = now().minusDays(retentionDays);
        return reviewReportRepository.deleteByStatusInAndResolvedAtBefore(TERMINAL_STATUSES, cutoff)
                + zoneReportRepository.deleteByStatusInAndResolvedAtBefore(TERMINAL_STATUSES, cutoff);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
