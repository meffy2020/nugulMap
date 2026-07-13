package com.neogulmap.neogul_map.repository;

import com.neogulmap.neogul_map.domain.SupportRequest;
import com.neogulmap.neogul_map.domain.enums.SupportRequestCategory;
import com.neogulmap.neogul_map.domain.enums.SupportRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SupportRequestRepository extends JpaRepository<SupportRequest, Long> {

    long countByCreatedAtAfter(LocalDateTime createdAfter);

    boolean existsByEmailAndCategoryAndCreatedAtAfter(
            String email,
            SupportRequestCategory category,
            LocalDateTime createdAfter
    );

    List<SupportRequest> findTop100ByStatusInOrderByCreatedAtAsc(List<SupportRequestStatus> statuses);

    long deleteByStatusInAndResolvedAtBefore(
            List<SupportRequestStatus> statuses,
            LocalDateTime resolvedBefore
    );
}
