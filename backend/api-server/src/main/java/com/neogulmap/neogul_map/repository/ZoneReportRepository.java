package com.neogulmap.neogul_map.repository;

import com.neogulmap.neogul_map.domain.ZoneReport;
import com.neogulmap.neogul_map.domain.enums.ModerationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ZoneReportRepository extends JpaRepository<ZoneReport, Long> {
    boolean existsByZoneIdAndReporterId(Integer zoneId, Long reporterId);

    List<ZoneReport> findTop100ByStatusOrderByCreatedAtAsc(ModerationStatus status);

    long deleteByStatusInAndResolvedAtBefore(
            List<ModerationStatus> statuses,
            LocalDateTime resolvedBefore
    );

    void deleteByZoneId(Integer zoneId);
}
