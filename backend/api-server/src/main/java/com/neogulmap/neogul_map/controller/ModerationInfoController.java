package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.domain.enums.ReviewReportReason;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/public/moderation")
public class ModerationInfoController {

    private final String supportEmail;

    public ModerationInfoController(
            @Value("${app.moderation.contact-email:}") String supportEmail) {
        this.supportEmail = supportEmail;
    }

    @GetMapping
    public ResponseEntity<?> getModerationInfo() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("supportRequestPath", "/api/public/support/requests");
        data.put("supportCategories", java.util.Arrays.stream(
                com.neogulmap.neogul_map.domain.enums.SupportRequestCategory.values()
        ).map(Enum::name).toList());
        data.put("reportPathTemplate", "/api/zones/{zoneId}/reviews/{reviewId}/reports");
        data.put("blockPathTemplate", "/api/users/{userId}/block");
        data.put("reportReasons", Arrays.stream(ReviewReportReason.values()).map(Enum::name).toList());
        data.put("supportEmailConfigured", supportEmail != null && !supportEmail.isBlank());
        if (supportEmail != null && !supportEmail.isBlank()) {
            data.put("supportEmail", supportEmail);
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "콘텐츠 신고 및 차단 안내",
                "data", data
        ));
    }
}
