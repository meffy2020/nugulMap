package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.config.security.PublicInsightRateLimiter;
import com.neogulmap.neogul_map.dto.MapInsightResponse;
import com.neogulmap.neogul_map.service.EventInsightService;
import com.neogulmap.neogul_map.service.HotplaceService;
import com.neogulmap.neogul_map.service.InsightStatusService;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/insights")
public class InsightController {

    private final HotplaceService hotplaceService;
    private final EventInsightService eventInsightService;
    private final InsightStatusService insightStatusService;
    private final PublicInsightRateLimiter publicInsightRateLimiter;

    @GetMapping("/map")
    public ResponseEntity<?> getMapInsights(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "hotplaceLimit", defaultValue = "8") int hotplaceLimit,
            @RequestParam(value = "eventLimit", defaultValue = "8") int eventLimit,
            @RequestParam(value = "minLat", required = false) Double minLat,
            @RequestParam(value = "maxLat", required = false) Double maxLat,
            @RequestParam(value = "minLng", required = false) Double minLng,
            @RequestParam(value = "maxLng", required = false) Double maxLng,
            HttpServletRequest request
    ) {
        PublicInsightRateLimiter.Decision rateLimit = publicInsightRateLimiter.tryAcquire(request);
        if (!rateLimit.allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(rateLimit.retryAfterSeconds()))
                    .body(Map.of(
                            "success", false,
                            "message", "요청이 많습니다. 잠시 후 다시 시도해 주세요."
                    ));
        }

        MapInsightResponse response = new MapInsightResponse(
                hotplaceService.getHotplaces(keyword, hotplaceLimit, minLat, maxLat, minLng, maxLng),
                eventInsightService.getEvents(keyword, eventLimit, minLat, maxLat, minLng, maxLng),
                insightStatusService.getStatus(),
                Instant.now()
        );
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "시즌2 지도 인사이트 조회 성공",
                "data", response
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "인사이트 공급자 상태 조회 성공",
                "data", insightStatusService.getStatus()
        ));
    }
}
