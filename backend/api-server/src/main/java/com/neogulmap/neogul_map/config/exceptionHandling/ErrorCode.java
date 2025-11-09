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
    ZONE_NOT_FOUND(HttpStatus.NOT_FOUND, "Z001", "장소를 찾을 수 없습니다."),
    ZONE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Z002", "이미 존재하는 장소입니다."),
    ZONE_SAVE_DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Z003", "데이터 저장 중 오류가 발생했습니다."),
    ZONE_DELETE_DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Z004", "데이터 삭제 중 오류가 발생했습니다."),
    ZONE_IMAGE_UPLOAD_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Z005", "이미지 업로드 중 오류가 발생했습니다."),
    ZONE_IMAGE_DELETE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Z006", "이미지 삭제 중 오류가 발생했습니다."),
    
    // Search
    SEARCH_KEYWORD_INVALID(HttpStatus.BAD_REQUEST, "S001", "검색 키워드가 올바르지 않습니다."),
    SEARCH_KEYWORD_TOO_SHORT(HttpStatus.BAD_REQUEST, "S002", "검색 키워드는 최소 2글자 이상이어야 합니다."),
    SEARCH_KEYWORD_TOO_LONG(HttpStatus.BAD_REQUEST, "S003", "검색 키워드는 최대 100글자까지 가능합니다."),
    SEARCH_PARAMETER_INVALID(HttpStatus.BAD_REQUEST, "S004", "검색 파라미터가 올바르지 않습니다."),
    SEARCH_NO_RESULTS(HttpStatus.NOT_FOUND, "S005", "검색 결과가 없습니다."),
    SEARCH_DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "S006", "검색 중 데이터베이스 오류가 발생했습니다."),
    
    // Location & Zoom
    LOCATION_COORDINATES_INVALID(HttpStatus.BAD_REQUEST, "L001", "위치 좌표가 올바르지 않습니다."),
    LOCATION_LATITUDE_INVALID(HttpStatus.BAD_REQUEST, "L002", "위도가 올바르지 않습니다. (-90 ~ 90 범위)"),
    LOCATION_LONGITUDE_INVALID(HttpStatus.BAD_REQUEST, "L003", "경도가 올바르지 않습니다. (-180 ~ 180 범위)"),
    ZOOM_LEVEL_INVALID(HttpStatus.BAD_REQUEST, "L004", "줌 레벨이 올바르지 않습니다. (1-15 범위)"),
    ZOOM_LEVEL_TOO_LOW(HttpStatus.BAD_REQUEST, "L005", "줌 레벨이 너무 낮습니다. (최소 1)"),
    ZOOM_LEVEL_TOO_HIGH(HttpStatus.BAD_REQUEST, "L006", "줌 레벨이 너무 높습니다. (최대 15)"),
    ZOOM_LEVEL_REQUIRED(HttpStatus.BAD_REQUEST, "L007", "줌 레벨이 필요합니다."),
    RADIUS_INVALID(HttpStatus.BAD_REQUEST, "L008", "반경이 올바르지 않습니다. (0.1-100km 범위)"),
    
    // File Storage
    FILE_UPLOAD_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "F001", "파일 업로드 중 오류가 발생했습니다."),
    FILE_DELETE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "F002", "파일 삭제 중 오류가 발생했습니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "F003", "파일을 찾을 수 없습니다."),
    FILE_SIZE_TOO_LARGE(HttpStatus.BAD_REQUEST, "F004", "파일 크기가 너무 큽니다."),
    FILE_TYPE_INVALID(HttpStatus.BAD_REQUEST, "F005", "지원하지 않는 파일 형식입니다."),
    
    // S3 Storage
    S3_UPLOAD_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "S301", "S3 파일 업로드 중 오류가 발생했습니다."),
    S3_DELETE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "S302", "S3 파일 삭제 중 오류가 발생했습니다."),
    S3_ACCESS_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "S303", "S3 접근 중 오류가 발생했습니다."),
    
    // Validation
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "V001", "입력값 검증에 실패했습니다."),
    REQUIRED_FIELD_MISSING(HttpStatus.BAD_REQUEST, "V002", "필수 필드가 누락되었습니다."),
    INVALID_FORMAT(HttpStatus.BAD_REQUEST, "V003", "잘못된 형식입니다."),
    
    // System
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SYS001", "서버 내부 오류가 발생했습니다."),
    DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SYS002", "데이터베이스 오류가 발생했습니다."),
    EXTERNAL_SERVICE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SYS003", "외부 서비스 오류가 발생했습니다.");
    

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