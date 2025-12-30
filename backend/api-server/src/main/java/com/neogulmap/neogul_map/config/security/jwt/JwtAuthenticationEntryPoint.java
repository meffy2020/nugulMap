package com.neogulmap.neogul_map.config.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neogulmap.neogul_map.dto.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * JWT 인증 실패 시 처리
 * - 브라우저 요청(HTML): OAuth2 로그인 페이지로 리다이렉트
 * - API 요청(JSON): 401 Unauthorized JSON 응답 반환
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    
    private static final String LOGIN_SELECTION_PAGE = "/login";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                        AuthenticationException authException) throws IOException, ServletException {
        
        log.warn("Unauthorized access attempt: {} - {}", request.getRequestURI(), authException.getMessage());
        
        // API 요청인지 브라우저 요청인지 판단
        if (isApiRequest(request)) {
            // API 요청: JSON 응답 반환
            sendJsonErrorResponse(request, response);
        } else {
            // 브라우저 요청: OAuth2 로그인 페이지로 리다이렉트
            redirectToOAuth2Login(request, response);
        }
    }
    
    /**
     * API 요청인지 판단
     * - Accept 헤더에 application/json이 포함되어 있거나
     * - Content-Type이 application/json이거나
     * - 요청 경로가 /api로 시작하는 경우
     */
    private boolean isApiRequest(HttpServletRequest request) {
        String acceptHeader = request.getHeader("Accept");
        String contentType = request.getHeader("Content-Type");
        String requestUri = request.getRequestURI();
        
        // Accept 헤더 확인
        if (acceptHeader != null && acceptHeader.contains(MediaType.APPLICATION_JSON_VALUE)) {
            return true;
        }
        
        // Content-Type 확인
        if (contentType != null && contentType.contains(MediaType.APPLICATION_JSON_VALUE)) {
            return true;
        }
        
        // 요청 경로 확인 (/api로 시작하면 API 요청으로 간주)
        if (requestUri.startsWith("/api")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * JSON 에러 응답 전송
     */
    private void sendJsonErrorResponse(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpServletResponse.SC_UNAUTHORIZED)
            .error("Unauthorized")
            .message("인증이 필요합니다.")
            .path(request.getRequestURI())
            .build();
        
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
    
    /**
     * 로그인 선택 페이지로 리다이렉트
     * 사용자가 Google, Kakao, Naver 중 선택할 수 있는 페이지로 이동
     */
    private void redirectToOAuth2Login(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        log.info("브라우저 요청 감지 - 로그인 선택 페이지로 리다이렉트: {}", request.getRequestURI());
        
        // 원래 요청한 URL을 쿼리 파라미터로 전달 (로그인 후 돌아올 수 있도록)
        String redirectUrl = LOGIN_SELECTION_PAGE;
        String originalRequest = request.getRequestURI();
        if (originalRequest != null && !originalRequest.equals("/login")) {
            redirectUrl += "?redirect=" + java.net.URLEncoder.encode(originalRequest, "UTF-8");
        }
        
        response.sendRedirect(redirectUrl);
    }
}

