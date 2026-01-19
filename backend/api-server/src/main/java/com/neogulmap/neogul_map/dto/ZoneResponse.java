package com.neogulmap.neogul_map.dto;

import com.neogulmap.neogul_map.domain.Zone;
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

    public static ZoneResponse from(Zone zone) {
        // creator가 있으면 creator의 이메일 사용, 없으면 기존 user 필드 사용 (하위 호환성)
        String userEmail = zone.getCreator() != null && zone.getCreator().getEmail() != null
                ? zone.getCreator().getEmail()
                : zone.getUser();
        
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
                .user(userEmail)
                .image(zone.getImage())
                .build();
    }
}
