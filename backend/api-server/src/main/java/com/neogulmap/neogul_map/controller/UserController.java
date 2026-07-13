package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.dto.UserRequest;
import com.neogulmap.neogul_map.dto.UserResponse;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.service.UserService;
import com.neogulmap.neogul_map.service.ImageService;
import com.neogulmap.neogul_map.domain.enums.ImageType;
import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ProfileImageRequiredException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.neogulmap.neogul_map.config.annotation.CurrentUser;
import org.springframework.transaction.annotation.Transactional;
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
    public ResponseEntity<?> getUser(@PathVariable("id") Long id, @CurrentUser User currentUser) {
        if (!isCurrentUser(id, currentUser)) {
            return selfOnlyResponse();
        }
        User user = userService.getUser(id);
        return ResponseEntity.ok()
                .header("X-Message", "사용자 조회 성공")
                .body(UserResponse.from(user));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateUser(
            @PathVariable("id") Long id,
            @RequestPart("userData") String userData,
            @RequestPart(value = "profileImage", required = false) MultipartFile profileImage,
            @CurrentUser User currentUser) {

        if (!isCurrentUser(id, currentUser)) {
            return selfOnlyResponse();
        }
        
        // JSON 데이터를 UserRequest로 파싱
        UserRequest userRequest = parseUserRequest(userData);
        validateNickname(userRequest.getNickname());
        userService.validatePublicNickname(userRequest.getNickname());
        // OAuth identifiers and storage paths can never be changed by client profile JSON.
        userRequest.setEmail(null);
        userRequest.setOauthId(null);
        userRequest.setOauthProvider(null);
        userRequest.setProfileImage(null);
        
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
            @RequestPart("profileImage") MultipartFile profileImage,
            @CurrentUser User currentUser) {

        if (!isCurrentUser(id, currentUser)) {
            return selfOnlyResponse();
        }
        
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

    /**
     * 프로필 완료 API (회원가입 완료)
     * OAuth2 로그인 후 프로필이 완료되지 않은 경우, 닉네임과 프로필 이미지를 설정하여 회원가입을 완료함
     * 
     * @param nickname 닉네임 (필수)
     * @param profileImage 프로필 이미지 (선택)
     * @return 완료된 사용자 정보
     */
    @Transactional
    @PostMapping(value = "/profile-setup", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> completeProfile(
            @CurrentUser User user,
            @RequestParam("nickname") String nickname,
            @RequestParam(value = "profileImage", required = false) MultipartFile profileImage) {
        
        try {
            
            // 이미 프로필이 완료된 경우
            if (user.isProfileComplete()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "이미 회원가입이 완료된 사용자입니다."
                ));
            }
            
            // 닉네임 검증
            if (nickname == null || nickname.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "닉네임은 필수입니다."
                ));
            }
            
            if (nickname.length() < 2 || nickname.length() > 20) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "닉네임은 2자 이상 20자 이하여야 합니다."
                ));
            }

            userService.validatePublicNickname(nickname);
            
            // 프로필 이미지 처리
            String profileImagePath = null;
            if (profileImage != null && !profileImage.isEmpty()) {
                profileImagePath = imageService.processImage(profileImage, ImageType.PROFILE);
            }
            
            // 사용자 정보 업데이트
            UserRequest userRequest = UserRequest.builder()
                    .email(user.getEmail())
                    .oauthId(user.getOauthId())
                    .oauthProvider(user.getOauthProvider())
                    .nickname(nickname.trim())
                    .profileImage(profileImagePath)
                    .build();
            
            // 사용자 정보 업데이트
            userService.updateUser(user.getId(), userRequest);
            
            // 프로필 이미지가 있으면 별도로 업데이트
            if (profileImagePath != null) {
                userService.updateProfileImage(user.getId(), profileImagePath);
            }
            
            // 업데이트된 사용자 정보 조회
            User updatedUser = userService.getUser(user.getId());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "회원가입이 완료되었습니다.",
                "data", Map.of(
                    "user", UserResponse.from(updatedUser)
                )
            ));
            
        } catch (BusinessBaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("프로필 완료 처리 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "프로필 완료 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
            ));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable("id") Long id, @CurrentUser User currentUser) {
        if (!currentUser.getId().equals(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", "본인 계정만 삭제할 수 있습니다."
            ));
        }

        UserService.AccountDeletionResult deletionResult = userService.deleteUser(id);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", deletionMessage(deletionResult),
            "data", Map.of(
                    "deletedUserId", id,
                    "manualAppleRevocationRequired", deletionResult.manualAppleRevocationRequired()
            )
        ));
    }

    @DeleteMapping("/me")
    public ResponseEntity<?> deleteCurrentUser(@CurrentUser User currentUser) {
        Long deletedUserId = currentUser.getId();
        UserService.AccountDeletionResult deletionResult = userService.deleteUser(deletedUserId);
        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", deletionMessage(deletionResult),
            "data", Map.of(
                    "deletedUserId", deletedUserId,
                    "manualAppleRevocationRequired", deletionResult.manualAppleRevocationRequired()
            )
        ));
    }

    private String deletionMessage(UserService.AccountDeletionResult result) {
        if (result.manualAppleRevocationRequired()) {
            return "계정 데이터가 삭제되었습니다. iPhone 설정의 Apple로 로그인에서 너굴맵 연결도 해제해 주세요.";
        }
        return "계정 삭제가 완료되었습니다.";
    }

    private boolean isCurrentUser(Long requestedUserId, User currentUser) {
        return currentUser != null && currentUser.getId() != null && currentUser.getId().equals(requestedUserId);
    }

    private ResponseEntity<Map<String, Object>> selfOnlyResponse() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "success", false,
                "message", "본인 프로필만 조회하거나 수정할 수 있습니다."
        ));
    }

    private void validateNickname(String nickname) {
        if (nickname == null) {
            return;
        }
        String normalized = nickname.trim();
        if (normalized.length() < 2 || normalized.length() > 20) {
            throw new IllegalArgumentException("닉네임은 2자 이상 20자 이하여야 합니다.");
        }
    }
    

    // JSON 문자열을 UserRequest로 파싱하는 헬퍼 메서드
    private UserRequest parseUserRequest(String userData) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(userData, UserRequest.class);
        } catch (Exception e) {
            log.error("사용자 데이터 파싱 실패: {}", e.getMessage(), e);
            throw new ValidationException(ErrorCode.VALIDATION_ERROR, "사용자 정보 형식이 올바르지 않습니다.");
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
