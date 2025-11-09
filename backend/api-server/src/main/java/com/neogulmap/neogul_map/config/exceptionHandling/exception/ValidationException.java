package com.neogulmap.neogul_map.config.exceptionHandling.exception;

import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;

/**
 * 입력값 검증 관련 예외
 */
public class ValidationException extends BusinessBaseException {
    
    public ValidationException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public ValidationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public ValidationException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
