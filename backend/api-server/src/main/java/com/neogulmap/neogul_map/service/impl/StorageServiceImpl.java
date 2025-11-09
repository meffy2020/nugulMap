package com.neogulmap.neogul_map.service.impl;

import com.neogulmap.neogul_map.service.StorageService;
import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.FileStorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service("localStorageService")
public class StorageServiceImpl implements StorageService {
    
    @Value("${app.upload.dir:uploads}")
    private String uploadDir;
    
    private static final String TEMP_PREFIX = "temp_";
    private static final String ZONE_PREFIX = "zone_";
    
    @Override
    public String saveTemp(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException(ErrorCode.FILE_UPLOAD_ERROR, "파일이 비어있습니다.");
        }
        
        try {
            // 임시 파일명 생성
            String tempFileName = generateTempFileName(file.getOriginalFilename());
            
            // 임시 디렉토리 생성
            Path tempDir = Paths.get(uploadDir, "temp", "zones");
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
            }
            
            // 임시 파일 저장
            Path tempFilePath = tempDir.resolve(tempFileName);
            file.transferTo(tempFilePath.toFile());
            
            log.info("임시 파일 저장 완료: {}", tempFileName);
            return tempFileName;
        } catch (IOException e) {
            log.error("임시 파일 저장 중 I/O 오류 발생: {}", e.getMessage(), e);
            throw new FileStorageException(ErrorCode.FILE_UPLOAD_ERROR, "임시 파일 저장 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void confirm(String tempName, String finalName) {
        try {
            Path tempDir = Paths.get(uploadDir, "temp", "zones");
            Path finalDir = Paths.get(uploadDir, "zones");
            
            // 최종 디렉토리 생성
            if (!Files.exists(finalDir)) {
                Files.createDirectories(finalDir);
            }
            
            Path tempFilePath = tempDir.resolve(tempName);
            Path finalFilePath = finalDir.resolve(finalName);
            
            // 임시 파일이 존재하는지 확인
            if (!Files.exists(tempFilePath)) {
                log.warn("임시 파일이 존재하지 않습니다: {}", tempName);
                return;
            }
            
            // 임시 파일을 최종 위치로 이동
            Files.move(tempFilePath, finalFilePath);
            
            // 임시 디렉토리 정리 (비어있으면 삭제)
            try {
                if (Files.list(tempDir).findAny().isEmpty()) {
                    Files.deleteIfExists(tempDir);
                }
            } catch (IOException e) {
                log.warn("임시 디렉토리 정리 실패: {}", e.getMessage());
            }
            
            log.info("파일 확정 완료: {} -> {}", tempName, finalName);
        } catch (IOException e) {
            log.error("파일 확정 중 I/O 오류 발생: {}", e.getMessage(), e);
            throw new FileStorageException(ErrorCode.FILE_UPLOAD_ERROR, "파일 확정 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void deleteQuietly(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return;
        }
        
        try {
            // 최종 파일 삭제 시도
            Path finalFilePath = Paths.get(uploadDir, "zones", fileName);
            if (Files.exists(finalFilePath)) {
                Files.deleteIfExists(finalFilePath);
                log.info("파일 삭제 완료: {}", fileName);
            }
            
            // 임시 파일 삭제 시도
            Path tempFilePath = Paths.get(uploadDir, "temp", "zones", fileName);
            if (Files.exists(tempFilePath)) {
                Files.deleteIfExists(tempFilePath);
                log.info("임시 파일 삭제 완료: {}", fileName);
            }
        } catch (IOException e) {
            log.warn("파일 삭제 실패: {} - {}", fileName, e.getMessage());
        }
    }
    
    @Override
    public boolean exists(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }
        
        Path finalFilePath = Paths.get(uploadDir, "zones", fileName);
        return Files.exists(finalFilePath);
    }
    
    /**
     * 임시 파일명 생성
     */
    private String generateTempFileName(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        
        return TEMP_PREFIX + ZONE_PREFIX + timestamp + "_" + uniqueId + extension;
    }
    
    /**
     * 최종 파일명 생성
     */
    public String generateFinalFileName(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        
        return ZONE_PREFIX + timestamp + "_" + uniqueId + extension;
    }
}
