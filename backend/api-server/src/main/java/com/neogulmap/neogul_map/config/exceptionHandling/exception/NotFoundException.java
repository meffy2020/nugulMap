package com.neogulmap.neogul_map.config.exceptionHandling.exception;

import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;

// 404 Not Found 예외의 부모 클래스
public class NotFoundException extends BusinessBaseException {

    // 기본적으로 ErrorCode.NOT_FOUND 사용
    public NotFoundException() {
        super(ErrorCode.NOT_FOUND);
    }

    // 특정 Not Found 예외를 위한 생성자
    public NotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    // 원인 예외를 포함한 생성자
    public NotFoundException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
