package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException;
import com.neogulmap.neogul_map.domain.SupportRequest;
import com.neogulmap.neogul_map.domain.enums.SupportRequestCategory;
import com.neogulmap.neogul_map.domain.enums.SupportRequestStatus;
import com.neogulmap.neogul_map.dto.SupportRequestCreateRequest;
import com.neogulmap.neogul_map.dto.SupportRequestOperatorResponse;
import com.neogulmap.neogul_map.dto.SupportRequestStatusUpdateRequest;
import com.neogulmap.neogul_map.dto.SupportRequestResponse;
import com.neogulmap.neogul_map.repository.SupportRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportRequestServiceTest {

    @Mock private SupportRequestRepository repository;
    private SupportRequestService service;

    @BeforeEach
    void setUp() {
        service = new SupportRequestService(
                repository,
                Clock.fixed(Instant.parse("2026-07-10T10:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void publicAccountDeletionRequestIsValidatedAndQueued() {
        when(repository.countByCreatedAtAfter(any())).thenReturn(0L);
        when(repository.existsByEmailAndCategoryAndCreatedAtAfter(any(), any(), any())).thenReturn(false);
        when(repository.save(any(SupportRequest.class))).thenAnswer(invocation -> {
            SupportRequest request = invocation.getArgument(0);
            request.setId(21L);
            request.onCreate();
            return request;
        });

        SupportRequestResponse response = service.create(new SupportRequestCreateRequest(
                "ACCOUNT_DELETION",
                " User@Example.com ",
                "Apple 로그인 계정을 삭제하고 싶습니다.",
                ""
        ));

        assertThat(response.id()).isEqualTo(21L);
        assertThat(response.category()).isEqualTo("ACCOUNT_DELETION");
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    void sameEmailAndCategoryCannotFloodRequestsInsideCooldown() {
        when(repository.countByCreatedAtAfter(any())).thenReturn(0L);
        when(repository.existsByEmailAndCategoryAndCreatedAtAfter(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.create(new SupportRequestCreateRequest(
                "ACCOUNT_SUPPORT", "user@example.com", "도움이 필요합니다.", null
        ))).isInstanceOf(BusinessBaseException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void honeypotAndInvalidEmailAreRejected() {
        assertThatThrownBy(() -> service.create(new SupportRequestCreateRequest(
                "OTHER", "not-an-email", "문의", "bot.example"
        ))).isInstanceOf(ValidationException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void operatorCanResolveAPendingSupportRequest() {
        SupportRequest request = supportRequest(31L, SupportRequestStatus.PENDING);
        when(repository.findById(31L)).thenReturn(Optional.of(request));
        when(repository.save(request)).thenReturn(request);

        SupportRequestOperatorResponse response = service.updateStatus(
                31L,
                new SupportRequestStatusUpdateRequest("RESOLVED")
        );

        assertThat(request.getStatus()).isEqualTo(SupportRequestStatus.RESOLVED);
        assertThat(request.getResolvedAt()).isEqualTo(LocalDateTime.parse("2026-07-10T10:00:00"));
        assertThat(response.status()).isEqualTo("RESOLVED");
        assertThat(response.resolvedAt()).isEqualTo(LocalDateTime.parse("2026-07-10T10:00:00"));
        verify(repository).save(request);
    }

    @Test
    void pendingAndInProgressRequestsRemainVisibleToOperators() {
        SupportRequest pending = supportRequest(41L, SupportRequestStatus.PENDING);
        SupportRequest inProgress = supportRequest(42L, SupportRequestStatus.IN_PROGRESS);
        when(repository.findTop100ByStatusInOrderByCreatedAtAsc(List.of(
                SupportRequestStatus.PENDING,
                SupportRequestStatus.IN_PROGRESS
        ))).thenReturn(List.of(pending, inProgress));

        List<SupportRequestOperatorResponse> responses = service.getActiveRequests();

        assertThat(responses).extracting(SupportRequestOperatorResponse::id).containsExactly(41L, 42L);
    }

    @Test
    void expiredTerminalRequestsArePurgedFromPiiStorage() {
        when(repository.deleteByStatusInAndResolvedAtBefore(any(), any())).thenReturn(3L);

        long purged = service.purgeExpiredClosedRequests();

        assertThat(purged).isEqualTo(3L);
        verify(repository).deleteByStatusInAndResolvedAtBefore(
                List.of(SupportRequestStatus.RESOLVED, SupportRequestStatus.REJECTED),
                LocalDateTime.parse("2026-06-10T10:00:00")
        );
    }

    @Test
    void terminalSupportRequestCannotBeReopened() {
        SupportRequest request = supportRequest(32L, SupportRequestStatus.RESOLVED);
        when(repository.findById(32L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.updateStatus(
                32L,
                new SupportRequestStatusUpdateRequest("IN_PROGRESS")
        )).isInstanceOf(ValidationException.class);

        verify(repository, never()).save(any());
    }

    private SupportRequest supportRequest(Long id, SupportRequestStatus status) {
        return SupportRequest.builder()
                .id(id)
                .category(SupportRequestCategory.ACCOUNT_SUPPORT)
                .email("user@example.com")
                .message("도움이 필요합니다.")
                .status(status)
                .createdAt(LocalDateTime.parse("2026-07-10T10:00:00"))
                .build();
    }
}
