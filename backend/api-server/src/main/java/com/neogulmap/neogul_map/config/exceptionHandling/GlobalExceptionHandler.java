package com.neogulmap.neogul_map.config.exceptionHandling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.neogulmap.neogul_map.config.exceptionHandling.ErrorResponse;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.NotFoundException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ProfileImageRequiredException;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.ProfileImageProcessingException;
import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;

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
     * 프로필 이미지 관련 예외 처리
     */
    @ExceptionHandler(ProfileImageRequiredException.class)
    protected ResponseEntity<ErrorResponse> handleProfileImageRequiredException(ProfileImageRequiredException e) {
        log.error("[400] Profile Image Required Exception", e);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.PROFILE_IMAGE_REQUIRED));
    }

    @ExceptionHandler(ProfileImageProcessingException.class)
    protected ResponseEntity<ErrorResponse> handleProfileImageProcessingException(ProfileImageProcessingException e) {
        log.error("[400] Profile Image Processing Exception: {}", e.getMessage());
        
        // 이미지 형식 오류는 클라이언트 오류이므로 400 Bad Request로 처리
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .status(HttpStatus.BAD_REQUEST.value())
                        .code("P005")
                        .message(e.getMessage())
                        .build());
    }

    /**
     * JSON 파싱 및 기타 런타임 예외 처리
     */
    @ExceptionHandler(RuntimeException.class)
    protected ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException e) {
        log.error("[500] Runtime Exception", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .message("데이터 처리 중 오류가 발생했습니다: " + e.getMessage())
                        .build());
    }

    /**
     * 파일 처리 관련 예외 처리
     */
    @ExceptionHandler(IOException.class)
    protected ResponseEntity<ErrorResponse> handleIOException(IOException e) {
        log.error("[500] IO Exception", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.PROFILE_IMAGE_PROCESSING_ERROR));
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
