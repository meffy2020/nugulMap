package com.neogulmap.neogul_map.config.web;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Component
public class PublicUrlBuilder {

    private static final String IMAGE_PATH = "/images/";

    public static String imageUrl(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }

        String normalized = fileName.trim();
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }

        normalized = stripKnownPathPrefix(normalized);

        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path(IMAGE_PATH)
                    .path(normalized)
                    .build()
                    .toUriString();
        } catch (IllegalStateException ex) {
            return IMAGE_PATH + normalized;
        }
    }

    private static String stripKnownPathPrefix(String fileName) {
        String normalized = fileName;
        if (normalized.startsWith(IMAGE_PATH)) {
            normalized = normalized.substring(IMAGE_PATH.length());
        }
        if (normalized.startsWith("profiles/")) {
            return normalized.substring("profiles/".length());
        }
        if (normalized.startsWith("zones/")) {
            return normalized.substring("zones/".length());
        }
        return normalized;
    }
}
