package com.neogulmap.neogul_map.config.security.oauth;

import java.util.Map;

/**
 * Google OAuth2 사용자 정보 파싱
 * 
 * Google 응답 구조:
 * {
 *   "sub": "123456789",
 *   "email": "user@example.com"
 * }
 */
public class GoogleOAuth2UserInfo implements OAuth2UserInfo {
    
    private final Map<String, Object> attributes;
    
    public GoogleOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
    
    @Override
    public String getId() {
        return (String) attributes.get("sub");
    }
    
    @Override
    public String getEmail() {
        return (String) attributes.get("email");
    }
    
    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }
    
    @Override
    public String getProvider() {
        return "google";
    }
}

