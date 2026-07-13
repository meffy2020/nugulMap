package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.domain.Zone;
import com.neogulmap.neogul_map.domain.ZoneReport;
import com.neogulmap.neogul_map.domain.enums.ModerationStatus;
import com.neogulmap.neogul_map.domain.enums.ZoneReportReason;
import com.neogulmap.neogul_map.domain.enums.ImageType;
import com.neogulmap.neogul_map.domain.enums.ZonePublicationStatus;
import com.neogulmap.neogul_map.dto.ModerationDecisionRequest;
import com.neogulmap.neogul_map.dto.ModerationDecisionResponse;
import com.neogulmap.neogul_map.dto.ZoneReportOperatorResponse;
import com.neogulmap.neogul_map.dto.ZoneReportRequest;
import com.neogulmap.neogul_map.dto.ZoneReportResponse;
import com.neogulmap.neogul_map.dto.ZoneSubmissionOperatorResponse;
import com.neogulmap.neogul_map.dto.ZonePublicationDecisionResponse;
import com.neogulmap.neogul_map.repository.ZoneReportRepository;
import com.neogulmap.neogul_map.repository.ZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZoneModerationServiceTest {

    @Mock private ZoneRepository zoneRepository;
    @Mock private ZoneReportRepository reportRepository;
    @Mock private ImageService imageService;

    private ZoneModerationService service;

    @BeforeEach
    void setUp() {
        service = new ZoneModerationService(
                zoneRepository,
                reportRepository,
                imageService,
                Clock.fixed(Instant.parse("2026-07-12T08:15:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void authenticatedUserCanReportPublicZoneContent() {
        User reporter = User.builder().id(1L).build();
        Zone zone = zone(10);
        when(zoneRepository.findByIdAndPublicationStatus(10, ZonePublicationStatus.PUBLISHED))
                .thenReturn(Optional.of(zone));
        when(reportRepository.existsByZoneIdAndReporterId(10, 1L)).thenReturn(false);
        when(reportRepository.save(any(ZoneReport.class))).thenAnswer(invocation -> {
            ZoneReport report = invocation.getArgument(0);
            report.setId(15L);
            report.onCreate();
            return report;
        });

        ZoneReportResponse response = service.reportZone(
                10,
                new ZoneReportRequest("INACCURATE", "주소가 다른 장소입니다."),
                reporter
        );

        assertThat(response.id()).isEqualTo(15L);
        assertThat(response.zoneId()).isEqualTo(10);
        assertThat(response.reason()).isEqualTo("INACCURATE");
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    void duplicateZoneReportIsRejectedWithoutWritingAgain() {
        User reporter = User.builder().id(1L).build();
        when(zoneRepository.findByIdAndPublicationStatus(10, ZonePublicationStatus.PUBLISHED))
                .thenReturn(Optional.of(zone(10)));
        when(reportRepository.existsByZoneIdAndReporterId(10, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.reportZone(
                10,
                new ZoneReportRequest("DUPLICATE", null),
                reporter
        )).isInstanceOf(BusinessBaseException.class);

        verify(reportRepository, never()).save(any());
    }

    @Test
    void otherReasonRequiresDetails() {
        User reporter = User.builder().id(1L).build();
        when(zoneRepository.findByIdAndPublicationStatus(10, ZonePublicationStatus.PUBLISHED))
                .thenReturn(Optional.of(zone(10)));
        when(reportRepository.existsByZoneIdAndReporterId(10, 1L)).thenReturn(false);

        assertThatThrownBy(() -> service.reportZone(
                10,
                new ZoneReportRequest("OTHER", "  "),
                reporter
        )).isInstanceOf(BusinessBaseException.class);

        verify(reportRepository, never()).save(any());
    }

    @Test
    void operatorQueueExposesModerationFieldsWithoutUserEmail() {
        Zone zone = zone(10);
        zone.setCreator(User.builder().id(2L).email("creator@example.com").build());
        ZoneReport report = ZoneReport.builder()
                .id(15L)
                .zone(zone)
                .reporter(User.builder().id(1L).email("reporter@example.com").build())
                .reason(ZoneReportReason.PERSONAL_INFORMATION)
                .details("사진에 전화번호가 보입니다.")
                .status(ModerationStatus.PENDING)
                .createdAt(LocalDateTime.parse("2026-07-11T12:00:00"))
                .build();
        when(reportRepository.findTop100ByStatusOrderByCreatedAtAsc(ModerationStatus.PENDING))
                .thenReturn(List.of(report));

        List<ZoneReportOperatorResponse> queue = service.getPendingReports();

        assertThat(queue).singleElement().satisfies(item -> {
            assertThat(item.zoneId()).isEqualTo(10);
            assertThat(item.creatorId()).isEqualTo(2L);
            assertThat(item.reporterId()).isEqualTo(1L);
            assertThat(item.address()).isEqualTo("서울 성동구 연무장길 1");
            assertThat(item.reason()).isEqualTo("PERSONAL_INFORMATION");
        });
    }

    @Test
    void operatorCanDismissPendingZoneReport() {
        ZoneReport report = pendingReport(zone(10));
        when(reportRepository.findById(15L)).thenReturn(Optional.of(report));

        ModerationDecisionResponse response = service.decideReport(
                15L,
                new ModerationDecisionRequest("DISMISS")
        );

        assertThat(response.status()).isEqualTo("DISMISSED");
        assertThat(response.contentRemoved()).isFalse();
        assertThat(report.getStatus()).isEqualTo(ModerationStatus.DISMISSED);
        assertThat(report.getResolvedAt()).isEqualTo(LocalDateTime.parse("2026-07-12T08:15:00"));
        verify(reportRepository).save(report);
        verify(zoneRepository, never()).delete(any(Zone.class));
    }

    @Test
    void operatorCanRemoveReportedZoneAndItsImage() {
        Zone zone = zone(10);
        zone.setImage("reported-zone.jpg");
        ZoneReport report = pendingReport(zone);
        when(reportRepository.findById(15L)).thenReturn(Optional.of(report));

        TransactionSynchronizationManager.initSynchronization();
        try {
            ModerationDecisionResponse response = service.decideReport(
                    15L,
                    new ModerationDecisionRequest("REMOVE_CONTENT")
            );

            assertThat(response.status()).isEqualTo("RESOLVED");
            assertThat(response.contentRemoved()).isTrue();
            verify(reportRepository).deleteByZoneId(10);
            verify(zoneRepository).delete(zone);
            verify(imageService, never()).deleteImage("reported-zone.jpg", ImageType.ZONE);

            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(imageService).deleteImage("reported-zone.jpg", ImageType.ZONE);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void operatorQueueReturnsUnpublishedUserZoneWithImageForReview() {
        Zone pending = zone(20);
        pending.setCreator(User.builder().id(2L).email("creator@example.com").build());
        pending.setImage("pending-zone.jpg");
        pending.setPublicationStatus(ZonePublicationStatus.PENDING);
        when(zoneRepository.findTop100ByPublicationStatusOrderByDateAscIdAsc(ZonePublicationStatus.PENDING))
                .thenReturn(List.of(pending));

        List<ZoneSubmissionOperatorResponse> queue = service.getPendingSubmissions();

        assertThat(queue).singleElement().satisfies(item -> {
            assertThat(item.zoneId()).isEqualTo(20);
            assertThat(item.creatorId()).isEqualTo(2L);
            assertThat(item.imageUrl()).isEqualTo("/images/pending-zone.jpg");
            assertThat(item.status()).isEqualTo("PENDING");
        });
    }

    @Test
    void operatorCanPublishReviewedZoneSubmission() {
        Zone pending = zone(20);
        pending.setPublicationStatus(ZonePublicationStatus.PENDING);
        when(zoneRepository.findById(20)).thenReturn(Optional.of(pending));

        ZonePublicationDecisionResponse response = service.decideSubmission(
                20,
                new ModerationDecisionRequest("PUBLISH")
        );

        assertThat(response.status()).isEqualTo("PUBLISHED");
        assertThat(response.contentRemoved()).isFalse();
        assertThat(pending.getPublicationStatus()).isEqualTo(ZonePublicationStatus.PUBLISHED);
        verify(zoneRepository).save(pending);
    }

    @Test
    void operatorCanRejectPendingZoneAndDeleteItsImageAfterCommit() {
        Zone pending = zone(20);
        pending.setImage("pending-zone.jpg");
        pending.setPublicationStatus(ZonePublicationStatus.PENDING);
        when(zoneRepository.findById(20)).thenReturn(Optional.of(pending));

        TransactionSynchronizationManager.initSynchronization();
        try {
            ZonePublicationDecisionResponse response = service.decideSubmission(
                    20,
                    new ModerationDecisionRequest("REJECT")
            );

            assertThat(response.status()).isEqualTo("REJECTED");
            assertThat(response.contentRemoved()).isTrue();
            verify(zoneRepository).delete(pending);
            verify(imageService, never()).deleteImage("pending-zone.jpg", ImageType.ZONE);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(imageService).deleteImage("pending-zone.jpg", ImageType.ZONE);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private ZoneReport pendingReport(Zone zone) {
        return ZoneReport.builder()
                .id(15L)
                .zone(zone)
                .reporter(User.builder().id(1L).build())
                .reason(ZoneReportReason.OFFENSIVE)
                .status(ModerationStatus.PENDING)
                .build();
    }

    private Zone zone(Integer id) {
        return Zone.builder()
                .id(id)
                .region("성수")
                .description("사용자 등록 장소")
                .latitude(BigDecimal.valueOf(37.54))
                .longitude(BigDecimal.valueOf(127.05))
                .address("서울 성동구 연무장길 1")
                .build();
    }
}
