package com.neogulmap.neogul_map.config.security.oauth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * OAuth2UserInfo Factory
 * 제공자별로 적절한 OAuth2UserInfo 구현체를 생성
 */
@Slf4j
@Component
public class OAuth2UserInfoFactory {
    
    /**
     * 제공자 이름과 attributes를 기반으로 적절한 OAuth2UserInfo 구현체 생성
     * 
     * @param registrationId OAuth 제공자 ID (google, kakao, naver)
     * @param attributes OAuth 제공자로부터 받은 사용자 정보 attributes
     * @return OAuth2UserInfo 구현체
     * @throws IllegalArgumentException 지원하지 않는 제공자인 경우
     */
    public OAuth2UserInfo getOAuth2UserInfo(String registrationId, Map<String, Object> attributes) {
        if (attributes == null) {
            throw new IllegalArgumentException("Attributes cannot be null");
        }
        
        return switch (registrationId.toLowerCase()) {
            case "google" -> new GoogleOAuth2UserInfo(attributes);
            case "kakao" -> new KakaoOAuth2UserInfo(attributes);
            case "naver" -> new NaverOAuth2UserInfo(attributes);
            default -> {
                log.warn("지원하지 않는 OAuth 제공자: {}. Google 형식으로 처리합니다.", registrationId);
                yield new GoogleOAuth2UserInfo(attributes);
            }
        };
    }
}

