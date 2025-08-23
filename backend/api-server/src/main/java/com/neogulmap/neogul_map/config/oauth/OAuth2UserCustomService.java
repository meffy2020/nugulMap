package com.neogulmap.neogul_map.config.oauth;

import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * OAuth2 사용자 정보를 처리하는 서비스 클래스
 * - OAuth2 로그인 시 사용자 정보를 가져오고 데이터베이스에 저장 또는 업데이트
 * - Spring Security의 DefaultOAuth2UserService를 확장하여 사용자 정보를 로드
 */
@RequiredArgsConstructor // final 필드(userRepository)에 대한 생성자를 자동 생성
@Service // Spring 서비스 컴포넌트로 등록
public class OAuth2UserCustomService extends DefaultOAuth2UserService {

    private static final Logger log = LoggerFactory.getLogger(OAuth2UserCustomService.class);
    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        
        try {
            return processOAuth2User(userRequest, oAuth2User);
        } catch (Exception e) {
            log.error("OAuth2 사용자 처리 중 오류 발생", e);
            throw new OAuth2AuthenticationException("사용자 정보 처리 실패");
        }
    }

    private OAuth2User processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oAuth2User) {
        String provider = userRequest.getClientRegistration().getRegistrationId();
        String providerId = oAuth2User.getName();
        String email = getEmailFromOAuth2User(oAuth2User, provider);
        String nickname = getNicknameFromOAuth2User(oAuth2User, provider);

        // 기존 사용자 조회
        Optional<User> existingUser = userRepository.findByEmail(email);
        
        if (existingUser.isPresent()) {
            // 기존 사용자 정보 업데이트
            User user = existingUser.get();
            updateExistingUser(user, provider, providerId, nickname);
            log.info("기존 OAuth 사용자 정보 업데이트: {}", email);
            return createOAuth2User(user, oAuth2User.getAttributes());
        } else {
            // 새 사용자 생성
            User newUser = createNewUser(provider, providerId, email, nickname);
            log.info("새 OAuth 사용자 생성: {}", email);
            return createOAuth2User(newUser, oAuth2User.getAttributes());
        }
    }

    private String getEmailFromOAuth2User(OAuth2User oAuth2User, String provider) {
        Map<String, Object> attributes = oAuth2User.getAttributes();
        
        switch (provider.toLowerCase()) {
            case "google":
                return (String) attributes.get("email");
            case "kakao":
                Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
                if (kakaoAccount != null) {
                    return (String) kakaoAccount.get("email");
                }
                break;
            case "naver":
                Map<String, Object> naverResponse = (Map<String, Object>) attributes.get("response");
                if (naverResponse != null) {
                    return (String) naverResponse.get("email");
                }
                break;
            default:
                return (String) attributes.get("email");
        }
        
        return null;
    }

    private String getNicknameFromOAuth2User(OAuth2User oAuth2User, String provider) {
        Map<String, Object> attributes = oAuth2User.getAttributes();
        
        switch (provider.toLowerCase()) {
            case "google":
                return (String) attributes.get("name");
            case "kakao":
                Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
                if (kakaoAccount != null) {
                    Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
                    if (profile != null) {
                        return (String) profile.get("nickname");
                    }
                }
                break;
            case "naver":
                Map<String, Object> naverResponse = (Map<String, Object>) attributes.get("response");
                if (naverResponse != null) {
                    return (String) naverResponse.get("nickname");
                }
                break;
            default:
                return (String) attributes.get("name");
        }
        
        return "사용자";
    }

    private void updateExistingUser(User user, String provider, String providerId, String nickname) {
        user.setOauthProvider(provider);
        user.setOauthId(providerId);
        if (nickname != null && !nickname.isEmpty()) {
            user.setNickname(nickname);
        }
        userRepository.save(user);
    }

    private User createNewUser(String provider, String providerId, String email, String nickname) {
        User user = User.builder()
                .email(email)
                .nickname(nickname != null ? nickname : email.split("@")[0])
                .oauthProvider(provider)
                .oauthId(providerId)
                .createdAt(LocalDateTime.now().toString())
                .build();
        
        return userRepository.save(user);
    }

    private OAuth2User createOAuth2User(User user, Map<String, Object> attributes) {
        return new OAuth2User() {
            @Override
            public Map<String, Object> getAttributes() {
                return attributes;
            }

            @Override
            public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
                return java.util.Collections.singletonList(
                    new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")
                );
            }

            @Override
            public String getName() {
                return user.getEmail();
            }
        };
    }
}

