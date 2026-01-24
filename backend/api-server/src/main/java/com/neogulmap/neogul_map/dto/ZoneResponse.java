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
        if (zone == null) return null;

        // creator 정보 안전하게 추출
        String userEmail = "익명사용자";
        try {
            if (zone.getCreator() != null) {
                userEmail = zone.getCreator().getEmail();
            } else if (zone.getUser() != null) {
                userEmail = zone.getUser();
            }
        } catch (Exception e) {
            // Lazy loading 등 예외 발생 시 기본값 유지
        }
        
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
