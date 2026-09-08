package com.neogulmap.neogul_map.dto.recommendation;

public enum KakaoPlaceCategory {
    FD6("음식점"),
    CE7("카페"),
    AT4("관광명소"),
    CT1("문화시설");

    private final String tag;

    KakaoPlaceCategory(String tag) {
        this.tag = tag;
    }

    public String code() {
        return name();
    }

    public String tag() {
        return tag;
    }
}
