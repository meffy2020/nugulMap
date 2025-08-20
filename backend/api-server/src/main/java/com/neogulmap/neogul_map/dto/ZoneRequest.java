package com.neogulmap.neogul_map.dto;

import com.neogulmap.neogul_map.domain.Zone;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneRequest {
    private String region;
    private String type;
    private String subtype;
    private String description;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String size;
    private String address;
    private String user;
    private String image;

    public Zone toEntity() {
        return Zone.builder()
                .region(region)
                .type(type)
                .subtype(subtype)
                .description(description)
                .latitude(latitude)
                .longitude(longitude)
                .size(size)
                .date(LocalDate.now()) // Set current date on creation
                .address(address)
                .user(user)
                .image(image)
                .build();
    }
}
