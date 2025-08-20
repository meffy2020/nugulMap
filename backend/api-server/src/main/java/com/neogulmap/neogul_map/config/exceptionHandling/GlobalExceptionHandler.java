package com.neogulmap.neogul_map.config.exceptionHandling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.neogulmap.neogul_map.config.exceptionHandling.ErrorResponse;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.NotFoundException;
import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.validation.ConstraintViolationException;

@Slf4j
@RestControllerAdvice// 모든 컨트롤러에서 발생하는 예외를 잡아서 처리
public class GlobalExceptionHandler {
       /**
     * 비즈니스 예외 (예: 권한 없음, 중복 신청 등)
     */
    @ExceptionHandler(BusinessBaseException.class)
    protected ResponseEntity<ErrorResponse> handleBusinessException(BusinessBaseException e) {
        log.error("[4xx] Business Exception", e);
        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(ErrorResponse.of(e.getErrorCode()));
    }

    @ExceptionHandler(NotFoundException.class)
    protected ResponseEntity<ErrorResponse> handleNotFoundException(NotFoundException e) {
        log.error("[404] Not Found Exception", e);
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ErrorCode.NOT_FOUND));
    }

    /**
     * Validation 예외 처리 (@Valid, @Validated)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        log.error("[400] Validation Exception", e);
        
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("입력값 검증에 실패했습니다.");
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .status(HttpStatus.BAD_REQUEST.value())
                        .message(errorMessage)
                        .build());
    }

    /**
     * 일반적인 예외 처리
     */
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("[500] Internal Server Error", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .message("서버 내부 오류가 발생했습니다.")
                        .build());
    }
}
