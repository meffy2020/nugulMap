package com.neogulmap.neogul_map.config.security.oauth;

import java.util.Map;

/**
 * Naver OAuth2 사용자 정보 파싱
 * 
 * Naver 응답 구조:
 * {
 *   "response": {
 *     "id": "123456789",
 *     "email": "user@example.com",
 *     "nickname": "홍길동",
 *     "profile_image": "https://..."
 *   }
 * }
 */
@SuppressWarnings("unchecked")
public class NaverOAuth2UserInfo implements OAuth2UserInfo {
    
    private final Map<String, Object> attributes;
    
    public NaverOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
    
    @Override
    public String getId() {
        Map<String, Object> response = (Map<String, Object>) attributes.get("response");
        if (response == null) {
            return null;
        }
        Object id = response.get("id");
        return id != null ? String.valueOf(id) : null;
    }
    
    @Override
    public String getEmail() {
        Map<String, Object> response = (Map<String, Object>) attributes.get("response");
        if (response == null) {
            return null;
        }
        return (String) response.get("email");
    }
    
    @Override
    public String getNickname() {
        Map<String, Object> response = (Map<String, Object>) attributes.get("response");
        if (response == null) {
            return null;
        }
        return (String) response.get("nickname");
    }
    
    @Override
    public String getProfileImage() {
        Map<String, Object> response = (Map<String, Object>) attributes.get("response");
        if (response == null) {
            return null;
        }
        return (String) response.get("profile_image");
    }
    
    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }
    
    @Override
    public String getProvider() {
        return "naver";
    }
}

