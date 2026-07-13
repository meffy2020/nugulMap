package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.config.annotation.CurrentUser;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.service.ReviewModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users/{userId}/block")
public class UserBlockController {

    private final ReviewModerationService reviewModerationService;

    @PostMapping
    public ResponseEntity<?> blockUser(
            @PathVariable("userId") Long userId,
            @CurrentUser User currentUser
    ) {
        reviewModerationService.blockUser(userId, currentUser);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "사용자를 차단했습니다.",
                "data", Map.of("blockedUserId", userId)
        ));
    }

    @DeleteMapping
    public ResponseEntity<?> unblockUser(
            @PathVariable("userId") Long userId,
            @CurrentUser User currentUser
    ) {
        reviewModerationService.unblockUser(userId, currentUser);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "사용자 차단을 해제했습니다.",
                "data", Map.of("unblockedUserId", userId)
        ));
    }
}
