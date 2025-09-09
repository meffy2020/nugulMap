package com.neogulmap.neogul_map.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import java.lang.Long;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.Map;

import com.neogulmap.neogul_map.dto.UserRequest;
import com.neogulmap.neogul_map.dto.UserResponse;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.service.UserService;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ProfileImageRequiredException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ProfileImageProcessingException;

import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@RequiredArgsConstructor
@RestController
public class UserController {
    private final UserService userService;

    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable("id") Long id) {
        User user = userService.getUser(id);
        return ResponseEntity.ok()
                .header("X-Message", "사용자 조회 성공")
                .body(UserResponse.from(user));
    }

    @PostMapping(value = "/users", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> createUser(
            @RequestPart("userData") String userData,
            @RequestPart(value = "profileImage", required = false) MultipartFile profileImage) throws IOException {
        
        // JSON 데이터를 UserRequest로 파싱
        UserRequest userRequest = parseUserRequest(userData);
        
        // 1단계: OAuth 정보로 기본 사용자 생성 (프로필 이미지 없이)
        UserResponse userResponse = userService.createUser(userRequest);
        
        // 2단계: 프로필 이미지가 있으면 설정, 없으면 기본값 유지
        String imageFileName = null;
        if (profileImage != null && !profileImage.isEmpty()) {
            imageFileName = processImage(profileImage);
            userService.updateProfileImage(userResponse.getId(), imageFileName);
        }
        
        // 3단계: 완성된 사용자 정보 반환
        User updatedUser = userService.getUser(userResponse.getId());
        String message = imageFileName != null ? 
            "OAuth 로그인 및 프로필 설정 완료" : 
            "OAuth 로그인 완료 (기본 이미지 사용)";
            
        return ResponseEntity.ok(Map.of(
            "message", message,
            "user", UserResponse.from(updatedUser)
        ));
    }

    @PutMapping(value = "/users/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable("id") Long id,
            @RequestPart("userData") String userData,
            @RequestPart(value = "profileImage", required = false) MultipartFile profileImage) throws IOException {
        
        // JSON 데이터를 UserRequest로 파싱
        UserRequest userRequest = parseUserRequest(userData);
        
        // 1단계: 프로필 이미지 처리 (있으면 업데이트, 없으면 기본값 유지)
        String imageFileName = null;
        if (profileImage != null && !profileImage.isEmpty()) {
            imageFileName = processImage(profileImage);
            userService.updateProfileImage(id, imageFileName);
        }
        
        // 2단계: 사용자 정보 업데이트 (닉네임 등)
        UserResponse userResponse = userService.updateUser(id, userRequest);
        
        // 응답 메시지 구성
        String nicknameInfo = userRequest.getNickname() != null ? "닉네임: " + userRequest.getNickname() : "";
        String message = "프로필 정보 업데이트 완료";
        if (!nicknameInfo.isEmpty()) {
            if (imageFileName != null) {
                message += " (" + nicknameInfo + ", 이미지: " + imageFileName + ")";
            } else {
                message += " (" + nicknameInfo + ", 기본 이미지 유지)";
            }
        } else {
            if (imageFileName != null) {
                message += " (이미지: " + imageFileName + ")";
            } else {
                message += " (기본 이미지 유지)";
            }
        }
        
        return ResponseEntity.ok(Map.of(
            "message", message,
            "user", userResponse
        ));
    }

    @PutMapping(value = "/users/{id}/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> updateProfileImage(
            @PathVariable("id") Long id,
            @RequestPart("profileImage") MultipartFile profileImage) throws IOException {
        
        if (profileImage == null || profileImage.isEmpty()) {
            throw new ProfileImageRequiredException();
        }
        
        String imageFileName = processImage(profileImage);
        userService.updateProfileImage(id, imageFileName);
        
        return ResponseEntity.ok(Map.of(
            "message", "프로필 이미지가 업데이트되었습니다.",
            "profileImage", imageFileName
        ));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable("id") Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(Map.of("message", "사용자 삭제 성공"));
    }
    
    // 이미지 파일 처리하는 헬퍼 메서드
    private String processImage(MultipartFile image) throws IOException {
        // 파일 유효성 검사
        if (image.isEmpty()) {
            throw new ProfileImageRequiredException();
        }
        
        // 파일 크기 검사 (10MB)
        if (image.getSize() > 10 * 1024 * 1024) {
            throw new ProfileImageProcessingException("이미지 크기는 10MB 이하여야 합니다");
        }
        
        // 파일 타입 검사
        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ProfileImageProcessingException("이미지 파일만 업로드 가능합니다");
        }
        
        // 파일명 생성
        String originalFilename = image.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String fileName = "profile_" + timestamp + "_" + uniqueId + extension;
        
        // 파일 저장 - 절대 경로 사용
        Path uploadPath = Paths.get(System.getProperty("user.dir"), "uploads", "profiles");
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        Path filePath = uploadPath.resolve(fileName);
        image.transferTo(filePath.toFile());
        
        log.info("프로필 이미지 업로드 성공: {}", fileName);
        return fileName;
    }

    // JSON 문자열을 UserRequest로 파싱하는 헬퍼 메서드
    private UserRequest parseUserRequest(String userData) {
        try {
            // Jackson ObjectMapper를 사용하여 JSON 파싱
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(userData, UserRequest.class);
        } catch (Exception e) {
            throw new RuntimeException("사용자 데이터 파싱 실패: " + e.getMessage());
        }
    }
}
