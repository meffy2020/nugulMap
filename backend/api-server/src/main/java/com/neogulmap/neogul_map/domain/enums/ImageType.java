package com.neogulmap.neogul_map.domain.enums;

/**
 * 이미지 타입 열거형
 */
public enum ImageType {
    PROFILE("profiles", "profile"),
    ZONE("zones", "zone");

    private final String directory;
    private final String prefix;

    ImageType(String directory, String prefix) {
        this.directory = directory;
        this.prefix = prefix;
    }

    public String getDirectory() {
        return directory;
    }

    public String getPrefix() {
        return prefix;
    }
}
