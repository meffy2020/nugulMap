package com.neogulmap.neogul_map.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 홈 컨트롤러 - 테스트용
 * - 루트 경로(`/`) 접근 시 로그인 상태에 따라 리다이렉트
 * - 로그인 페이지(`/login`) 제공 (OAuth2 제공자 선택)
 * 
 * 주의: 이 컨트롤러는 테스트/개발 환경 전용입니다.
 */
@Slf4j
@Controller
public class HomeController {

    /**
     * 루트 경로 접근 시 로그인 상태 확인 (테스트용)
     * - 로그인 안 되어 있으면: 로그인 선택 페이지로 리다이렉트
     * - 로그인 되어 있으면: 테스트 페이지로 리다이렉트
     */
    @GetMapping("/")
    public String home() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // 인증되지 않았거나 익명 사용자인 경우 로그인 페이지로 리다이렉트
        if (authentication == null || 
            !authentication.isAuthenticated() || 
            "anonymousUser".equals(authentication.getPrincipal().toString())) {
            log.info("루트 경로 접근 - 인증되지 않음, 로그인 페이지로 리다이렉트");
            return "redirect:/login";
        }
        
        // 인증된 사용자는 테스트 페이지로 리다이렉트
        log.info("루트 경로 접근 - 인증됨, 테스트 페이지로 리다이렉트");
        return "redirect:/test";
    }

    /**
     * 로그인 선택 페이지 (테스트용)
     * OAuth2 제공자(Google, Kakao, Naver) 선택 페이지를 표시합니다.
     */
    @GetMapping("/login")
    public String loginPage(Model model, @RequestParam(required = false) String redirect) {
        log.info("로그인 페이지 접근 - redirect: {}", redirect);
        model.addAttribute("title", "NeogulMap 로그인");
        if (redirect != null) {
            model.addAttribute("redirect", redirect);
        }
        return "oauth-test";
    }
}

