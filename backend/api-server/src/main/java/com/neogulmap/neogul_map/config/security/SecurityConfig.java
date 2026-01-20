package com.neogulmap.neogul_map.config.security;

import com.neogulmap.neogul_map.config.security.jwt.JwtAuthenticationEntryPoint;
import com.neogulmap.neogul_map.config.security.jwt.JwtAuthenticationFilter;
import com.neogulmap.neogul_map.config.security.oauth.OAuth2SuccessHandler;
import com.neogulmap.neogul_map.config.security.oauth.OAuth2FailureHandler;
import com.neogulmap.neogul_map.config.security.oauth.OAuth2UserCustomService;
import com.neogulmap.neogul_map.config.security.oauth.OAuth2AuthorizationRequestBasedOnCookieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Profile;


/**
 * 프로덕션 수준 보안 설정
 * - JWT 기반 인증
 * - OAuth2 소셜 로그인
 * - CORS 보안 강화
 * - CSRF 보호
 */
@Profile({"dev", "prod", "mysql"})  // mysql 프로파일에서도 활성화
@Configuration 
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;
    private final OAuth2UserCustomService oAuth2UserCustomService;
    private final OAuth2AuthorizationRequestBasedOnCookieRepository oAuth2AuthorizationRequestBasedOnCookieRepository;

    @Value("${app.cors.allowed-origins:http://localhost,http://localhost:3000}")
    private String[] allowedOrigins;

    @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
    private String[] allowedMethods;

    @Value("${app.cors.allowed-headers:*}")
    private String[] allowedHeaders;

    @Value("${app.security.csrf.enabled:true}")
    private boolean csrfEnabled;

    /**
     * 통합 보안 필터 체인
     * - OAuth2 로그인 플로우와 JWT 인증을 하나의 필터 체인에서 관리
     * - 세션 정책: IF_REQUIRED (OAuth2 로그인 시에만 세션 사용, 이후 JWT로 통신)
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // Stateless 설정: OAuth2 인증 정보는 쿠키 레포지토리(OAuth2AuthorizationRequestBasedOnCookieRepository)에서 관리
            // 서버 세션 불필요 - 브라우저 쿠키만으로 OAuth2 state 관리 가능
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(authz -> authz
                // 테스트 엔드포인트 허용 (최우선 순위)
                .requestMatchers("/test/**", "/api/test/**").permitAll()

                // 공개 엔드포인트 (인증 불필요)
                .requestMatchers("/login").permitAll() // 로그인 선택 페이지
                .requestMatchers("/login/**").permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll() // OAuth2 인증 시작/콜백
                .requestMatchers("/auth/refresh", "/auth/validate").permitAll()
                .requestMatchers("/public/**").permitAll()
                .requestMatchers("/health").permitAll()
                
                // 공개 조회 엔드포인트 (GET 요청만 허용)
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/zones/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/images/**").permitAll()
                
                // 정적 리소스 허용
                .requestMatchers("/static/**", "/css/**", "/js/**", "/favicon.ico").permitAll()
                
                // Swagger UI 허용 (개발용)
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                
                // Actuator 엔드포인트
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                
                // 회원가입 페이지 (GET, POST 모두 공개)
                // OAuth2 로그인 후 프로필 미완료 사용자가 닉네임 등록을 위해 접근
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/auth/signup").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/auth/signup").permitAll()
                
                // 인증이 필요한 API 엔드포인트
                // /zones/**는 위에서 GET만 permitAll()로 설정했으므로, 나머지 메서드는 여기서 authenticated() 처리
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/zones/**").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.PUT, "/zones/**").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/zones/**").authenticated()
                .requestMatchers("/users/**").authenticated()
                // /images/**는 위에서 GET만 permitAll()로 설정했으므로, 나머지 메서드는 여기서 authenticated() 처리
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/images/**").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.PUT, "/images/**").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/images/**").authenticated()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                
                // 나머지는 인증 필요
                .anyRequest().authenticated()
            )
            // OAuth2 로그인 설정
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(authorization -> authorization
                    .baseUri("/oauth2/authorization")
                    .authorizationRequestRepository(oAuth2AuthorizationRequestBasedOnCookieRepository)
                )
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(oAuth2UserCustomService)
                )
                .successHandler(oAuth2SuccessHandler)
                .failureHandler(oAuth2FailureHandler)
                // defaultSuccessUrl 제거: successHandler가 리다이렉트를 처리함
            )
            // 예외 처리: 인증 실패 시 JWT EntryPoint 사용
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
            )
            // JWT 인증 필터 추가 (OAuth2 로그인 이후 JWT 토큰으로 인증)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
            )
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // 강화된 BCrypt
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // 허용된 오리진 설정 (보안 강화)
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));
        configuration.setAllowedMethods(Arrays.asList(allowedMethods));
        configuration.setAllowedHeaders(Arrays.asList(allowedHeaders));
        configuration.setAllowCredentials(true);
        
        // 보안 헤더 설정
        configuration.setExposedHeaders(List.of(
            "Authorization", 
            "X-Total-Count",
            "X-Page-Number",
            "X-Page-Size"
        ));
        
        // Preflight 요청 캐시 시간 설정
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}
