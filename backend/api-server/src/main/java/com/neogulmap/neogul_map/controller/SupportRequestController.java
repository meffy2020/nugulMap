package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.dto.SupportRequestCreateRequest;
import com.neogulmap.neogul_map.dto.SupportRequestResponse;
import com.neogulmap.neogul_map.service.SupportRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/public/support/requests")
public class SupportRequestController {

    private final SupportRequestService supportRequestService;

    @PostMapping
    public ResponseEntity<?> create(@RequestBody SupportRequestCreateRequest request) {
        SupportRequestResponse response = supportRequestService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "message", "요청이 접수되었습니다.",
                "data", Map.of("request", response)
        ));
    }
}
