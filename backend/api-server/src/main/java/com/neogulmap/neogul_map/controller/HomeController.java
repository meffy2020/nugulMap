package com.neogulmap.neogul_map.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 홈 컨트롤러 - 루트 경로 리다이렉트
 */
@Controller
public class HomeController {

    /**
     * 루트 경로에서 테스트 페이지로 리다이렉트
     */
    @GetMapping("/")
    public String home() {
        return "redirect:/test";
    }
}

