package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.NotFoundException;
import com.neogulmap.neogul_map.domain.Zone;
import com.neogulmap.neogul_map.dto.ZoneRequest;
import com.neogulmap.neogul_map.dto.ZoneResponse;
import com.neogulmap.neogul_map.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZoneService {

    private final ZoneRepository zoneRepository;

    @Transactional
    public ZoneResponse createZone(ZoneRequest request, MultipartFile image) throws IOException {
        // Check for duplicate address
        zoneRepository.findByAddress(request.getAddress()).ifPresent(zone -> {
            throw new BusinessBaseException(ErrorCode.ZONE_ALREADY_EXISTS);
        });

        // 이미지 처리
        if (image != null && !image.isEmpty()) {
            String imageFileName = processZoneImage(image);
            request.setImage(imageFileName);
        }

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
    public ZoneResponse updateZone(Integer zoneId, ZoneRequest request, MultipartFile image) throws IOException {
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

        // 이미지 처리
        if (image != null && !image.isEmpty()) {
            String imageFileName = processZoneImage(image);
            request.setImage(imageFileName);
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

    // 흡연구역 이미지 파일 처리하는 메서드
    private String processZoneImage(MultipartFile image) throws IOException {
        // 이미지 검증은 Controller에서 이미 완료됨 - 여기서는 저장만
        
        // 파일명 생성
        String originalFilename = image.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String fileName = "zone_" + timestamp + "_" + uniqueId + extension;
        
        // 파일 저장 - 절대 경로 사용
        Path uploadPath = Paths.get(System.getProperty("user.dir"), "uploads", "zones");
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        Path filePath = uploadPath.resolve(fileName);
        image.transferTo(filePath.toFile());
        
        log.info("흡연구역 이미지 업로드 성공: {}", fileName);
        return fileName;
    }
}
