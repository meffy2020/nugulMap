package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.dto.MapInsightResponse;
import com.neogulmap.neogul_map.service.EventInsightService;
import com.neogulmap.neogul_map.service.HotplaceService;
import com.neogulmap.neogul_map.service.InsightStatusService;
import lombok.RequiredArgsConstructor;
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

    @GetMapping("/map")
    public ResponseEntity<?> getMapInsights(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "hotplaceLimit", defaultValue = "8") int hotplaceLimit,
            @RequestParam(value = "eventLimit", defaultValue = "8") int eventLimit,
            @RequestParam(value = "minLat", required = false) Double minLat,
            @RequestParam(value = "maxLat", required = false) Double maxLat,
            @RequestParam(value = "minLng", required = false) Double minLng,
            @RequestParam(value = "maxLng", required = false) Double maxLng
    ) {
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
}
