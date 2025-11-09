package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.domain.enums.ImageType;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ProfileImageProcessingException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ProfileImageRequiredException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ImageNotFoundException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ImageUploadException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException;
import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import java.io.File;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
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
@Service
public class ImageService {
    
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    
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
            
            // 파일명 생성
            String fileName = generateFileName(image, type);
            
            // 파일 저장
            saveImage(image, fileName, type);
            
            log.info("{} 이미지 업로드 성공: {} (크기: {} bytes)", 
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
        
        try {
            // profiles 디렉토리에서 먼저 찾기
            Path imagePath = Paths.get(System.getProperty("user.dir"), "uploads", "profiles", fileName);
            File imageFile = imagePath.toFile();
            
            if (!imageFile.exists()) {
                // zones 디렉토리에서 찾기
                imagePath = Paths.get(System.getProperty("user.dir"), "uploads", "zones", fileName);
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
        
        try {
            Path imagePath = Paths.get(System.getProperty("user.dir"), "uploads", type.getDirectory(), fileName);
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
        
        // 파일 타입 검사
        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ProfileImageProcessingException("이미지 파일만 업로드 가능합니다");
        }
        
        // 파일 확장자 검사
        String originalFilename = image.getOriginalFilename();
        if (originalFilename != null) {
            String extension = originalFilename.toLowerCase();
            if (!extension.contains(".")) {
                throw new ProfileImageProcessingException("파일 확장자가 필요합니다");
            }
            
            if (!extension.matches(".*\\.(jpg|jpeg|png|gif|webp)$")) {
                throw new ProfileImageProcessingException("지원하지 않는 이미지 형식입니다. (.jpg, .jpeg, .png, .gif, .webp만 가능)");
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
        Path uploadPath = Paths.get(System.getProperty("user.dir"), "uploads", type.getDirectory());
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        Path filePath = uploadPath.resolve(fileName);
        image.transferTo(filePath.toFile());
    }
}
