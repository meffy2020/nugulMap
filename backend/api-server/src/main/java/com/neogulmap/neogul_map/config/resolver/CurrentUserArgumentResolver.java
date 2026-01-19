package com.neogulmap.neogul_map.config.resolver;

import com.neogulmap.neogul_map.config.annotation.CurrentUser;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * @CurrentUser 어노테이션을 처리하는 ArgumentResolver
 * 컨트롤러 메서드에서 현재 인증된 사용자 정보를 간편하게 주입받을 수 있도록 합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final UserService userService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class) &&
               parameter.getParameterType().equals(User.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // optional인 경우 (required = false)
        CurrentUser annotation = parameter.getParameterAnnotation(CurrentUser.class);
        boolean required = annotation != null ? annotation.required() : true; // 기본값은 required = true
        
        if (authentication == null || !authentication.isAuthenticated() ||
            "anonymousUser".equals(authentication.getPrincipal().toString())) {
            if (required) {
                throw new IllegalStateException("인증이 필요합니다.");
            }
            return null; // optional인 경우 null 반환
        }

        try {
            return userService.getUserFromAuthentication(authentication.getPrincipal());
        } catch (Exception e) {
            log.error("사용자 정보 조회 실패: {}", e.getMessage());
            if (required) {
                throw new IllegalStateException("사용자 정보를 찾을 수 없습니다.", e);
            }
            return null; // optional인 경우 null 반환
        }
    }
}
