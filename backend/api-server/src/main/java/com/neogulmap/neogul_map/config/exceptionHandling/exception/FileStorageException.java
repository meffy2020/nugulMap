package com.neogulmap.neogul_map.config.exceptionHandling.exception;

import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;

/**
 * 파일 저장소 관련 예외
 */
public class FileStorageException extends BusinessBaseException {
    
    public FileStorageException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public FileStorageException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    
    public FileStorageException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
    
    public FileStorageException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message);
        initCause(cause);
    }
}
