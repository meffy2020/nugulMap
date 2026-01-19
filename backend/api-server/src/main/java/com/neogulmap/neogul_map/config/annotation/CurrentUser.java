package com.neogulmap.neogul_map.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 현재 인증된 사용자를 주입받기 위한 커스텀 어노테이션
 * HandlerMethodArgumentResolver를 통해 자동으로 User 객체를 주입받습니다.
 * 
 * @param required 필수 여부 (기본값: true)
 *                true인 경우 인증되지 않으면 예외 발생
 *                false인 경우 인증되지 않으면 null 반환
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
    boolean required() default true;
}
