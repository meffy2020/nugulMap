package com.neogulmap.neogul_map.service;

import org.springframework.stereotype.Service;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.NotFoundException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ProfileImageProcessingException;
import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.repository.UserRepository;
import com.neogulmap.neogul_map.config.jwt.TokenProvider;
import lombok.RequiredArgsConstructor;
import com.neogulmap.neogul_map.dto.UserRequest;
import com.neogulmap.neogul_map.dto.UserResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final TokenProvider tokenProvider;
    
    @Value("${file.upload-dir:uploads/profiles/}")
    private String uploadDir;
    
    // 허용된 이미지 타입
    private static final String[] ALLOWED_IMAGE_TYPES = {".jpg", ".jpeg", ".png", ".gif", ".webp"};

    @Transactional
    public UserResponse createUser(UserRequest userRequest) {
        // 프로필 이미지 처리
        String profileImagePath = null;
        if (userRequest.getProfileImage() != null && !userRequest.getProfileImage().isEmpty()) {
            profileImagePath = userRequest.getProfileImage();
        }
        
        User user = User.builder()
                .email(userRequest.getEmail())
                .oauthId(userRequest.getOauthId())
                .oauthProvider(userRequest.getOauthProvider())
                .nickname(userRequest.getNickname())
                .profileImage(profileImagePath)
                .createdAt(userRequest.getCreatedAt())
                .build();
        User savedUser = userRepository.save(user);
        return UserResponse.from(savedUser);
    }

    @Transactional
    public UserResponse updateUser(Long id, UserRequest userRequest) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
        user.update(userRequest);
        return UserResponse.from(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        // 사용자가 존재하는지 먼저 확인
        if (!userRepository.existsById(id)) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND);
        }
        userRepository.deleteById(id);
    }

    public User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
    }
    
    /**
     * JWT 토큰 생성 (OAuth 사용자용)
     */
    public Map<String, String> generateJwtTokens(User user) {
        // Access Token (2시간)
        String accessToken = tokenProvider.generateToken(user, Duration.ofHours(2));
        // Refresh Token (30일)
        String refreshToken = tokenProvider.generateToken(user, Duration.ofDays(30));
        
        return Map.of(
            "accessToken", accessToken,
            "refreshToken", refreshToken,
            "message", "JWT 토큰 생성 성공"
        );
    }
    
    /**
     * 프로필 이미지 저장 메서드
     */
    public void saveProfileImage(MultipartFile file, String filename) {
        try {
            // 이미지 검증은 Controller에서 이미 완료됨 - 여기서는 저장만
            
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Path filePath = uploadPath.resolve(filename);
            Files.copy(file.getInputStream(), filePath);
        } catch (IOException e) {
            throw new ProfileImageProcessingException("프로필 이미지 저장에 실패했습니다.");
        }
    }
    
    /**
     * 프로필 이미지 업데이트 메서드
     */
    @Transactional
    public void updateProfileImage(Long id, String profileImagePath) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
        
        user.setProfileImage(profileImagePath);
        userRepository.save(user);
    }
    
    /**
     * 인증된 사용자 정보 조회
     */
    public User getUserFromAuthentication(Object principal) {
        if (principal == null) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND);
        }

        String email = null;

        if (principal instanceof OAuth2User oAuth2User) {
            email = (String) oAuth2User.getAttributes().get("email");
        } else if (principal instanceof UserDetails userDetails) {
            email = userDetails.getUsername();
        } else if (principal instanceof String) {
            // JWT 토큰의 경우 principal이 String (email) 형태
            email = (String) principal;
        }

        if (email == null || email.isEmpty()) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND);
        }

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
    }
    
    /**
     * 이메일로 사용자 조회
     */
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    /**
     * 프로필 이미지 처리 메서드 (기존 메서드 개선)
     */
    private String processProfileImage(String imageData) {
        try {
            // 이미지 저장 디렉토리 생성
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            
            // 고유한 파일명 생성 (UUID + 타임스탬프)
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String uniqueId = UUID.randomUUID().toString().substring(0, 8);
            String fileName = "profile_" + timestamp + "_" + uniqueId + ".jpg";
            
            // 파일 경로 설정
            Path filePath = uploadPath.resolve(fileName);
            
            // 이미지 데이터를 파일로 저장 (Base64 디코딩 가정)
            // 실제 구현에서는 MultipartFile을 사용하는 것이 좋습니다
            byte[] imageBytes = java.util.Base64.getDecoder().decode(imageData);
            Files.write(filePath, imageBytes);
            
            return fileName; // 데이터베이스에 저장할 파일명 반환
            
        } catch (IOException e) {
            throw new ProfileImageProcessingException("프로필 이미지 저장 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    /**
     * 이미지 파일 삭제 메서드
     */
    public void deleteProfileImage(String fileName) {
        if (fileName != null && !fileName.isEmpty()) {
            try {
                Path filePath = Paths.get(uploadDir, fileName);
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                }
            } catch (IOException e) {
                // 로그만 남기고 예외는 던지지 않음
                System.err.println("프로필 이미지 삭제 중 오류: " + e.getMessage());
            }
        }
    }
}
