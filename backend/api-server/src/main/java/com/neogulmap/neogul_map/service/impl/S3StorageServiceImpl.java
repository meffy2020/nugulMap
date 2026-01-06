package com.neogulmap.neogul_map.service.impl;

import com.neogulmap.neogul_map.domain.enums.ImageType;
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
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service("s3StorageService")
@RequiredArgsConstructor
public class S3StorageServiceImpl implements StorageService {
    
    public final S3Client s3Client;
    
    @Value("${cloud.aws.s3.bucket}")
    public String bucketName;
    
    @Value("${cloud.aws.s3.endpoint:#{null}}")
    public String endpoint;
    
    @Value("${app.s3.temp-prefix:temp/}")
    private String tempPrefix;
    
    @Value("${app.s3.profile-prefix:profiles/}")
    public String profilePrefix;
    
    @Value("${app.s3.zone-prefix:zones/}")
    public String zonePrefix;
    
    private static final String TEMP_PREFIX = "temp_";
    
    /**
     * 이미지를 S3에 직접 저장 (ImageService에서 사용)
     * @param file 업로드할 파일
     * @param imageType 이미지 타입 (PROFILE, ZONE)
     * @return 저장된 파일명
     */
    public String saveImage(MultipartFile file, ImageType imageType) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException(ErrorCode.FILE_UPLOAD_ERROR, "파일이 비어있습니다.");
        }
        
        // 파일명 생성
        String fileName = generateFileName(file.getOriginalFilename(), imageType);
        String s3Key = getS3Key(fileName, imageType);
        
        try {
            // S3에 파일 업로드
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();
            
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(
                    file.getInputStream(), file.getSize()));
            
            log.info("S3 이미지 저장 완료: {} (타입: {})", fileName, imageType);
            return fileName;
            
        } catch (Exception e) {
            log.error("S3 이미지 저장 실패: {}", fileName, e);
            throw new FileStorageException(ErrorCode.S3_UPLOAD_ERROR, "S3 이미지 저장 실패", e);
        }
    }
    
    /**
     * 이미지 파일 URL 생성 (Nginx 프록시 또는 직접 접근)
     * @param fileName 파일명
     * @param imageType 이미지 타입
     * @return 이미지 URL
     */
    public String getImageUrl(String fileName, ImageType imageType) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return null;
        }
        
        // MinIO인 경우 Nginx를 통해 프록시하도록 설정
        if (endpoint != null && !endpoint.isEmpty()) {
            // Nginx를 통해 접근: /api/images/{filename}
            return "/api/images/" + fileName;
        } else {
            // AWS S3인 경우 presigned URL 생성
            try {
                String s3Key = getS3Key(fileName, imageType);
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .build();
                
                S3Presigner presigner = S3Presigner.create();
                PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(
                        GetObjectPresignRequest.builder()
                                .signatureDuration(Duration.ofHours(1))
                                .getObjectRequest(getObjectRequest)
                                .build());
                
                return presignedRequest.url().toString();
            } catch (Exception e) {
                log.warn("Presigned URL 생성 실패: {} - {}", fileName, e.getMessage());
                return null;
            }
        }
    }
    
    /**
     * 이미지 파일 존재 확인
     * @param fileName 파일명
     * @param imageType 이미지 타입
     * @return 존재 여부
     */
    public boolean exists(String fileName, ImageType imageType) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }
        
        try {
            String s3Key = getS3Key(fileName, imageType);
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
     * 이미지 파일 삭제
     * @param fileName 파일명
     * @param imageType 이미지 타입
     */
    public void deleteImage(String fileName, ImageType imageType) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return;
        }
        
        try {
            String s3Key = getS3Key(fileName, imageType);
            deleteS3Object(s3Key);
            log.info("S3 이미지 삭제 완료: {} (타입: {})", fileName, imageType);
        } catch (Exception e) {
            log.warn("S3 이미지 삭제 실패: {} - {}", fileName, e.getMessage());
        }
    }
    
    @Override
    public String saveTemp(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException(ErrorCode.FILE_UPLOAD_ERROR, "파일이 비어있습니다.");
        }
        
        // 임시 파일명 생성 (기본적으로 ZONE 타입으로 처리)
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
            // profiles와 zones 모두에서 삭제 시도
            deleteS3Object(profilePrefix + fileName);
            deleteS3Object(zonePrefix + fileName);
            deleteS3Object(tempPrefix + fileName);
            
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
        
        // profiles와 zones 모두 확인
        try {
            String profileKey = profilePrefix + fileName;
            HeadObjectRequest profileRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(profileKey)
                    .build();
            s3Client.headObject(profileRequest);
            return true;
        } catch (NoSuchKeyException e) {
            // profiles에 없으면 zones 확인
            try {
                String zoneKey = zonePrefix + fileName;
                HeadObjectRequest zoneRequest = HeadObjectRequest.builder()
                        .bucket(bucketName)
                        .key(zoneKey)
                        .build();
                s3Client.headObject(zoneRequest);
                return true;
            } catch (NoSuchKeyException ex) {
                return false;
            }
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
     * S3 키 경로 생성
     */
    private String getS3Key(String fileName, ImageType imageType) {
        String prefix = imageType == ImageType.PROFILE ? profilePrefix : zonePrefix;
        return prefix + fileName;
    }
    
    /**
     * 파일명 생성
     */
    private String generateFileName(String originalFilename, ImageType imageType) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        return imageType.getPrefix() + timestamp + "_" + uniqueId + extension;
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
        
        return TEMP_PREFIX + timestamp + "_" + uniqueId + extension;
    }
}
