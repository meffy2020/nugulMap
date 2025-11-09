package com.neogulmap.neogul_map.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;

@Slf4j
// @RestController  // 테스트용으로 임시 비활성화
@RequiredArgsConstructor
@RequestMapping("/oauth2")
public class OAuth2Controller {

    @Value("${app.oauth2.google.url:/oauth2/authorization/google}")
    private String googleLoginUrl;
    
    @Value("${app.oauth2.kakao.url:/oauth2/authorization/kakao}")
    private String kakaoLoginUrl;
    
    @Value("${app.oauth2.naver.url:/oauth2/authorization/naver}")
    private String naverLoginUrl;

    // OAuth2 로그인 성공 페이지
    @GetMapping("/success")
    public ResponseEntity<?> oauth2Success() {
        log.info("OAuth2 로그인 성공 페이지 접근");
        
        Map<String, Object> response = Map.of(
            "success", true,
            "message", "OAuth2 로그인이 성공적으로 완료되었습니다.",
            "data", Map.of(
                "redirectUrl", "/",
                "timestamp", java.time.LocalDateTime.now().toString()
            )
        );
        
        return ResponseEntity.ok(response);
    }

    // OAuth2 로그인 실패 페이지
    @GetMapping("/failure")
    public ResponseEntity<?> oauth2Failure() {
        log.warn("OAuth2 로그인 실패");
        
        Map<String, Object> response = Map.of(
            "success", false,
            "message", "OAuth2 로그인에 실패했습니다.",
            "data", Map.of(
                "redirectUrl", "/login",
                "timestamp", java.time.LocalDateTime.now().toString()
            )
        );
        
        return ResponseEntity.badRequest().body(response);
    }

    // OAuth2 로그인 URL 제공
    @GetMapping("/login-urls")
    public ResponseEntity<?> getLoginUrls() {
        Map<String, Object> loginUrls = Map.of(
            "google", googleLoginUrl,
            "kakao", kakaoLoginUrl,
            "naver", naverLoginUrl
        );
        
        Map<String, Object> response = Map.of(
            "success", true,
            "message", "OAuth2 로그인 URL 목록",
            "data", Map.of(
                "loginUrls", loginUrls,
                "timestamp", java.time.LocalDateTime.now().toString()
            )
        );
        
        return ResponseEntity.ok(response);
    }
    
}
