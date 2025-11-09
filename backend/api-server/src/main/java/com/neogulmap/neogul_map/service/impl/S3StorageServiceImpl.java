package com.neogulmap.neogul_map.service.impl;

import com.neogulmap.neogul_map.service.StorageService;
import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.FileStorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
// @Service("s3StorageService") // AWS SDK 의존성 문제로 임시 비활성화
@RequiredArgsConstructor
public class S3StorageServiceImpl implements StorageService {
    
    private final S3Client s3Client;
    
    @Value("${app.s3.bucket-name}")
    private String bucketName;
    
    @Value("${app.s3.temp-prefix:temp/}")
    private String tempPrefix;
    
    @Value("${app.s3.zone-prefix:zones/}")
    private String zonePrefix;
    
    private static final String TEMP_PREFIX = "temp_";
    private static final String ZONE_PREFIX = "zone_";
    
    @Override
    public String saveTemp(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException(ErrorCode.FILE_UPLOAD_ERROR, "파일이 비어있습니다.");
        }
        
        // 임시 파일명 생성
        String tempFileName = generateTempFileName(file.getOriginalFilename());
        String s3Key = tempPrefix + tempFileName;
        
        try {
            // S3에 임시 파일 업로드
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();
            
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(
                    file.getInputStream(), file.getSize()));
            
            log.info("S3 임시 파일 저장 완료: {}", tempFileName);
            return tempFileName;
            
        } catch (Exception e) {
            log.error("S3 임시 파일 저장 실패: {}", tempFileName, e);
            throw new FileStorageException(ErrorCode.S3_UPLOAD_ERROR, "S3 임시 파일 저장 실패", e);
        }
    }
    
    @Override
    public void confirm(String tempName, String finalName) {
        String tempS3Key = tempPrefix + tempName;
        String finalS3Key = zonePrefix + finalName;
        
        try {
            // S3에서 임시 파일을 최종 위치로 복사
            CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                    .sourceBucket(bucketName)
                    .sourceKey(tempS3Key)
                    .destinationBucket(bucketName)
                    .destinationKey(finalS3Key)
                    .build();
            
            s3Client.copyObject(copyRequest);
            
            // 임시 파일 삭제
            deleteS3Object(tempS3Key);
            
            log.info("S3 파일 확정 완료: {} -> {}", tempName, finalName);
            
        } catch (Exception e) {
            log.error("S3 파일 확정 실패: {} -> {}", tempName, finalName, e);
            throw new FileStorageException(ErrorCode.S3_UPLOAD_ERROR, "S3 파일 확정 실패", e);
        }
    }
    
    @Override
    public void deleteQuietly(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return;
        }
        
        try {
            // 최종 파일 삭제 시도
            String finalS3Key = zonePrefix + fileName;
            deleteS3Object(finalS3Key);
            
            // 임시 파일 삭제 시도
            String tempS3Key = tempPrefix + fileName;
            deleteS3Object(tempS3Key);
            
            log.info("S3 파일 삭제 완료: {}", fileName);
            
        } catch (Exception e) {
            log.warn("S3 파일 삭제 실패: {} - {}", fileName, e.getMessage());
        }
    }
    
    @Override
    public boolean exists(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }
        
        try {
            String s3Key = zonePrefix + fileName;
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
            
            s3Client.headObject(headRequest);
            return true;
            
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.warn("S3 파일 존재 확인 실패: {} - {}", fileName, e.getMessage());
            return false;
        }
    }
    
    /**
     * S3 객체 삭제
     */
    private void deleteS3Object(String s3Key) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
            
            s3Client.deleteObject(deleteRequest);
            
        } catch (Exception e) {
            log.warn("S3 객체 삭제 실패: {} - {}", s3Key, e.getMessage());
        }
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
    
    /**
     * S3 파일 URL 생성 (CDN 또는 직접 접근용)
     */
    public String generateFileUrl(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return null;
        }
        
        String s3Key = zonePrefix + fileName;
        return String.format("https://%s.s3.amazonaws.com/%s", bucketName, s3Key);
    }
}
