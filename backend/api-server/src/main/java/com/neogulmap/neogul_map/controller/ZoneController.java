package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.dto.ZoneRequest;
import com.neogulmap.neogul_map.dto.ZoneResponse;
import com.neogulmap.neogul_map.service.ZoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/zones")
public class ZoneController {

    private final ZoneService zoneService;

    @PostMapping
    public ResponseEntity<ZoneResponse> createZone(@RequestBody @Valid ZoneRequest request) {
        ZoneResponse response = zoneService.createZone(request);
        return ResponseEntity.status(201) // 201 Created
                .header("X-Message", "흡연구역 생성 성공")
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ZoneResponse> getZone(@PathVariable("id") Integer id) {
        ZoneResponse response = zoneService.getZone(id);
        return ResponseEntity.ok()
                .header("X-Message", "흡연구역 조회 성공")
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<ZoneResponse>> getAllZones() {
        List<ZoneResponse> response = zoneService.getAllZones();
        return ResponseEntity.ok()
                .header("X-Message", "모든 흡연구역 조회 성공")
                .body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ZoneResponse> updateZone(@PathVariable("id") Integer id, @RequestBody @Valid ZoneRequest request) {
        ZoneResponse response = zoneService.updateZone(id, request);
        return ResponseEntity.ok()
                .header("X-Message", "흡연구역 업데이트 성공")
                .body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteZone(@PathVariable("id") Integer id) {
        zoneService.deleteZone(id);
        return ResponseEntity.ok()
                .header("X-Message", "흡연구역 삭제 성공")
                .build();
    }
}
