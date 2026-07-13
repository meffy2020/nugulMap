package com.neogulmap.neogul_map.dto;

import com.neogulmap.neogul_map.config.web.PublicUrlBuilder;
import com.neogulmap.neogul_map.domain.Zone;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ZoneSubmissionOperatorResponse(
        Integer zoneId,
        Long creatorId,
        String region,
        String type,
        String subtype,
        String address,
        String description,
        BigDecimal latitude,
        BigDecimal longitude,
        String imageUrl,
        String status,
        LocalDate submittedDate
) {
    public static ZoneSubmissionOperatorResponse from(Zone zone) {
        Long creatorId = zone.getCreator() == null ? null : zone.getCreator().getId();
        return new ZoneSubmissionOperatorResponse(
                zone.getId(),
                creatorId,
                zone.getRegion(),
                zone.getType(),
                zone.getSubtype(),
                zone.getAddress(),
                zone.getDescription(),
                zone.getLatitude(),
                zone.getLongitude(),
                PublicUrlBuilder.imageUrl(zone.getImage()),
                zone.getPublicationStatus().name(),
                zone.getDate()
        );
    }
}
