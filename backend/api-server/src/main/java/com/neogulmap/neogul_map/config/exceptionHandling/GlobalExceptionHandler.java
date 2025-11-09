package com.neogulmap.neogul_map.config.exceptionHandling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.neogulmap.neogul_map.dto.ErrorResponse;
import com.neogulmap.neogul_map.config.exceptionHandling.exception.*;
import com.neogulmap.neogul_map.config.exceptionHandling.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import jakarta.validation.ConstraintViolationException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import java.io.IOException;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 비즈니스 예외 처리 ====================
    
    /**
     * 비즈니스 예외 (예: 권한 없음, 중복 신청 등)
     */
    @ExceptionHandler(BusinessBaseException.class)
    protected ResponseEntity<ErrorResponse> handleBusinessException(BusinessBaseException e) {
        log.error("[Business Exception] {}: {}", e.getErrorCode().getCode(), e.getMessage(), e);
        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(ErrorResponse.of(e.getErrorCode()));
    }

    /**
     * 이미지 관련 예외 처리
     */
    @ExceptionHandler({ImageNotFoundException.class, ImageUploadException.class, 
                      ProfileImageRequiredException.class, ProfileImageProcessingException.class})
    protected ResponseEntity<ErrorResponse> handleImageException(RuntimeException e) {
        log.error("[Image Exception] {}", e.getMessage(), e);
        
        ErrorCode errorCode = switch (e.getClass().getSimpleName()) {
            case "ImageNotFoundException" -> ErrorCode.FILE_NOT_FOUND;
            case "ImageUploadException" -> ErrorCode.FILE_UPLOAD_ERROR;
            case "ProfileImageRequiredException" -> ErrorCode.PROFILE_IMAGE_REQUIRED;
            case "ProfileImageProcessingException" -> ErrorCode.PROFILE_IMAGE_PROCESSING_ERROR;
            default -> ErrorCode.FILE_UPLOAD_ERROR;
        };
        
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode));
    }

    /**
     * JSON 파싱 예외 처리
     */
    @ExceptionHandler({com.fasterxml.jackson.core.JsonProcessingException.class, 
                      com.fasterxml.jackson.databind.JsonMappingException.class})
    protected ResponseEntity<ErrorResponse> handleJsonException(Exception e) {
        log.error("[JSON Exception] {}", e.getMessage(), e);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR));
    }

    /**
     * IllegalArgumentException 처리 (잘못된 인수)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    protected ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("[Illegal Argument Exception] {}", e.getMessage(), e);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR));
    }

    /**
     * NoResourceFoundException 처리 (정적 리소스 없음)
     */
    @ExceptionHandler(NoResourceFoundException.class)
    protected ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("[Resource Not Found] {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ErrorCode.FILE_NOT_FOUND));
    }

    /**
     * Not Found 예외
     */
    @ExceptionHandler(NotFoundException.class)
    protected ResponseEntity<ErrorResponse> handleNotFoundException(NotFoundException e) {
        log.error("[Not Found Exception] {}: {}", e.getErrorCode().getCode(), e.getMessage(), e);
        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(ErrorResponse.of(e.getErrorCode()));
    }

    /**
     * 파일 저장소 예외
     */
    @ExceptionHandler(FileStorageException.class)
    protected ResponseEntity<ErrorResponse> handleFileStorageException(FileStorageException e) {
        log.error("[File Storage Exception] {}: {}", e.getErrorCode().getCode(), e.getMessage(), e);
        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(ErrorResponse.of(e.getErrorCode()));
    }

    /**
     * 검증 예외
     */
    @ExceptionHandler(ValidationException.class)
    protected ResponseEntity<ErrorResponse> handleValidationException(ValidationException e) {
        log.error("[Validation Exception] {}: {}", e.getErrorCode().getCode(), e.getMessage(), e);
        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(ErrorResponse.of(e.getErrorCode()));
    }

    /**
     * 시스템 예외
     */
    @ExceptionHandler(SystemException.class)
    protected ResponseEntity<ErrorResponse> handleSystemException(SystemException e) {
        log.error("[System Exception] {}: {}", e.getErrorCode().getCode(), e.getMessage(), e);
        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(ErrorResponse.of(e.getErrorCode()));
    }

    // ==================== Spring 프레임워크 예외 처리 ====================


    /**
     * Validation 예외 처리 (@Valid, @Validated)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.error("[Validation Exception] Method argument not valid", e);
        
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .status(HttpStatus.BAD_REQUEST.value())
                        .code(ErrorCode.VALIDATION_ERROR.getCode())
                        .message(errorMessage)
                        .build());
    }

    /**
     * ConstraintViolationException 처리
     */
    @ExceptionHandler(ConstraintViolationException.class)
    protected ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException e) {
        log.error("[Validation Exception] Constraint violation", e);
        
        String errorMessage = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .status(HttpStatus.BAD_REQUEST.value())
                        .code(ErrorCode.VALIDATION_ERROR.getCode())
                        .message(errorMessage)
                        .build());
    }

    /**
     * 파일 업로드 크기 초과 예외
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    protected ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.error("[File Upload Exception] Max upload size exceeded", e);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.FILE_SIZE_TOO_LARGE));
    }

    // ==================== 데이터베이스 예외 처리 ====================

    /**
     * 데이터 접근 예외
     */
    @ExceptionHandler(DataAccessException.class)
    protected ResponseEntity<ErrorResponse> handleDataAccessException(DataAccessException e) {
        log.error("[Database Exception] Data access error", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.DATABASE_ERROR));
    }

    /**
     * 데이터 무결성 위반 예외
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    protected ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        log.error("[Database Exception] Data integrity violation", e);
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ErrorCode.ZONE_ALREADY_EXISTS));
    }

    /**
     * 빈 결과 예외
     */
    @ExceptionHandler(EmptyResultDataAccessException.class)
    protected ResponseEntity<ErrorResponse> handleEmptyResultDataAccessException(EmptyResultDataAccessException e) {
        log.error("[Database Exception] Empty result", e);
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ErrorCode.ZONE_NOT_FOUND));
    }

    // ==================== AWS S3 예외 처리 ====================

    /**
     * S3 예외 처리
     */
    @ExceptionHandler(S3Exception.class)
    protected ResponseEntity<ErrorResponse> handleS3Exception(S3Exception e) {
        log.error("[S3 Exception] {}: {}", e.awsErrorDetails().errorCode(), e.getMessage(), e);
        
        ErrorCode errorCode = switch (e.awsErrorDetails().errorCode()) {
            case "NoSuchKey" -> ErrorCode.FILE_NOT_FOUND;
            case "AccessDenied" -> ErrorCode.S3_ACCESS_ERROR;
            default -> ErrorCode.S3_UPLOAD_ERROR;
        };
        
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(errorCode));
    }

    /**
     * S3 NoSuchKey 예외
     */
    @ExceptionHandler(NoSuchKeyException.class)
    protected ResponseEntity<ErrorResponse> handleNoSuchKeyException(NoSuchKeyException e) {
        log.error("[S3 Exception] No such key", e);
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ErrorCode.FILE_NOT_FOUND));
    }

    // ==================== I/O 예외 처리 ====================

    /**
     * I/O 예외 처리
     */
    @ExceptionHandler(IOException.class)
    protected ResponseEntity<ErrorResponse> handleIOException(IOException e) {
        log.error("[IO Exception] File operation error", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.FILE_UPLOAD_ERROR));
    }

    // ==================== 기타 예외 처리 ====================

    /**
     * 런타임 예외 처리
     */
    @ExceptionHandler(RuntimeException.class)
    protected ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException e) {
        log.error("[Runtime Exception] Unexpected runtime error", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    /**
     * 모든 예외의 최종 처리
     */
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("[Exception] Unexpected error", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
