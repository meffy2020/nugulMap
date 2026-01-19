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

    @Column(nullable = true)
    private LocalDate date;

    @Column(nullable = false, length = 100, unique = true)
    private String address;

    /**
     * Zone을 생성한 사용자 (FK 관계)
     * @ManyToOne 관계로 User 엔티티와 연결
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private User creator;

    /**
     * @deprecated 이메일 기반 user 필드는 하위 호환성을 위해 유지
     * 새로운 코드에서는 creator 필드를 사용하세요.
     * 향후 마이그레이션 후 제거 예정
     */
    @Deprecated
    @Column(name = "creator", length = 100, insertable = false, updatable = false)
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
        // creator는 직접 User 객체로 설정해야 함 (update 메서드에서는 제외)
        // if (request.getUser() != null) this.user = request.getUser(); // deprecated
        if (request.getImage() != null) this.image = request.getImage();
    }
}
