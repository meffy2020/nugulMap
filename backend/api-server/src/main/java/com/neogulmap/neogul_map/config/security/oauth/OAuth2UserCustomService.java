package com.neogulmap.neogul_map.config.security.oauth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2UserCustomService extends DefaultOAuth2UserService {
    
    private final OAuth2UserInfoFactory oAuth2UserInfoFactory;
    
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        
        try {
            String registrationId = userRequest.getClientRegistration().getRegistrationId();
            log.info("OAuth2 로그인 시도: {}", registrationId);
            
            // Factory를 사용하여 제공자별 OAuth2UserInfo 생성
            OAuth2UserInfo oAuth2UserInfo = oAuth2UserInfoFactory.getOAuth2UserInfo(
                registrationId, 
                oAuth2User.getAttributes()
            );
            
            // OAuth2UserInfo를 기반으로 CustomOAuth2User 생성
            return new CustomOAuth2User(oAuth2User, oAuth2UserInfo);
            
        } catch (Exception e) {
            log.error("OAuth2 사용자 로드 중 오류 발생", e);
            OAuth2Error oauth2Error = new OAuth2Error(
                "oauth2_user_load_failed",
                "OAuth2 사용자 로드 실패: " + e.getMessage(),
                null
            );
            throw new OAuth2AuthenticationException(oauth2Error, e);
        }
    }
    
    // 커스텀 OAuth2User 클래스
    public static class CustomOAuth2User implements OAuth2User {
        private final OAuth2User oAuth2User;
        private final OAuth2UserInfo oAuth2UserInfo;
        
        public CustomOAuth2User(OAuth2User oAuth2User, OAuth2UserInfo oAuth2UserInfo) {
            this.oAuth2User = oAuth2User;
            this.oAuth2UserInfo = oAuth2UserInfo;
        }
                 
        @Override
        public Map<String, Object> getAttributes() {
            return oAuth2User.getAttributes();
        }
        
        @Override
        public String getName() {
            // OAuth2UserInfo의 getId를 사용 (제공자별 고유 ID)
            return oAuth2UserInfo.getId();
        }
        
        @Override
        public Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
            return Collections.emptyList();
        }
        
        // OAuth 제공자 정보 추가
        public String getRegistrationId() {
            return oAuth2UserInfo.getProvider();
        }
        
        // OAuth2UserInfo를 통한 통일된 인터페이스로 사용자 정보 추출
        public String getEmail() {
            return oAuth2UserInfo.getEmail();
        }
        
        public String getNickname() {
            return oAuth2UserInfo.getNickname();
        }
        
        public String getProfileImage() {
            return oAuth2UserInfo.getProfileImage();
        }
        
        // OAuth2UserInfo 직접 접근 (필요한 경우)
        public OAuth2UserInfo getOAuth2UserInfo() {
            return oAuth2UserInfo;
        }
    }
}