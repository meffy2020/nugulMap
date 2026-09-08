package com.neogulmap.neogul_map.repository;

import com.neogulmap.neogul_map.domain.LocationLetterReport;
import com.neogulmap.neogul_map.domain.enums.ModerationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface LocationLetterReportRepository extends JpaRepository<LocationLetterReport, Long> {
    boolean existsByLetterIdAndReporterId(Long letterId, Long reporterId);
    List<LocationLetterReport> findTop100ByStatusOrderByCreatedAtAsc(ModerationStatus status);
    void deleteByLetterId(Long letterId);
    long deleteByStatusInAndResolvedAtBefore(List<ModerationStatus> statuses, LocalDateTime cutoff);
}
