package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.domain.enums.ImageType;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ProfileImageProcessingException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ProfileImageRequiredException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ImageNotFoundException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ImageUploadException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException;
import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.service.impl.S3StorageServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {
    
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    
    private final StorageService storageService;
    
    @Value("${app.storage.type:local}")
    private String storageType;
    
    /**
     * 업로드 디렉토리 경로 (기본값: uploads)
     * 환경 변수 UPLOAD_DIR 또는 설정 파일 app.image.upload-base-path로 오버라이드 가능
     */
    @Value("${app.image.upload-base-path:uploads}")
    private String uploadBasePath;
    
    /**
     * 이미지 파일을 처리하고 저장합니다.
     * 
     * @param image 업로드할 이미지 파일
     * @param type 이미지 타입 (PROFILE, ZONE)
     * @return 저장된 파일명
     * @throws ImageUploadException 이미지 업로드 중 오류 발생
     */
    public String processImage(MultipartFile image, ImageType type) throws ImageUploadException {
        try {
            // 파일 유효성 검사
            validateImage(image);
            
            // S3 저장소인 경우
            if ("s3".equalsIgnoreCase(storageType) && storageService instanceof S3StorageServiceImpl) {
                S3StorageServiceImpl s3StorageService = (S3StorageServiceImpl) storageService;
                String fileName = s3StorageService.saveImage(image, type);
                log.info("{} 이미지 업로드 성공 (S3): {} (크기: {} bytes)", 
                        type.name(), fileName, image.getSize());
                return fileName;
            }
            
            // 로컬 저장소인 경우
            String fileName = generateFileName(image, type);
            saveImage(image, fileName, type);
            
            log.info("{} 이미지 업로드 성공 (로컬): {} (크기: {} bytes)", 
                    type.name(), fileName, image.getSize());
            return fileName;
            
        } catch (ProfileImageRequiredException | ProfileImageProcessingException e) {
            // 이미 정의된 예외는 그대로 전파
            throw e;
        } catch (IOException e) {
            log.error("{} 이미지 저장 중 I/O 오류 발생: {}", type.name(), e.getMessage(), e);
            throw new ImageUploadException("이미지 저장 중 오류가 발생했습니다: " + e.getMessage(), e);
        } catch (SecurityException e) {
            log.error("{} 이미지 저장 중 보안 오류 발생: {}", type.name(), e.getMessage(), e);
            throw new ImageUploadException("이미지 저장 권한이 없습니다: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            log.error("{} 이미지 처리 중 잘못된 인수 오류: {}", type.name(), e.getMessage(), e);
            throw new ValidationException(ErrorCode.VALIDATION_ERROR, "이미지 처리 중 잘못된 인수가 전달되었습니다: " + e.getMessage());
        } catch (Exception e) {
            log.error("{} 이미지 처리 중 예상치 못한 오류 발생: {}", type.name(), e.getMessage(), e);
            throw new ImageUploadException("이미지 처리 중 예상치 못한 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * 이미지 파일을 조회합니다.
     * 
     * @param fileName 파일명
     * @return 이미지 리소스
     * @throws ImageNotFoundException 이미지 파일을 찾을 수 없는 경우
     */
    public Resource getImage(String fileName) throws ImageNotFoundException {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new ImageNotFoundException("파일명이 제공되지 않았습니다");
        }
        
        // S3 저장소인 경우
        if ("s3".equalsIgnoreCase(storageType) && storageService instanceof S3StorageServiceImpl) {
            S3StorageServiceImpl s3StorageService = (S3StorageServiceImpl) storageService;
            
            // 파일명에서 타입 추론 (profile 또는 zone으로 시작하는지 확인)
            ImageType imageType = fileName.startsWith("profile") ? ImageType.PROFILE : ImageType.ZONE;
            
            // 파일 존재 확인
            if (!s3StorageService.exists(fileName, imageType)) {
                // 다른 타입으로도 확인
                ImageType otherType = imageType == ImageType.PROFILE ? ImageType.ZONE : ImageType.PROFILE;
                if (!s3StorageService.exists(fileName, otherType)) {
                    log.warn("S3에서 이미지 파일을 찾을 수 없습니다: {}", fileName);
                    throw new ImageNotFoundException("이미지 파일을 찾을 수 없습니다: " + fileName);
                }
                imageType = otherType;
            }
            
            try {
                // S3에서 파일 다운로드
                String s3Key = imageType == ImageType.PROFILE ? 
                    s3StorageService.profilePrefix + fileName : 
                    s3StorageService.zonePrefix + fileName;
                
                ResponseInputStream<GetObjectResponse> s3Object = s3StorageService.s3Client.getObject(
                    software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                        .bucket(s3StorageService.bucketName)
                        .key(s3Key)
                        .build()
                );
                
                return new InputStreamResource(s3Object);
            } catch (Exception e) {
                log.error("S3 이미지 조회 실패: {}", fileName, e);
                throw new ImageNotFoundException("이미지 파일을 조회할 수 없습니다: " + fileName);
            }
        }
        
        // 로컬 저장소인 경우
        try {
            // profiles 디렉토리에서 먼저 찾기
            Path imagePath = getUploadPath("profiles", fileName);
            File imageFile = imagePath.toFile();
            
            if (!imageFile.exists()) {
                // zones 디렉토리에서 찾기
                imagePath = getUploadPath("zones", fileName);
                imageFile = imagePath.toFile();
            }
            
            if (!imageFile.exists()) {
                log.warn("이미지 파일을 찾을 수 없습니다: {}", fileName);
                throw new ImageNotFoundException("이미지 파일을 찾을 수 없습니다: " + fileName);
            }
            
            if (!imageFile.canRead()) {
                log.warn("이미지 파일을 읽을 수 없습니다: {}", fileName);
                throw new ImageNotFoundException("이미지 파일을 읽을 수 없습니다: " + fileName);
            }
            
            return new FileSystemResource(imageFile);
            
        } catch (SecurityException e) {
            log.error("이미지 파일 접근 권한 오류: {}", fileName, e);
            throw new ImageNotFoundException("이미지 파일에 접근할 권한이 없습니다: " + fileName);
        }
    }
    
    /**
     * 이미지 파일을 삭제합니다.
     * 
     * @param fileName 삭제할 파일명
     * @param type 이미지 타입
     */
    public void deleteImage(String fileName, ImageType type) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return;
        }
        
        // S3 저장소인 경우
        if ("s3".equalsIgnoreCase(storageType) && storageService instanceof S3StorageServiceImpl) {
            S3StorageServiceImpl s3StorageService = (S3StorageServiceImpl) storageService;
            s3StorageService.deleteImage(fileName, type);
            return;
        }
        
        // 로컬 저장소인 경우
        try {
            Path imagePath = getUploadPath(type.getDirectory(), fileName);
            if (Files.exists(imagePath)) {
                Files.delete(imagePath);
                log.info("{} 이미지 삭제 성공: {}", type.name(), fileName);
            }
        } catch (IOException e) {
            log.error("{} 이미지 삭제 중 오류 발생: {}", type.name(), e.getMessage());
        }
    }
    
    /**
     * 파일의 Content-Type을 반환합니다.
     * 
     * @param fileName 파일명
     * @return Content-Type
     */
    public String getContentType(String fileName) {
        String extension = fileName.toLowerCase();
        if (extension.endsWith(".jpg") || extension.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (extension.endsWith(".png")) {
            return "image/png";
        } else if (extension.endsWith(".gif")) {
            return "image/gif";
        } else if (extension.endsWith(".webp")) {
            return "image/webp";
        } else {
            return "application/octet-stream";
        }
    }
    
    /**
     * 이미지 파일 유효성을 검사합니다.
     */
    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ProfileImageRequiredException();
        }
        
        // 파일 크기 검사
        if (image.getSize() > MAX_FILE_SIZE) {
            throw new ProfileImageProcessingException("이미지 크기는 10MB 이하여야 합니다");
        }
        
        // 파일 타입 검사 (PNG, JPG만 허용)
        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ProfileImageProcessingException("이미지 파일만 업로드 가능합니다");
        }
        
        // PNG, JPG만 허용
        if (!contentType.equals("image/png") && 
            !contentType.equals("image/jpeg") && 
            !contentType.equals("image/jpg")) {
            throw new ProfileImageProcessingException("PNG 또는 JPG(JPEG) 형식의 이미지만 업로드 가능합니다");
        }
        
        // 파일 확장자 검사 (PNG, JPG만 허용)
        String originalFilename = image.getOriginalFilename();
        if (originalFilename != null) {
            String extension = originalFilename.toLowerCase();
            if (!extension.contains(".")) {
                throw new ProfileImageProcessingException("파일 확장자가 필요합니다");
            }
            
            if (!extension.matches(".*\\.(jpg|jpeg|png)$")) {
                throw new ProfileImageProcessingException("지원하지 않는 이미지 형식입니다. PNG 또는 JPG(JPEG) 형식만 가능합니다.");
            }
        } else {
            throw new ProfileImageProcessingException("파일명이 필요합니다");
        }
        
        log.info("이미지 검증 성공: {} (크기: {} bytes, 타입: {})", 
                originalFilename, image.getSize(), contentType);
    }
    
    /**
     * 고유한 파일명을 생성합니다.
     */
    private String generateFileName(MultipartFile image, ImageType type) {
        String originalFilename = image.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        return type.getPrefix() + timestamp + "_" + uniqueId + extension;
    }
    
    /**
     * 이미지 파일을 저장합니다.
     */
    private void saveImage(MultipartFile image, String fileName, ImageType type) throws IOException {
        Path uploadPath = getUploadPath(type.getDirectory(), null);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            log.info("업로드 디렉토리 생성: {}", uploadPath);
        }
        
        Path filePath = uploadPath.resolve(fileName);
        image.transferTo(filePath.toFile());
        log.debug("이미지 저장 완료: {}", filePath);
    }
    
    /**
     * 업로드 경로를 반환합니다.
     * 
     * @param subDirectory 하위 디렉토리 (profiles, zones 등)
     * @param fileName 파일명 (null이면 디렉토리 경로만 반환)
     * @return 업로드 경로
     */
    private Path getUploadPath(String subDirectory, String fileName) {
        // uploadBasePath가 절대 경로인지 확인
        Path basePath;
        if (Paths.get(uploadBasePath).isAbsolute()) {
            // 절대 경로인 경우 그대로 사용
            basePath = Paths.get(uploadBasePath);
        } else {
            // 상대 경로인 경우 현재 작업 디렉토리 기준
            basePath = Paths.get(System.getProperty("user.dir"), uploadBasePath);
        }
        
        Path fullPath = basePath.resolve(subDirectory);
        if (fileName != null) {
            fullPath = fullPath.resolve(fileName);
        }
        
        return fullPath;
    }
}
