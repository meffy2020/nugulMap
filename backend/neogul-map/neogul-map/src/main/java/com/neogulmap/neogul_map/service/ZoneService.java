package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.NotFoundException;
import com.neogulmap.neogul_map.domain.Zone;
import com.neogulmap.neogul_map.dto.ZoneRequest;
import com.neogulmap.neogul_map.dto.ZoneResponse;
import com.neogulmap.neogul_map.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ZoneService {

    private final ZoneRepository zoneRepository;

    @Transactional
    public ZoneResponse createZone(ZoneRequest request) {
        // Check for duplicate address
        zoneRepository.findByAddress(request.getAddress()).ifPresent(zone -> {
            throw new BusinessBaseException(ErrorCode.ZONE_ALREADY_EXISTS);
        });

        Zone zone = request.toEntity();
        Zone savedZone = zoneRepository.save(zone);
        return ZoneResponse.from(savedZone);
    }

    @Transactional(readOnly = true)
    public ZoneResponse getZone(Integer zoneId) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ZONE_NOT_FOUND));
        return ZoneResponse.from(zone);
    }

    @Transactional(readOnly = true)
    public List<ZoneResponse> getAllZones() {
        return zoneRepository.findAll().stream()
                .map(ZoneResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public ZoneResponse updateZone(Integer zoneId, ZoneRequest request) {
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ZONE_NOT_FOUND));

        // Check if the new address is already taken by another zone
        if (request.getAddress() != null) {
            zoneRepository.findByAddress(request.getAddress()).ifPresent(existingZone -> {
                if (!existingZone.getId().equals(zoneId)) {
                    throw new BusinessBaseException(ErrorCode.ZONE_ALREADY_EXISTS);
                }
            });
        }

        zone.update(request);
        return ZoneResponse.from(zone); // The changes are automatically saved by the transaction
    }

    @Transactional
    public void deleteZone(Integer zoneId) {
        if (!zoneRepository.existsById(zoneId)) {
            throw new NotFoundException(ErrorCode.ZONE_NOT_FOUND);
        }
        zoneRepository.deleteById(zoneId);
    }
}
