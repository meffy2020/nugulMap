package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.domain.enums.ImageType;
import com.neogulmap.neogul_map.service.ImageService;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ImageNotFoundException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ImageUploadException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ProfileImageProcessingException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ProfileImageRequiredException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException;
import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    /**
     * 이미지 조회
     */
    @GetMapping("/{filename}")
    public ResponseEntity<?> getImage(@PathVariable String filename) {
        Resource resource = imageService.getImage(filename);
        String contentType = imageService.getContentType(filename);
        
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .header("X-Image-Filename", filename)
                .body(resource);
    }

    /**
     * 이미지 업로드 (공통)
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadImage(
            @RequestParam("image") MultipartFile image,
            @RequestParam("type") String type) {
        
        // 타입 검증
        ImageType imageType;
        try {
            imageType = ImageType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException(ErrorCode.VALIDATION_ERROR, 
                    "지원하지 않는 이미지 타입입니다. PROFILE 또는 ZONE을 사용하세요");
        }
        
        // 이미지 처리
        String fileName = imageService.processImage(image, imageType);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                    "success", true,
                    "message", "이미지 업로드 성공",
                    "data", Map.of(
                        "filename", fileName,
                        "type", imageType.name(),
                        "size", image.getSize(),
                        "originalName", image.getOriginalFilename()
                    )
                ));
    }

    /**
     * 이미지 삭제
     */
    @DeleteMapping("/{filename}")
    public ResponseEntity<?> deleteImage(
            @PathVariable String filename,
            @RequestParam("type") String type) {
        
        // 타입 검증
        ImageType imageType;
        try {
            imageType = ImageType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException(ErrorCode.VALIDATION_ERROR, 
                    "지원하지 않는 이미지 타입입니다. PROFILE 또는 ZONE을 사용하세요");
        }
        
        // 이미지 삭제
        imageService.deleteImage(filename, imageType);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "이미지 삭제 성공",
            "data", Map.of(
                "filename", filename,
                "type", imageType.name()
            )
        ));
    }
    
}

