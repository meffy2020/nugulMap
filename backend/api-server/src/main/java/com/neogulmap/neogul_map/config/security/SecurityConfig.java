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

    @Value("${app.cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS}")
    private String[] allowedMethods;

    @Value("${app.cors.allowed-headers:*}")
    private String[] allowedHeaders;

    @Value("${app.security.csrf.enabled:true}")
    private boolean csrfEnabled;

    @Value("${app.security.sensitive-rate-limit.enabled:true}")
    private boolean sensitiveRateLimitEnabled;

    @Value("${app.security.sensitive-rate-limit.requests-per-window:30}")
    private int sensitiveRequestsPerWindow;

    @Value("${app.security.sensitive-rate-limit.window-seconds:60}")
    private long sensitiveWindowSeconds;

    @Value("${app.security.sensitive-rate-limit.max-tracked-clients:10000}")
    private int sensitiveMaxTrackedClients;

    @Value("${app.security.public-zone-rate-limit.enabled:true}")
    private boolean publicZoneRateLimitEnabled;

    @Value("${app.security.public-zone-rate-limit.requests-per-window:240}")
    private int publicZoneRequestsPerWindow;

    @Value("${app.security.public-zone-rate-limit.window-seconds:60}")
    private long publicZoneWindowSeconds;

    @Value("${app.security.public-zone-rate-limit.max-tracked-clients:10000}")
    private int publicZoneMaxTrackedClients;

    @Value("${app.test-endpoints.enabled:false}")
    private boolean testEndpointsEnabled;

    @Value("${app.swagger.enabled:true}")
    private boolean swaggerEnabled;

    /**
     * 통합 보안 필터 체인
     * - OAuth2 로그인 플로우와 JWT 인증을 하나의 필터 체인에서 관리
     * - 세션 정책: IF_REQUIRED (OAuth2 로그인 시에만 세션 사용, 이후 JWT로 통신)
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        CookieMutationOriginFilter cookieMutationOriginFilter =
                new CookieMutationOriginFilter(csrfEnabled, allowedOrigins);
        SensitiveEndpointRateLimitFilter sensitiveEndpointRateLimitFilter =
                new SensitiveEndpointRateLimitFilter(
                        sensitiveRateLimitEnabled,
                        sensitiveRequestsPerWindow,
                        sensitiveWindowSeconds,
                        sensitiveMaxTrackedClients
                );
        PublicZoneReadRateLimitFilter publicZoneReadRateLimitFilter =
                new PublicZoneReadRateLimitFilter(
                        publicZoneRateLimitEnabled,
                        publicZoneRequestsPerWindow,
                        publicZoneWindowSeconds,
                        publicZoneMaxTrackedClients
                );
        return http
            // Native clients use bearer tokens. Browser cookie mutations are protected
            // by CookieMutationOriginFilter so they cannot be replayed cross-site.
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // OAuth 인가 요청과 PKCE metadata만 짧은 서버 세션에 보관한다. API 인증은 계속 JWT stateless다.
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            .authorizeHttpRequests(authz -> {
                if (testEndpointsEnabled) {
                    // 로컬/개발에서 명시적으로 켠 경우에만 테스트 엔드포인트 허용
                    authz.requestMatchers("/test/**", "/api/test/**").permitAll();
                }

                if (swaggerEnabled) {
                    authz.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll();
                } else {
                    authz.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").denyAll();
                }

                authz
                // 공개 엔드포인트 (인증 불필요)
                .requestMatchers("/login").permitAll() // 로그인 선택 페이지
                .requestMatchers("/login/**").permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll() // OAuth2 인증 시작/콜백
                .requestMatchers("/auth/refresh", "/auth/validate", "/auth/mobile/exchange", "/auth/apple/mobile", "/auth/logout").permitAll()
                .requestMatchers("/public/**").permitAll()
                // 자체 비밀 헤더를 상수시간 비교하는 운영자 지원 요청 조회 경로
                .requestMatchers("/operator/support/requests", "/operator/support/requests/**").permitAll()
                .requestMatchers("/operator/moderation/reports", "/operator/moderation/reports/**").permitAll()
                .requestMatchers("/health").permitAll()
                
                // 공개 조회 엔드포인트 (GET 요청만 허용)
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/zones/my").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/zones/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/images/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/insights/**").permitAll()
                
                // 정적 리소스 허용
                .requestMatchers("/static/**", "/css/**", "/js/**", "/favicon.ico").permitAll()
                
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
                .anyRequest().authenticated();
            })
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
            .addFilterBefore(sensitiveEndpointRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(publicZoneReadRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(cookieMutationOriginFilter, UsernamePasswordAuthenticationFilter.class)
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
