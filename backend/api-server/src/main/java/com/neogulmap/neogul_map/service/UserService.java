package com.neogulmap.neogul_map.service;

import org.springframework.stereotype.Service;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.NotFoundException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ProfileImageProcessingException;
import com.neogulmap.neogul_map.config.security.jwt.TokenProvider;
import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import com.neogulmap.neogul_map.dto.UserRequest;
import com.neogulmap.neogul_map.dto.UserResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.time.Duration;
import java.util.List;
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
    
    // 이미지 처리 관련 설정은 ImageService로 이동됨

    @Transactional
    public UserResponse createUser(UserRequest userRequest) {
        // 이메일 중복 체크
        if (userRequest.getEmail() != null && !userRequest.getEmail().isEmpty()) {
            if (userRepository.findByEmail(userRequest.getEmail()).isPresent()) {
                throw new BusinessBaseException(ErrorCode.EMAIL_DUPLICATION);
            }
        }
        
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
     * 모든 사용자 조회 (테스트용)
     */
    public List<User> getAllUsers() {
        return userRepository.findAll();
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
    
    // 이미지 처리 로직은 ImageService로 이동됨
    
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
    
    // 이미지 처리 관련 메서드들은 ImageService로 이동됨
}
