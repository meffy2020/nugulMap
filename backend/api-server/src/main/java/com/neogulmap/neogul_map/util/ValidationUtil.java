package com.neogulmap.neogul_map.util;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * 공통 검증 유틸리티
 */
public class ValidationUtil {

    // 이메일 패턴
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    // 닉네임 패턴 (2-20자, 영문, 한글, 숫자)
    private static final Pattern NICKNAME_PATTERN = Pattern.compile(
        "^[a-zA-Z가-힣0-9]{2,20}$"
    );

    // 위도 범위
    private static final double MIN_LATITUDE = -90.0;
    private static final double MAX_LATITUDE = 90.0;

    // 경도 범위
    private static final double MIN_LONGITUDE = -180.0;
    private static final double MAX_LONGITUDE = 180.0;

    // 최대 문자열 길이
    private static final int MAX_STRING_LENGTH = 1000;

    /**
     * 이메일 유효성 검사
     */
    public static boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * 닉네임 유효성 검사
     */
    public static boolean isValidNickname(String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) {
            return false;
        }
        return NICKNAME_PATTERN.matcher(nickname).matches();
    }

    /**
     * 위도 유효성 검사
     */
    public static boolean isValidLatitude(Double latitude) {
        return latitude != null && latitude >= MIN_LATITUDE && latitude <= MAX_LATITUDE;
    }

    /**
     * 경도 유효성 검사
     */
    public static boolean isValidLongitude(Double longitude) {
        return longitude != null && longitude >= MIN_LONGITUDE && longitude <= MAX_LONGITUDE;
    }

    /**
     * 문자열 길이 검사
     */
    public static boolean isValidStringLength(String str) {
        return str == null || str.length() <= MAX_STRING_LENGTH;
    }

    /**
     * 필수 필드 검사
     */
    public static boolean isNotEmpty(String str) {
        return str != null && !str.trim().isEmpty();
    }

    /**
     * 숫자 범위 검사
     */
    public static boolean isInRange(int value, int min, int max) {
        return value >= min && value <= max;
    }

    /**
     * 입력값 정리 (기본적인 공백 제거)
     */
    public static String cleanInput(String input) {
        if (input == null) {
            return null;
        }
        return input.trim();
    }

    /**
     * Zone 주소 검증
     */
    public static boolean isValidZoneAddress(String address) {
        return isNotEmpty(address) && isValidStringLength(address) && address.length() >= 5;
    }

    /**
     * Zone 설명 검증
     */
    public static boolean isValidZoneDescription(String description) {
        return description == null || isValidStringLength(description);
    }

    /**
     * BigDecimal 위도 유효성 검사
     */
    public static boolean isValidLatitude(BigDecimal latitude) {
        if (latitude == null) return false;
        double lat = latitude.doubleValue();
        return lat >= MIN_LATITUDE && lat <= MAX_LATITUDE;
    }

    /**
     * BigDecimal 경도 유효성 검사
     */
    public static boolean isValidLongitude(BigDecimal longitude) {
        if (longitude == null) return false;
        double lng = longitude.doubleValue();
        return lng >= MIN_LONGITUDE && lng <= MAX_LONGITUDE;
    }
}
