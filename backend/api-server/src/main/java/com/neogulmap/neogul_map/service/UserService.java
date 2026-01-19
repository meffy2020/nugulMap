package com.neogulmap.neogul_map.service;

import org.springframework.stereotype.Service;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.NotFoundException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import com.neogulmap.neogul_map.config.security.jwt.TokenProvider;
import com.neogulmap.neogul_map.config.security.oauth.OAuth2UserCustomService;
import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.neogulmap.neogul_map.dto.UserRequest;
import com.neogulmap.neogul_map.dto.UserResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
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

    @Transactional(readOnly = true)
    public User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
    }
    
    /**
     * 모든 사용자 조회 (테스트용)
     */
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    /**
     * OAuth2 사용자 처리 (생성 또는 업데이트)
     * OAuth2SuccessHandler에서 호출됨
     * 
     * @param customOAuth2User OAuth2 사용자 정보
     * @return 생성 또는 업데이트된 User 객체
     */
    @Transactional
    public User processOAuth2User(OAuth2UserCustomService.CustomOAuth2User customOAuth2User) {
        String email = customOAuth2User.getEmail();
        String oauthId = customOAuth2User.getName();
        String oauthProvider = customOAuth2User.getRegistrationId();
        
        log.info("processOAuth2User 시작 - Email: {}, OAuthId: {}, Provider: {}", email, oauthId, oauthProvider);
        
        if (email == null) {
            throw new RuntimeException("OAuth2에서 이메일을 가져올 수 없습니다.");
        }
        
        // 기존 사용자 조회
        Optional<User> existingUserOpt = getUserByEmail(email);
        log.info("기존 사용자 조회 결과 - 존재 여부: {}", existingUserOpt.isPresent());
        
        return existingUserOpt
                .map(existingUser -> {
                    // 기존 사용자 정보 업데이트 (OAuth 정보만 업데이트, 닉네임과 프로필 이미지는 회원가입에서 설정)
                    log.info("기존 사용자 업데이트 - User ID: {}, 기존 Nickname: {}", existingUser.getId(), existingUser.getNickname());
                    existingUser.setOauthId(oauthId);
                    existingUser.setOauthProvider(oauthProvider);
                    // 직접 저장 (순환 참조 방지)
                    User savedUser = userRepository.save(existingUser);
                    log.info("기존 사용자 저장 완료 - User ID: {}, Nickname: {}, isProfileComplete: {}", 
                        savedUser.getId(), savedUser.getNickname(), savedUser.isProfileComplete());
                    
                    // 저장 후 DB에서 다시 조회하여 확인
                    User verifiedUser = userRepository.findById(savedUser.getId())
                        .orElseThrow(() -> new RuntimeException("저장된 사용자를 DB에서 찾을 수 없습니다."));
                    log.info("DB 재조회 확인 - User ID: {}, Email: {}, Nickname: {}, isProfileComplete: {}", 
                        verifiedUser.getId(), verifiedUser.getEmail(), verifiedUser.getNickname(), verifiedUser.isProfileComplete());
                    
                    return verifiedUser;
                })
                .orElseGet(() -> {
                    // 첫 로그인: OAuth 정보만 저장하고 닉네임과 프로필 이미지는 null로 설정 (회원가입에서 설정)
                    log.info("신규 사용자 생성 시작 - Email: {}", email);
                    User newUser = User.builder()
                            .email(email)
                            .nickname(null) // 회원가입에서 설정하도록 null로 설정
                            .profileImage(null) // 회원가입에서 설정하도록 null로 설정
                            .oauthId(oauthId)
                            .oauthProvider(oauthProvider)
                            .createdAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                            .build();
                    
                    // 직접 저장 (순환 참조 방지)
                    User savedUser = userRepository.save(newUser);
                    log.info("신규 사용자 저장 완료 - User ID: {}, Email: {}, Nickname: {}", 
                        savedUser.getId(), savedUser.getEmail(), savedUser.getNickname());
                    
                    // 저장 후 DB에서 다시 조회하여 확인
                    User verifiedUser = userRepository.findById(savedUser.getId())
                        .orElseThrow(() -> new RuntimeException("저장된 사용자를 DB에서 찾을 수 없습니다."));
                    log.info("DB 재조회 확인 - User ID: {}, Email: {}, Nickname: {}, isProfileComplete: {}", 
                        verifiedUser.getId(), verifiedUser.getEmail(), verifiedUser.getNickname(), verifiedUser.isProfileComplete());
                    
                    return verifiedUser;
                });
    }
    
    // 이미지 처리 관련 메서드들은 ImageService로 이동됨
}
