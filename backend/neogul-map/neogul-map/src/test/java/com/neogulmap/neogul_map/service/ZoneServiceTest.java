package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import com.neogulmap.neogul_map.dto.ZoneRequest;
import com.neogulmap.neogul_map.dto.ZoneResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class ZoneServiceTest {

    @Autowired
    private ZoneService zoneService;

    @Test
    @DisplayName("흡연구역 정보를 받아 성공적으로 저장한다.")
    void createZone_Success() {
        // given (주어진 상황)
        ZoneRequest request = ZoneRequest.builder()
                .address("서울시 강남구 테헤란로 231")
                .latitude(new BigDecimal("37.506901"))
                .longitude(new BigDecimal("127.045561"))
                .region("서울 강남구")
                .build();

        // when (무엇을 할 때)
        ZoneResponse response = zoneService.createZone(request);

        // then (결과는 이래야 한다)
        assertThat(response.getId()).isNotNull();
        assertThat(response.getAddress()).isEqualTo("서울시 강남구 테헤란로 231");
    }

    @Test
    @DisplayName("이미 존재하는 주소로 흡연구역을 생성하려고 하면 예외가 발생한다.")
    void createZone_Fail_With_Duplicate_Address() {
        // given
        ZoneRequest request1 = ZoneRequest.builder()
                .address("서울시 강남구 테헤란로 231")
                .latitude(new BigDecimal("37.506901"))
                .longitude(new BigDecimal("127.045561"))
                .region("서울 강남구")
                .build();
        zoneService.createZone(request1);

        ZoneRequest request2 = ZoneRequest.builder()
                .address("서울시 강남구 테헤란로 231") // Same address
                .latitude(new BigDecimal("37.506902"))
                .longitude(new BigDecimal("127.045562"))
                .region("서울 강남구")
                .build();

        // when & then
        assertThrows(BusinessBaseException.class, () -> {
            zoneService.createZone(request2);
        });
    }
}
