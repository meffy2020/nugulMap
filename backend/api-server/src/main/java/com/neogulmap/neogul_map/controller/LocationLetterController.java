package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.config.annotation.CurrentUser;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.dto.LocationLetterCreateRequest;
import com.neogulmap.neogul_map.dto.LocationLetterNearbyResponse;
import com.neogulmap.neogul_map.dto.LocationLetterLikeResponse;
import com.neogulmap.neogul_map.dto.LocationLetterReportResponse;
import com.neogulmap.neogul_map.dto.LocationLetterResponse;
import com.neogulmap.neogul_map.dto.ReviewReportRequest;
import com.neogulmap.neogul_map.service.LocationLetterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.Map;

@RestController
@ConditionalOnProperty(name = "app.discovery.enabled", havingValue = "true")
@RequiredArgsConstructor
@RequestMapping("/location-letters")
public class LocationLetterController {

    private final LocationLetterService letterService;

    @GetMapping
    public ResponseEntity<?> getNearby(
            @RequestParam("lat") Double latitude,
            @RequestParam("lng") Double longitude,
            @RequestParam(value = "limit", required = false) Integer limit,
            @CurrentUser(required = false) User currentUser
    ) {
        LocationLetterNearbyResponse data = letterService.getNearby(latitude, longitude, limit, currentUser);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "이곳의 편지를 불러왔습니다.",
                "data", data
        ));
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestBody LocationLetterCreateRequest request,
            @CurrentUser User currentUser
    ) {
        LocationLetterResponse letter = letterService.create(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "message", "이곳에 편지를 남겼습니다.",
                "data", Map.of("letter", letter)
        ));
    }

    @DeleteMapping("/{letterId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long letterId,
            @CurrentUser User currentUser
    ) {
        letterService.delete(letterId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{letterId}/like")
    public ResponseEntity<?> like(
            @PathVariable Long letterId,
            @CurrentUser User currentUser
    ) {
        LocationLetterLikeResponse result = letterService.like(letterId, currentUser);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "좋아요를 남겼습니다.",
                "data", result
        ));
    }

    @DeleteMapping("/{letterId}/like")
    public ResponseEntity<?> unlike(
            @PathVariable Long letterId,
            @CurrentUser User currentUser
    ) {
        LocationLetterLikeResponse result = letterService.unlike(letterId, currentUser);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "좋아요를 취소했습니다.",
                "data", result
        ));
    }

    @PostMapping("/{letterId}/reports")
    public ResponseEntity<?> report(
            @PathVariable Long letterId,
            @RequestBody ReviewReportRequest request,
            @CurrentUser User currentUser
    ) {
        LocationLetterReportResponse report = letterService.report(letterId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "message", "신고가 접수되었습니다.",
                "data", Map.of("report", report)
        ));
    }
}
