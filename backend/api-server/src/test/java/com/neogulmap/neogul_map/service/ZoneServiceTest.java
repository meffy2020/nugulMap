package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.domain.Zone;
import com.neogulmap.neogul_map.domain.enums.ImageType;
import com.neogulmap.neogul_map.dto.ZoneRequest;
import com.neogulmap.neogul_map.dto.ZoneResponse;
import com.neogulmap.neogul_map.repository.ZoneRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZoneServiceTest {

    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private ImageService imageService;

    @InjectMocks
    private ZoneService zoneService;

    @Test
    @DisplayName("Zone 생성 시 이미지 처리 결과와 작성자 정보를 응답에 반영한다")
    void createZoneWithImageAndCreator() {
        User creator = User.builder()
                .id(1L)
                .email("owner@nugulmap.com")
                .nickname("작성자")
                .build();
        MultipartFile image = mock(MultipartFile.class);
        when(image.isEmpty()).thenReturn(false);
        when(imageService.processImage(image, ImageType.ZONE)).thenReturn("zones/created.jpg");
        when(zoneRepository.save(any(Zone.class))).thenAnswer(invocation -> {
            Zone zone = invocation.getArgument(0);
            zone.setId(10);
            return zone;
        });

        ZoneResponse response = zoneService.createZone(validZoneRequest(), image, creator);

        assertThat(response.getId()).isEqualTo(10);
        assertThat(response.getUser()).isEqualTo("owner@nugulmap.com");
        assertThat(response.getImage()).isEqualTo("zones/created.jpg");
        assertThat(response.getImageUrl()).isEqualTo("/images/created.jpg");
    }

    @Test
    @DisplayName("타인이 등록한 Zone은 수정할 수 없다")
    void updateZoneRejectsNonOwner() {
        Zone zone = validZone();
        zone.setCreator(User.builder().id(1L).email("owner@nugulmap.com").build());
        when(zoneRepository.findById(10)).thenReturn(Optional.of(zone));

        User attacker = User.builder().id(2L).email("attacker@nugulmap.com").build();

        assertThatThrownBy(() -> zoneService.updateZone(10, validZoneRequest(), null, attacker))
                .isInstanceOf(BusinessBaseException.class)
                .hasMessageContaining("본인이 등록한 장소");

        verify(imageService, never()).processImage(any(), any());
    }

    @Test
    @DisplayName("본인이 등록한 Zone은 삭제할 수 있다")
    void deleteZoneAllowsOwner() {
        Zone zone = validZone();
        zone.setImage("zones/delete.jpg");
        zone.setCreator(User.builder().id(1L).email("owner@nugulmap.com").build());
        when(zoneRepository.findById(10)).thenReturn(Optional.of(zone));

        zoneService.deleteZone(10, User.builder().id(1L).email("owner@nugulmap.com").build());

        verify(imageService).deleteImage("zones/delete.jpg", ImageType.ZONE);
        verify(zoneRepository).deleteById(10);
    }

    private ZoneRequest validZoneRequest() {
        return ZoneRequest.builder()
                .region("서울")
                .type("흡연구역")
                .subtype("실외")
                .description("테스트")
                .latitude(BigDecimal.valueOf(37.5665))
                .longitude(BigDecimal.valueOf(126.9780))
                .address("서울특별시 중구 세종대로")
                .build();
    }

    private Zone validZone() {
        return Zone.builder()
                .id(10)
                .region("서울")
                .type("흡연구역")
                .subtype("실외")
                .description("테스트")
                .latitude(BigDecimal.valueOf(37.5665))
                .longitude(BigDecimal.valueOf(126.9780))
                .address("서울특별시 중구 세종대로")
                .build();
    }
}
