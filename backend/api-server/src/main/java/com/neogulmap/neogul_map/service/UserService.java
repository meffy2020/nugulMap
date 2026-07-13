package com.neogulmap.neogul_map.service;

import org.springframework.stereotype.Service;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.NotFoundException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException;
import com.neogulmap.neogul_map.config.security.jwt.TokenProvider;
import com.neogulmap.neogul_map.config.security.oauth.OAuth2UserCustomService;
import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import com.neogulmap.neogul_map.domain.Zone;
import com.neogulmap.neogul_map.domain.enums.ImageType;
import com.neogulmap.neogul_map.repository.UserRepository;
import com.neogulmap.neogul_map.repository.ZoneRepository;
import com.neogulmap.neogul_map.repository.ZoneReviewRepository;

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

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final ZoneRepository zoneRepository;
    private final ZoneReviewRepository zoneReviewRepository;
    private final ImageService imageService;
    private final TokenProvider tokenProvider;
    private final LinkedAccountRevocationService linkedAccountRevocationService;
    private final AppleRefreshTokenCipher appleRefreshTokenCipher;
    private final ReviewContentPolicy contentPolicy;
    
    // 이미지 처리 관련 설정은 ImageService로 이동됨

    @Transactional
    public UserResponse createUser(UserRequest userRequest) {
        String normalizedNickname = validatePublicNickname(userRequest.getNickname());

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
                .nickname(normalizedNickname)
                .profileImage(profileImagePath)
                .createdAt(LocalDateTime.now())
                .build();
        User savedUser = userRepository.save(user);
        return UserResponse.from(savedUser);
    }

    @Transactional
    public UserResponse updateUser(Long id, UserRequest userRequest) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
        if (userRequest.getNickname() != null) {
            userRequest.setNickname(validatePublicNickname(userRequest.getNickname()));
        }
        user.update(userRequest);
        return UserResponse.from(user);
    }

    public String validatePublicNickname(String nickname) {
        if (nickname == null) {
            return null;
        }

        String normalizedNickname = nickname.trim();
        if (normalizedNickname.length() < 2 || normalizedNickname.length() > 20) {
            throw new ValidationException(
                    ErrorCode.VALIDATION_ERROR,
                    "닉네임은 2자 이상 20자 이하여야 합니다."
            );
        }

        try {
            contentPolicy.ensureAllowed(normalizedNickname);
        } catch (ValidationException exception) {
            throw new ValidationException(
                    ErrorCode.NICKNAME_CONTENT_REJECTED,
                    "공격적이거나 부적절한 닉네임은 사용할 수 없습니다."
            );
        }
        return normalizedNickname;
    }

    @Transactional
    public AccountDeletionResult deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
        boolean appleAccount = "apple".equalsIgnoreCase(user.getOauthProvider());
        boolean manualAppleRevocationRequired = appleAccount
                && (user.getAppleRefreshTokenCiphertext() == null
                || user.getAppleRefreshTokenCiphertext().isBlank());
        // Accounts with a stored Apple refresh token are deleted only after Apple confirms
        // revocation. A transient provider failure rolls back this transaction so the token
        // remains available for the user's next deletion attempt. Legacy accounts that never
        // had a token still complete local deletion and receive the manual-revocation notice.
        linkedAccountRevocationService.revokeBeforeDeletion(user);

        // App Store account-deletion contract: remove reviews authored on places owned by others too.
        zoneReviewRepository.deleteByAuthorId(id);

        List<Zone> ownedZones = zoneRepository.findByCreatorId(id);
        for (Zone zone : ownedZones) {
            if (zone.getImage() != null && !zone.getImage().isBlank()) {
                imageService.deleteImage(zone.getImage(), ImageType.ZONE);
            }
        }
        zoneRepository.deleteAll(ownedZones);

        if (user.getProfileImage() != null && !user.getProfileImage().isBlank()) {
            imageService.deleteImage(user.getProfileImage(), ImageType.PROFILE);
        }
        userRepository.deleteById(id);
        return new AccountDeletionResult(manualAppleRevocationRequired);
    }

    public record AccountDeletionResult(boolean manualAppleRevocationRequired) {}

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
        String accessToken = tokenProvider.generateAccessToken(user, Duration.ofHours(2));
        // Refresh Token (30일)
        String refreshToken = tokenProvider.generateRefreshToken(user, Duration.ofDays(30));
        
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

    @Transactional
    public User processAppleUser(String appleSubject, String email, String fullName, String appleRefreshToken) {
        if (appleSubject == null || appleSubject.isBlank()) {
            throw new IllegalArgumentException("Apple 사용자 식별자가 필요합니다.");
        }
        String encryptedRefreshToken = appleRefreshTokenCipher.encrypt(appleRefreshToken);

        Optional<User> existingByAppleId = userRepository.findByOauthProviderAndOauthId("apple", appleSubject);
        if (existingByAppleId.isPresent()) {
            User existingUser = existingByAppleId.get();
            existingUser.setAppleRefreshTokenCiphertext(encryptedRefreshToken);
            return userRepository.save(existingUser);
        }

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("최초 Apple 로그인에는 이메일 동의가 필요합니다.");
        }

        Optional<User> existingByEmail = getUserByEmail(email);
        if (existingByEmail.isPresent()) {
            User existingUser = existingByEmail.get();
            existingUser.setOauthId(appleSubject);
            existingUser.setOauthProvider("apple");
            existingUser.setAppleRefreshTokenCiphertext(encryptedRefreshToken);
            return userRepository.save(existingUser);
        }

        String normalizedName = providerNicknameOrNull(fullName);
        User newUser = User.builder()
                .email(email)
                .nickname(normalizedName)
                .profileImage(null)
                .oauthId(appleSubject)
                .oauthProvider("apple")
                .appleRefreshTokenCiphertext(encryptedRefreshToken)
                .createdAt(LocalDateTime.now())
                .build();

        return userRepository.save(newUser);
    }

    private String providerNicknameOrNull(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        try {
            return validatePublicNickname(fullName);
        } catch (ValidationException exception) {
            log.info("Provider nickname requires user profile setup, code={}", exception.getErrorCode().getCode());
            return null;
        }
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
        
        log.debug("processOAuth2User 시작 - Provider: {}", oauthProvider);
        
        if (email == null) {
            throw new RuntimeException("OAuth2에서 이메일을 가져올 수 없습니다.");
        }
        
        // 기존 사용자 조회
        Optional<User> existingUserOpt = getUserByEmail(email);
        log.debug("기존 OAuth 사용자 조회 완료 - 존재 여부: {}", existingUserOpt.isPresent());
        
        return existingUserOpt
                .map(existingUser -> {
                    // 기존 사용자 정보 업데이트 (OAuth 정보만 업데이트, 닉네임과 프로필 이미지는 회원가입에서 설정)
                    log.debug("기존 OAuth 사용자 업데이트 시작");
                    existingUser.setOauthId(oauthId);
                    existingUser.setOauthProvider(oauthProvider);
                    // 직접 저장 (순환 참조 방지)
                    User savedUser = userRepository.save(existingUser);
                    log.debug("기존 OAuth 사용자 저장 완료");
                    
                    // 저장 후 DB에서 다시 조회하여 확인
                    User verifiedUser = userRepository.findById(savedUser.getId())
                        .orElseThrow(() -> new RuntimeException("저장된 사용자를 DB에서 찾을 수 없습니다."));
                    log.debug("기존 OAuth 사용자 DB 재조회 완료");
                    
                    return verifiedUser;
                })
                .orElseGet(() -> {
                    // 첫 로그인: OAuth 정보만 저장하고 닉네임과 프로필 이미지는 null로 설정 (회원가입에서 설정)
                    log.debug("신규 OAuth 사용자 생성 시작 - Provider: {}", oauthProvider);
                    User newUser = User.builder()
                            .email(email)
                            .nickname(null) // 회원가입에서 설정하도록 null로 설정
                            .profileImage(null) // 회원가입에서 설정하도록 null로 설정
                            .oauthId(oauthId)
                            .oauthProvider(oauthProvider)
                            .createdAt(LocalDateTime.now())
                            .build();
                    
                    // 직접 저장 (순환 참조 방지)
                    User savedUser = userRepository.save(newUser);
                    log.debug("신규 OAuth 사용자 저장 완료");
                    
                    // 저장 후 DB에서 다시 조회하여 확인
                    User verifiedUser = userRepository.findById(savedUser.getId())
                        .orElseThrow(() -> new RuntimeException("저장된 사용자를 DB에서 찾을 수 없습니다."));
                    log.debug("신규 OAuth 사용자 DB 재조회 완료");
                    
                    return verifiedUser;
                });
    }
    
    // 이미지 처리 관련 메서드들은 ImageService로 이동됨
}
