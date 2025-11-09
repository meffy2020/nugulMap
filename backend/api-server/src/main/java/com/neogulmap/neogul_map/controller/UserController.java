package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.dto.UserRequest;
import com.neogulmap.neogul_map.dto.UserResponse;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.service.UserService;
import com.neogulmap.neogul_map.service.ImageService;
import com.neogulmap.neogul_map.domain.enums.ImageType;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ProfileImageRequiredException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ProfileImageProcessingException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ImageUploadException;
import com.neogulmap.neogul_map.util.ValidationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService userService;
    private final ImageService imageService;

    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(@PathVariable("id") Long id) {
        User user = userService.getUser(id);
        return ResponseEntity.ok()
                .header("X-Message", "사용자 조회 성공")
                .body(UserResponse.from(user));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createUser(
            @RequestPart("userData") String userData,
            @RequestPart(value = "profileImage", required = false) MultipartFile profileImage) {
        
        // JSON 데이터를 UserRequest로 파싱
        UserRequest userRequest = parseUserRequest(userData);
        
        // 1단계: OAuth 정보로 기본 사용자 생성 (프로필 이미지 없이)
        UserResponse userResponse = userService.createUser(userRequest);
        
        // 2단계: 프로필 이미지가 있으면 설정, 없으면 기본값 유지
        String imageFileName = null;
        if (profileImage != null && !profileImage.isEmpty()) {
            imageFileName = imageService.processImage(profileImage, ImageType.PROFILE);
            userService.updateProfileImage(userResponse.getId(), imageFileName);
        }
        
        // 3단계: 완성된 사용자 정보 반환
        User updatedUser = userService.getUser(userResponse.getId());
        String message = imageFileName != null ? 
            "OAuth 로그인 및 프로필 설정 완료" : 
            "OAuth 로그인 완료 (기본 이미지 사용)";
            
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                    "success", true,
                    "message", message,
                    "data", Map.of(
                        "user", UserResponse.from(updatedUser),
                        "profileImage", imageFileName != null ? imageFileName : "default"
                    )
                ));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateUser(
            @PathVariable("id") Long id,
            @RequestPart("userData") String userData,
            @RequestPart(value = "profileImage", required = false) MultipartFile profileImage) {
        
        // JSON 데이터를 UserRequest로 파싱
        UserRequest userRequest = parseUserRequest(userData);
        
        // 1단계: 프로필 이미지 처리 (있으면 업데이트, 없으면 기본값 유지)
        String imageFileName = null;
        if (profileImage != null && !profileImage.isEmpty()) {
            imageFileName = imageService.processImage(profileImage, ImageType.PROFILE);
            userService.updateProfileImage(id, imageFileName);
        }
        
        // 2단계: 사용자 정보 업데이트 (닉네임 등)
        UserResponse userResponse = userService.updateUser(id, userRequest);
        
        // 응답 메시지 구성
        String message = buildUpdateMessage(userRequest.getNickname(), imageFileName);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", message,
            "data", Map.of(
                "user", userResponse,
                "profileImage", imageFileName != null ? imageFileName : "unchanged"
            )
        ));
    }

    @PutMapping(value = "/{id}/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateProfileImage(
            @PathVariable("id") Long id,
            @RequestPart("profileImage") MultipartFile profileImage) {
        
        if (profileImage == null || profileImage.isEmpty()) {
            throw new ProfileImageRequiredException();
        }
        
        String imageFileName = imageService.processImage(profileImage, ImageType.PROFILE);
        userService.updateProfileImage(id, imageFileName);
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "프로필 이미지가 업데이트되었습니다.",
            "data", Map.of(
                "profileImage", imageFileName,
                "userId", id
            )
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable("id") Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "사용자 삭제 성공",
            "data", Map.of("deletedUserId", id)
        ));
    }
    

    // JSON 문자열을 UserRequest로 파싱하는 헬퍼 메서드
    private UserRequest parseUserRequest(String userData) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(userData, UserRequest.class);
        } catch (Exception e) {
            log.error("사용자 데이터 파싱 실패: {}", e.getMessage(), e);
            throw new RuntimeException("사용자 데이터 파싱 실패: " + e.getMessage());
        }
    }
    
    // 업데이트 메시지 구성 헬퍼 메서드
    private String buildUpdateMessage(String nickname, String imageFileName) {
        StringBuilder message = new StringBuilder("프로필 정보 업데이트 완료");
        
        boolean hasNickname = nickname != null && !nickname.trim().isEmpty();
        boolean hasImage = imageFileName != null;
        
        if (hasNickname || hasImage) {
            message.append(" (");
            
            if (hasNickname) {
                message.append("닉네임: ").append(nickname);
                if (hasImage) {
                    message.append(", ");
                }
            }
            
            if (hasImage) {
                message.append("이미지: ").append(imageFileName);
            } else if (hasNickname) {
                message.append(", 기본 이미지 유지");
            }
            
            message.append(")");
        } else {
            message.append(" (기본 이미지 유지)");
        }
        
        return message.toString();
    }
    
}
