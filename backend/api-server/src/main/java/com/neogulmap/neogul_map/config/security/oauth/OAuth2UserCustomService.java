package com.neogulmap.neogul_map.config.security.oauth;

import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2UserCustomService extends DefaultOAuth2UserService {
    
    private final UserService userService;
    
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        
        try {
            String registrationId = userRequest.getClientRegistration().getRegistrationId();
            log.info("OAuth2 로그인 시도: {}", registrationId);
            
            // OAuth2User를 커스텀 OAuth2User로 래핑
            return new CustomOAuth2User(oAuth2User, registrationId);
            
        } catch (Exception e) {
            log.error("OAuth2 사용자 로드 중 오류 발생", e);
            throw new OAuth2AuthenticationException("OAuth2 사용자 로드 실패");
        }
    }
    
    // 커스텀 OAuth2User 클래스
    public static class CustomOAuth2User implements OAuth2User {
        private final OAuth2User oAuth2User;
        private final String registrationId;
        
        public CustomOAuth2User(OAuth2User oAuth2User, String registrationId) {
            this.oAuth2User = oAuth2User;
            this.registrationId = registrationId;
        }
                 
        @Override
        public Map<String, Object> getAttributes() {
            return oAuth2User.getAttributes();
        }
        
        @Override
        public String getName() {
            return oAuth2User.getName();
        }
        
        @Override
        public Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
            return Collections.emptyList();
        }
        
        // OAuth 제공자 정보 추가
        public String getRegistrationId() {
            return registrationId;
        }
        
        // 제공자별 사용자 정보 추출
        public String getEmail() {
            Map<String, Object> attributes = getAttributes();
            
            switch (registrationId) {
                case "google":
                    return (String) attributes.get("email");
                case "kakao":
                    Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
                    return kakaoAccount != null ? (String) kakaoAccount.get("email") : null;
                case "naver":
                    Map<String, Object> naverResponse = (Map<String, Object>) attributes.get("response");
                    return naverResponse != null ? (String) naverResponse.get("email") : null;
                default:
                    return (String) attributes.get("email");
            }
        }
        
        public String getNickname() {
            Map<String, Object> attributes = getAttributes();
            
            switch (registrationId) {
                case "google":
                    return (String) attributes.get("name");
                case "kakao":
                    Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
                    Map<String, Object> profile = kakaoAccount != null ? 
                        (Map<String, Object>) kakaoAccount.get("profile") : null;
                    return profile != null ? (String) profile.get("nickname") : null;
                case "naver":
                    Map<String, Object> naverResponse = (Map<String, Object>) attributes.get("response");
                    return naverResponse != null ? (String) naverResponse.get("nickname") : null;
                default:
                    return (String) attributes.get("name");
            }
        }
        
        public String getProfileImage() {
            Map<String, Object> attributes = getAttributes();
            
            switch (registrationId) {
                case "google":
                    return (String) attributes.get("picture");
                case "kakao":
                    Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
                    Map<String, Object> profile = kakaoAccount != null ? 
                        (Map<String, Object>) kakaoAccount.get("profile") : null;
                    return profile != null ? (String) profile.get("profile_image_url") : null;
                case "naver":
                    Map<String, Object> naverResponse = (Map<String, Object>) attributes.get("response");
                    return naverResponse != null ? (String) naverResponse.get("profile_image") : null;
                default:
                    return (String) attributes.get("picture");
            }
        }
    }
}