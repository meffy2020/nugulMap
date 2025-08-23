package com.neogulmap.neogul_map.config;

// import com.neogulmap.neogul_map.config.oauth.OAuth2SuccessHandler;
// import com.neogulmap.neogul_map.config.oauth.OAuth2UserCustomService;
// import com.neogulmap.neogul_map.config.oauth.OAuth2AuthorizationRequestBasedOnCookieRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
// import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // OAuth2 관련 의존성 주석 처리 (테스트용)
    /*
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2UserCustomService oAuth2UserCustomService;
    private final OAuth2AuthorizationRequestBasedOnCookieRepository oAuth2AuthorizationRequestBasedOnCookieRepository;

    public SecurityConfig(OAuth2SuccessHandler oAuth2SuccessHandler,
                         OAuth2UserCustomService oAuth2UserCustomService,
                         OAuth2AuthorizationRequestBasedOnCookieRepository oAuth2AuthorizationRequestBasedOnCookieRepository) {
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
        this.oAuth2UserCustomService = oAuth2UserCustomService;
        this.oAuth2AuthorizationRequestBasedOnCookieRepository = oAuth2AuthorizationRequestBasedOnCookieRepository;
    }
    */

    // 기본 생성자 추가 (테스트용)
    public SecurityConfig() {
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(authz -> authz
                // 모든 요청을 허용 (테스트용)
                .anyRequest().permitAll()
            )
            // OAuth2 설정 주석 처리 (테스트용)
            /*
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(authorization -> authorization
                    .authorizationRequestRepository(oAuth2AuthorizationRequestBasedOnCookieRepository)
                )
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(oAuth2UserCustomService)
                )
                .successHandler(oAuth2SuccessHandler)
                .defaultSuccessUrl("/oauth2/success", true)
            )
            */
            .headers(headers -> headers.frameOptions().disable()); // H2 콘솔용
        
        return http.build();
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}
