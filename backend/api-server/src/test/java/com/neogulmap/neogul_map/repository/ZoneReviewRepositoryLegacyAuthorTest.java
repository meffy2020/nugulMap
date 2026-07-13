package com.neogulmap.neogul_map.repository;

import com.neogulmap.neogul_map.domain.Zone;
import com.neogulmap.neogul_map.domain.ZoneReview;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class ZoneReviewRepositoryLegacyAuthorTest {

    @Autowired private ZoneRepository zoneRepository;
    @Autowired private ZoneReviewRepository zoneReviewRepository;

    @Test
    void leftJoinQueriesKeepAnonymousLegacyReviewAddressableById() {
        Zone zone = zoneRepository.save(Zone.builder()
                .region("성수")
                .type("흡연구역")
                .latitude(BigDecimal.valueOf(37.54))
                .longitude(BigDecimal.valueOf(127.05))
                .address("서울 성동구 레거시 리뷰 테스트")
                .build());
        ZoneReview review = zoneReviewRepository.save(ZoneReview.builder()
                .zone(zone)
                .author(null)
                .content("작성자 계정이 삭제된 리뷰")
                .build());
        zoneReviewRepository.flush();

        assertThat(zoneReviewRepository.findByIdWithAuthorAndZone(review.getId()))
                .hasValueSatisfying(found -> {
                    assertThat(found.getAuthor()).isNull();
                    assertThat(found.getZone().getId()).isEqualTo(zone.getId());
                });
        assertThat(zoneReviewRepository.findByZoneIdWithAuthorOrderByCreatedAtDesc(zone.getId()))
                .singleElement()
                .satisfies(found -> assertThat(found.getAuthor()).isNull());
    }
}
