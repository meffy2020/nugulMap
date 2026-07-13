package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.config.annotation.CurrentUser;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.dto.ZoneReviewRequest;
import com.neogulmap.neogul_map.dto.ZoneReviewResponse;
import com.neogulmap.neogul_map.dto.ReviewReportRequest;
import com.neogulmap.neogul_map.dto.ReviewReportResponse;
import com.neogulmap.neogul_map.service.ReviewModerationService;
import com.neogulmap.neogul_map.service.ZoneReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/zones/{zoneId}/reviews")
public class ZoneReviewController {

    private final ZoneReviewService zoneReviewService;
    private final ReviewModerationService reviewModerationService;

    @GetMapping
    public ResponseEntity<?> getReviews(
            @PathVariable("zoneId") Integer zoneId,
            @CurrentUser(required = false) User user
    ) {
        List<ZoneReviewResponse> reviews = zoneReviewService.getReviews(zoneId, user);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "리뷰 조회 성공",
                "data", Map.of(
                        "reviews", reviews,
                        "count", reviews.size()
                )
        ));
    }

    @PostMapping
    public ResponseEntity<?> createReview(@PathVariable("zoneId") Integer zoneId,
                                          @RequestBody ZoneReviewRequest request,
                                          @CurrentUser User user) {
        ZoneReviewResponse review = zoneReviewService.createReview(zoneId, request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "message", "리뷰 등록 성공",
                "data", Map.of("review", review)
        ));
    }

    @PostMapping("/{reviewId}/reports")
    public ResponseEntity<?> reportReview(
            @PathVariable("zoneId") Integer zoneId,
            @PathVariable("reviewId") Long reviewId,
            @RequestBody ReviewReportRequest request,
            @CurrentUser User user
    ) {
        ReviewReportResponse report = reviewModerationService.reportReview(zoneId, reviewId, request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "message", "신고가 접수되었습니다.",
                "data", Map.of("report", report)
        ));
    }
}
