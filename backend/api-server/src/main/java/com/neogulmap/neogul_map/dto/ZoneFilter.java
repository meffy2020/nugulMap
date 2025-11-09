package com.neogulmap.neogul_map.dto;

/**
 * Zone 검색을 위한 필터 DTO
 * 모든 검색 조건을 선택적으로 적용할 수 있도록 설계
 */
public record ZoneFilter(
    String keyword,     // 키워드 검색 (지역, 주소, 설명, 타입, 서브타입에서 검색)
    String region,      // 지역 필터
    String type,        // 타입 필터
    String subtype,     // 서브타입 필터
    String size,        // 크기 필터
    String user,        // 사용자 필터
    Double latitude,    // 위도 (위치 기반 검색용)
    Double longitude,   // 경도 (위치 기반 검색용)
    Double radiusKm,    // 반경 (km)
    Integer zoomLevel   // 줌 레벨 (반경 자동 계산용)
) {
    
    /**
     * 빈 필터 생성 (모든 조건이 null)
     */
    public static ZoneFilter empty() {
        return new ZoneFilter(null, null, null, null, null, null, null, null, null, null);
    }
    
    /**
     * 키워드 검색용 필터 생성
     */
    public static ZoneFilter keyword(String keyword) {
        return new ZoneFilter(keyword, null, null, null, null, null, null, null, null, null);
    }
    
    /**
     * 지역 + 타입 검색용 필터 생성
     */
    public static ZoneFilter regionAndType(String region, String type) {
        return new ZoneFilter(null, region, type, null, null, null, null, null, null, null);
    }
    
    /**
     * 위치 기반 검색용 필터 생성
     */
    public static ZoneFilter location(Double latitude, Double longitude, Double radiusKm) {
        return new ZoneFilter(null, null, null, null, null, null, latitude, longitude, radiusKm, null);
    }
    
    /**
     * 줌 레벨 기반 검색용 필터 생성
     */
    public static ZoneFilter zoomLevel(Double latitude, Double longitude, Integer zoomLevel) {
        return new ZoneFilter(null, null, null, null, null, null, latitude, longitude, null, zoomLevel);
    }
    
    /**
     * 필터가 비어있는지 확인
     */
    public boolean isEmpty() {
        return keyword == null && region == null && type == null && subtype == null && 
               size == null && user == null && latitude == null && longitude == null && 
               radiusKm == null && zoomLevel == null;
    }
    
    /**
     * 위치 기반 검색인지 확인
     */
    public boolean isLocationBased() {
        return latitude != null && longitude != null && (radiusKm != null || zoomLevel != null);
    }
    
    /**
     * 키워드 검색인지 확인
     */
    public boolean isKeywordSearch() {
        return keyword != null && !keyword.trim().isEmpty();
    }
}
