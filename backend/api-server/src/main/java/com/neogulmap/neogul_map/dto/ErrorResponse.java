package com.neogulmap.neogul_map.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * API 에러 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    
    private LocalDateTime timestamp;
    private int status;
    private String code;
    private String error;
    private String message;
    private String path;
    private String trace;
    
    public static ErrorResponse of(int status, String error, String message, String path) {
        return ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(status)
            .error(error)
            .message(message)
            .path(path)
            .build();
    }
    
    public static ErrorResponse of(com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode errorCode) {
        return ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(errorCode.getStatus().value())
            .code(errorCode.getCode())
            .error(errorCode.getStatus().getReasonPhrase())
            .message(errorCode.getMessage())
            .build();
    }
    
    public static ErrorResponse of(com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode errorCode, String message) {
        return ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(errorCode.getStatus().value())
            .code(errorCode.getCode())
            .error(errorCode.getStatus().getReasonPhrase())
            .message(message)
            .build();
    }
}
