package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.dto.SupportRequestOperatorResponse;
import com.neogulmap.neogul_map.dto.SupportRequestStatusUpdateRequest;
import com.neogulmap.neogul_map.service.OperatorAccessGuard;
import com.neogulmap.neogul_map.service.SupportRequestService;
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
@RequestMapping("/operator/support/requests")
public class SupportOperatorController {

    private final SupportRequestService supportRequestService;
    private final OperatorAccessGuard operatorAccessGuard;

    @GetMapping
    public ResponseEntity<?> getActive(
            @RequestHeader(value = "X-Nugul-Operator-Key", required = false) String operatorKey
    ) {
        operatorAccessGuard.requireAccess(operatorKey);
        List<SupportRequestOperatorResponse> requests = supportRequestService.getActiveRequests();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "처리 중인 지원 요청 조회 성공",
                "data", Map.of("requests", requests, "count", requests.size())
        ));
    }

    @PatchMapping("/{requestId}")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long requestId,
            @RequestBody SupportRequestStatusUpdateRequest request,
            @RequestHeader(value = "X-Nugul-Operator-Key", required = false) String operatorKey
    ) {
        operatorAccessGuard.requireAccess(operatorKey);
        SupportRequestOperatorResponse response = supportRequestService.updateStatus(requestId, request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "지원 요청 처리 상태 변경 성공",
                "data", Map.of("request", response)
        ));
    }
}
