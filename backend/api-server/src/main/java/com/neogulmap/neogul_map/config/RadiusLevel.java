package com.neogulmap.neogul_map.config;

import lombok.Getter;

@Getter
public enum RadiusLevel {
    
    // 지도 줌 레벨별 반경 (미터 단위)
    LEVEL_1(1, 1.0),           // 1미터
    LEVEL_2(2, 5.0),           // 5미터
    LEVEL_3(3, 30.0),          // 30미터
    LEVEL_4(4, 50.0),          // 50미터
    LEVEL_5(5, 100.0),         // 100미터
    LEVEL_6(6, 250.0),         // 250미터
    LEVEL_7(7, 500.0),         // 500미터
    LEVEL_8(8, 1000.0),        // 1키로미터
    LEVEL_9(9, 2000.0),        // 2키로미터
    LEVEL_10(10, 4000.0),      // 4키로미터
    LEVEL_11(11, 9000.0),      // 9키로미터
    LEVEL_12(12, 19000.0),     // 19키로미터
    LEVEL_13(13, 38000.0),     // 38키로미터
    LEVEL_14(14, 76000.0),     // 76키로미터
    LEVEL_15(15, 152000.0);    // 152키로미터
    
    private final int zoomLevel;
    private final double radiusMeters;
    
    RadiusLevel(int zoomLevel, double radiusMeters) {
        this.zoomLevel = zoomLevel;
        this.radiusMeters = radiusMeters;
    }
    
    // 줌 레벨에 따른 반경(미터) 반환
    public static double getRadiusByZoomLevel(int zoomLevel) {
        for (RadiusLevel level : values()) {
            if (level.zoomLevel == zoomLevel) {
                return level.radiusMeters;
            }
        }
        // 기본값: 줌 레벨 8 (1km)
        return LEVEL_8.getRadiusMeters();
    }
    
    // 줌 레벨에 따른 반경(킬로미터) 반환
    public static double getRadiusKmByZoomLevel(int zoomLevel) {
        return getRadiusByZoomLevel(zoomLevel) / 1000.0;
    }
    
    // 반경(미터)에 따른 적절한 줌 레벨 반환
    public static int getZoomLevelByRadius(double radiusMeters) {
        for (RadiusLevel level : values()) {
            if (radiusMeters <= level.radiusMeters) {
                return level.zoomLevel;
            }
        }
        // 최대 줌 레벨
        return LEVEL_15.getZoomLevel();
    }
    
    // 반경(킬로미터)에 따른 적절한 줌 레벨 반환
    public static int getZoomLevelByRadiusKm(double radiusKm) {
        return getZoomLevelByRadius(radiusKm * 1000.0);
    }
}
