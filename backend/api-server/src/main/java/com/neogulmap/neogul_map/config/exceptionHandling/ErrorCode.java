package com.neogulmap.neogul_map.config.exceptionHandling;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // 404 Not Found
    NOT_FOUND(HttpStatus.NOT_FOUND, "404", "존재하지 않는 리소스입니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "사용자를 찾을 수 없습니다."),
    EMAIL_DUPLICATION(HttpStatus.CONFLICT, "U002", "이미 존재하는 이메일입니다."),
    LOGIN_INPUT_INVALID(HttpStatus.BAD_REQUEST, "U003", "로그인 정보가 올바르지 않습니다."),
    USER_DATA_INTEGRITY_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "U004", "사용자 데이터 무결성 오류가 발생했습니다."),
    
    // Profile Image
    PROFILE_IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "P001", "프로필 이미지가 필요합니다."),
    PROFILE_IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "P002", "이미지 크기는 10MB 이하여야 합니다."),
    PROFILE_IMAGE_INVALID_TYPE(HttpStatus.BAD_REQUEST, "P003", "이미지 파일만 업로드 가능합니다."),
    PROFILE_IMAGE_PROCESSING_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "P004", "이미지 처리 중 오류가 발생했습니다."),
    
    // Zone
    ZONE_NOT_FOUND(HttpStatus.NOT_FOUND, "Z001", "장소를를 찾을 수 없습니다."),
    ZONE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Z002", "이미 존재하는 장소소입니다.");
    

    private final HttpStatus status;  // HTTP 상태 코드
    private final String code;  // 클라이언트가 에러를 식별할 수 있도록 하는 에러 코드
    private final String message;  // 사용자에게 제공할 에러 메시지
    
    // 생성자: 각 에러 코드에 대한 속성을 설정
    ErrorCode(HttpStatus status, String code, String message) {
            this.status = status;
            this.code = code;
            this.message = message;
        }

}