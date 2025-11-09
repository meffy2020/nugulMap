package com.neogulmap.neogul_map.config.exceptionHandling.exception;

import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import lombok.Getter;

// 비즈니스 로직 예외 처리 클래스(UserNotFoundException 등)
@Getter
public class BusinessBaseException extends RuntimeException {
    private final ErrorCode errorCode;// 예외와 연결된 ErrorCode

    // 예외 생성 시 ErrorCode를 받아 설정
    public BusinessBaseException(ErrorCode errorCode) {
        super(errorCode.getMessage()); // RuntimeException의 메시지 설정
        this.errorCode = errorCode;
    }
    
    // 예외 생성 시 ErrorCode와 커스텀 메시지를 받아 설정
    public BusinessBaseException(ErrorCode errorCode, String message) {
        super(message); // RuntimeException의 메시지 설정
        this.errorCode = errorCode;
    }
    
    // 예외 생성 시 ErrorCode와 원인 예외를 받아 설정
    public BusinessBaseException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause); // RuntimeException의 메시지와 원인 설정
        this.errorCode = errorCode;
    }
}
