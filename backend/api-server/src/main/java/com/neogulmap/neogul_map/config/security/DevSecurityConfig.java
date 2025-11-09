package com.neogulmap.neogul_map.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * 개발 환경용 보안 설정
 * - 모든 API 허용 (개발 편의성)
 * - CORS 완전 개방
 * - H2 콘솔 접근 허용
 * 
 * 활성화 조건: spring.profiles.active=dev 또는 app.security.dev-mode=true
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(
    name = "app.security.dev-mode", 
    havingValue = "true", 
    matchIfMissing = false
)
@RequiredArgsConstructor
public class DevSecurityConfig {

    @Bean
    @Order(0) // 최우선 순위
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(devCorsConfigurationSource()))
            .authorizeHttpRequests(authz -> authz
                // 모든 요청 허용 (개발용)
                .anyRequest().permitAll()
            )
            .headers(headers -> headers
                .frameOptions().disable() // H2 콘솔용
                .contentTypeOptions().disable()
            )
            .build();
    }

    @Bean
    public CorsConfigurationSource devCorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // 개발용: 모든 오리진 허용
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        
        // 개발용 헤더 노출
        configuration.setExposedHeaders(Arrays.asList(
            "Authorization",
            "X-Total-Count",
            "X-Page-Number", 
            "X-Page-Size",
            "X-Request-ID"
        ));
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}

