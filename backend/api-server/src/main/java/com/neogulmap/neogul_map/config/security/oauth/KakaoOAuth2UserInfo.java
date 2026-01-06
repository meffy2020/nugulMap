package com.neogulmap.neogul_map.config.security.oauth;

import java.util.Map;

/**
 * Kakao OAuth2 사용자 정보 파싱
 * 
 * Kakao 응답 구조:
 * {
 *   "id": 123456789,
 *   "kakao_account": {
 *     "email": "user@example.com"
 *   }
 * }
 */
@SuppressWarnings("unchecked")
public class KakaoOAuth2UserInfo implements OAuth2UserInfo {
    
    private final Map<String, Object> attributes;
    
    public KakaoOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
    
    @Override
    public String getId() {
        Object id = attributes.get("id");
        // Kakao는 id를 숫자로 반환할 수 있음
        return id != null ? String.valueOf(id) : null;
    }
    
    @Override
    public String getEmail() {
        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        if (kakaoAccount == null) {
            return null;
        }
        return (String) kakaoAccount.get("email");
    }
    
    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }
    
    @Override
    public String getProvider() {
        return "kakao";
    }
}

