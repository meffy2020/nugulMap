package com.neogulmap.neogul_map.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "zone")
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String region;

    @Column(length = 50)
    private String type;

    @Column(length = 50)
    private String subtype;

    @Lob
    private String description;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(length = 50)
    private String size;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 100, unique = true)
    private String address;

    @Column(name = "creator", length = 100)
    private String user;

    @Column(length = 255)
    private String image;

    public void update(com.neogulmap.neogul_map.dto.ZoneRequest request) {
        if (request.getRegion() != null) this.region = request.getRegion();
        if (request.getType() != null) this.type = request.getType();
        if (request.getSubtype() != null) this.subtype = request.getSubtype();
        if (request.getDescription() != null) this.description = request.getDescription();
        if (request.getLatitude() != null) this.latitude = request.getLatitude();
        if (request.getLongitude() != null) this.longitude = request.getLongitude();
        if (request.getSize() != null) this.size = request.getSize();
        if (request.getAddress() != null) this.address = request.getAddress();
        if (request.getUser() != null) this.user = request.getUser();
        if (request.getImage() != null) this.image = request.getImage();
    }
}
