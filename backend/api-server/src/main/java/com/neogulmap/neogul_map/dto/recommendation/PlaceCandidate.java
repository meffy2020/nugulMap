package com.neogulmap.neogul_map.dto.recommendation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record PlaceCandidate(
        String id,
        String name,
        String category,
        String address,
        double latitude,
        double longitude,
        Integer distanceMeters,
        String placeUrl,
        PlaceProvider provider,
        CandidateFreshness freshness,
        Set<String> tags
) {
    public PlaceCandidate {
        id = requireText(id, "id");
        name = requireText(name, "name");
        category = clean(category);
        address = clean(address);
        placeUrl = clean(placeUrl);
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude is outside WGS84 range");
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude is outside WGS84 range");
        }
        if (distanceMeters != null && distanceMeters < 0) {
            throw new IllegalArgumentException("distanceMeters must not be negative");
        }
        provider = Objects.requireNonNull(provider, "provider");
        freshness = Objects.requireNonNull(freshness, "freshness");

        LinkedHashSet<String> copiedTags = new LinkedHashSet<>();
        if (tags != null) {
            tags.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(tag -> !tag.isEmpty())
                    .forEach(copiedTags::add);
        }
        tags = Collections.unmodifiableSet(copiedTags);
    }

    private static String requireText(String value, String field) {
        String cleaned = clean(value);
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return cleaned;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
