package com.neogulmap.neogul_map.config.exceptionHandling.exception;

import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;

/**
 * 시스템 관련 예외
 */
public class SystemException extends BusinessBaseException {
    
    public SystemException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public SystemException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public SystemException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
