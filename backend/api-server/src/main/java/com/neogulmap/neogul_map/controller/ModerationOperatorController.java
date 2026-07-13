package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.dto.ReviewReportOperatorResponse;
import com.neogulmap.neogul_map.dto.ZoneReportOperatorResponse;
import com.neogulmap.neogul_map.dto.ModerationDecisionRequest;
import com.neogulmap.neogul_map.dto.ModerationDecisionResponse;
import com.neogulmap.neogul_map.dto.ZoneSubmissionOperatorResponse;
import com.neogulmap.neogul_map.dto.ZonePublicationDecisionResponse;
import com.neogulmap.neogul_map.service.OperatorAccessGuard;
import com.neogulmap.neogul_map.service.ReviewModerationService;
import com.neogulmap.neogul_map.service.ZoneModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/operator/moderation/reports")
public class ModerationOperatorController {

    private final ReviewModerationService reviewModerationService;
    private final ZoneModerationService zoneModerationService;
    private final OperatorAccessGuard operatorAccessGuard;

    @GetMapping
    public ResponseEntity<?> getPending(
            @RequestHeader(value = "X-Nugul-Operator-Key", required = false) String operatorKey
    ) {
        operatorAccessGuard.requireAccess(operatorKey);
        List<ReviewReportOperatorResponse> reports = reviewModerationService.getPendingReports();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "대기 중인 리뷰 신고 조회 성공",
                "data", Map.of("reports", reports, "count", reports.size())
        ));
    }

    @GetMapping("/zones")
    public ResponseEntity<?> getPendingZoneReports(
            @RequestHeader(value = "X-Nugul-Operator-Key", required = false) String operatorKey
    ) {
        operatorAccessGuard.requireAccess(operatorKey);
        List<ZoneReportOperatorResponse> reports = zoneModerationService.getPendingReports();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "대기 중인 장소 신고 조회 성공",
                "data", Map.of("reports", reports, "count", reports.size())
        ));
    }

    @GetMapping("/zone-submissions")
    public ResponseEntity<?> getPendingZoneSubmissions(
            @RequestHeader(value = "X-Nugul-Operator-Key", required = false) String operatorKey
    ) {
        operatorAccessGuard.requireAccess(operatorKey);
        List<ZoneSubmissionOperatorResponse> submissions = zoneModerationService.getPendingSubmissions();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "대기 중인 장소 등록 검토 목록 조회 성공",
                "data", Map.of("submissions", submissions, "count", submissions.size())
        ));
    }

    @PatchMapping("/{reportId}")
    public ResponseEntity<?> decideReviewReport(
            @PathVariable Long reportId,
            @RequestBody ModerationDecisionRequest request,
            @RequestHeader(value = "X-Nugul-Operator-Key", required = false) String operatorKey
    ) {
        operatorAccessGuard.requireAccess(operatorKey);
        ModerationDecisionResponse decision = reviewModerationService.decideReport(reportId, request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "리뷰 신고 처리 성공",
                "data", Map.of("decision", decision)
        ));
    }

    @PatchMapping("/zones/{reportId}")
    public ResponseEntity<?> decideZoneReport(
            @PathVariable Long reportId,
            @RequestBody ModerationDecisionRequest request,
            @RequestHeader(value = "X-Nugul-Operator-Key", required = false) String operatorKey
    ) {
        operatorAccessGuard.requireAccess(operatorKey);
        ModerationDecisionResponse decision = zoneModerationService.decideReport(reportId, request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "장소 신고 처리 성공",
                "data", Map.of("decision", decision)
        ));
    }

    @PatchMapping("/zone-submissions/{zoneId}")
    public ResponseEntity<?> decideZoneSubmission(
            @PathVariable Integer zoneId,
            @RequestBody ModerationDecisionRequest request,
            @RequestHeader(value = "X-Nugul-Operator-Key", required = false) String operatorKey
    ) {
        operatorAccessGuard.requireAccess(operatorKey);
        ZonePublicationDecisionResponse decision = zoneModerationService.decideSubmission(zoneId, request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "장소 등록 검토 처리 성공",
                "data", Map.of("decision", decision)
        ));
    }
}
