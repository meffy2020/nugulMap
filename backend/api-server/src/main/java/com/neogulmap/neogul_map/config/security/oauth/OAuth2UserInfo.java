package com.neogulmap.neogul_map.config.security.oauth;

import java.util.Map;

/**
 * OAuth2 사용자 정보 통일 인터페이스
 * 각 OAuth 제공자(Google, Kakao, Naver)의 서로 다른 JSON 구조를 통일된 인터페이스로 제공
 */
public interface OAuth2UserInfo {
    
    /**
     * OAuth 제공자 ID (고유 식별자)
     * @return 제공자별 고유 ID
     */
    String getId();
    
    /**
     * 사용자 이메일
     * @return 이메일 주소
     */
    String getEmail();
    
    /**
     * 원본 attributes 반환
     * @return 원본 attributes Map
     */
    Map<String, Object> getAttributes();
    
    /**
     * OAuth 제공자 이름
     * @return 제공자 이름 (google, kakao, naver 등)
     */
    String getProvider();
}

