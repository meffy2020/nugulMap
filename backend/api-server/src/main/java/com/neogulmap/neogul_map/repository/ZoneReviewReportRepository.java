package com.neogulmap.neogul_map.repository;

import com.neogulmap.neogul_map.domain.ZoneReviewReport;
import com.neogulmap.neogul_map.domain.enums.ModerationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ZoneReviewReportRepository extends JpaRepository<ZoneReviewReport, Long> {
    boolean existsByReviewIdAndReporterId(Long reviewId, Long reporterId);

    List<ZoneReviewReport> findTop100ByStatusOrderByCreatedAtAsc(ModerationStatus status);

    long deleteByStatusInAndResolvedAtBefore(
            List<ModerationStatus> statuses,
            LocalDateTime resolvedBefore
    );

    void deleteByReviewId(Long reviewId);
}
