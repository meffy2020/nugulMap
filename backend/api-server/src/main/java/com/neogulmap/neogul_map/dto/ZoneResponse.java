package com.neogulmap.neogul_map.dto;

import com.neogulmap.neogul_map.domain.Zone;
import com.neogulmap.neogul_map.domain.enums.ZonePublicationStatus;
import com.neogulmap.neogul_map.config.web.PublicUrlBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
public class ZoneResponse {
    private Integer id;
    private String region;
    private String type;
    private String subtype;
    private String description;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String size;
    private LocalDate date;
    private String address;
    private String user;
    private String image;
    private String imageUrl;
    private ZonePublicationStatus publicationStatus;

    public static ZoneResponse from(Zone zone) {
        if (zone == null) return null;

        String publicUserLabel = PublicUserLabel.from(zone.getCreator());
        
        return ZoneResponse.builder()
                .id(zone.getId())
                .region(zone.getRegion())
                .type(zone.getType())
                .subtype(zone.getSubtype())
                .description(zone.getDescription())
                .latitude(zone.getLatitude())
                .longitude(zone.getLongitude())
                .size(zone.getSize())
                .date(zone.getDate())
                .address(zone.getAddress())
                .user(publicUserLabel)
                .image(zone.getImage())
                .imageUrl(PublicUrlBuilder.imageUrl(zone.getImage()))
                .publicationStatus(zone.getPublicationStatus())
                .build();
    }
}
