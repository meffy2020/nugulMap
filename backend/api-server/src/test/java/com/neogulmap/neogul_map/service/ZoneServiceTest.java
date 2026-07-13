package com.neogulmap.neogul_map.service;

import com.neogulmap.neogul_map.config.exceptionHandling.exception.BusinessBaseException;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.domain.Zone;
import com.neogulmap.neogul_map.domain.enums.ImageType;
import com.neogulmap.neogul_map.domain.enums.ZonePublicationStatus;
import com.neogulmap.neogul_map.dto.ZoneRequest;
import com.neogulmap.neogul_map.dto.ZoneResponse;
import com.neogulmap.neogul_map.repository.ZoneRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZoneServiceTest {

    @Mock
    private ZoneRepository zoneRepository;

    @Mock
    private ImageService imageService;

    @Mock
    private ReviewContentPolicy contentPolicy;

    @InjectMocks
    private ZoneService zoneService;

    @Test
    @DisplayName("Zone 생성 응답은 작성자 이메일 대신 공개 닉네임을 사용한다")
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
        assertThat(response.getUser()).isEqualTo("작성자");
        assertThat(response.getUser()).doesNotContain("@");
        assertThat(response.getImage()).isEqualTo("zones/created.jpg");
        assertThat(response.getImageUrl()).isEqualTo("/images/created.jpg");
        assertThat(response.getPublicationStatus()).isEqualTo(ZonePublicationStatus.PENDING);
        verify(contentPolicy).ensureAllowed("테스트");
        verify(contentPolicy).ensureAllowed("서울특별시 중구 세종대로");
    }

    @Test
    @DisplayName("닉네임이 없는 Zone 작성자의 이메일과 레거시 creator 값은 공개하지 않는다")
    void zoneResponseNeverFallsBackToCreatorEmail() {
        Zone zone = validZone();
        zone.setCreator(User.builder()
                .id(1L)
                .email("private-owner@nugulmap.com")
                .nickname("  ")
                .build());
        zone.setUser("legacy-owner@nugulmap.com");
        when(zoneRepository.findByIdAndPublicationStatus(10, ZonePublicationStatus.PUBLISHED))
                .thenReturn(Optional.of(zone));

        ZoneResponse response = zoneService.getZone(10);

        assertThat(response.getUser()).isEqualTo("익명사용자");
    }

    @Test
    @DisplayName("운영자 검토 대기 장소와 이미지는 공개 목록에 노출하지 않는다")
    void publicZoneListExcludesPendingSubmissions() {
        Zone published = validZone();
        published.setId(10);
        Zone pending = validZone();
        pending.setId(11);
        pending.setAddress("서울특별시 성동구 연무장길 1");
        pending.setPublicationStatus(ZonePublicationStatus.PENDING);
        when(zoneRepository.findByPublicationStatusOrderByDateAscIdAsc(
                eq(ZonePublicationStatus.PUBLISHED),
                any(Pageable.class)
        ))
                .thenReturn(List.of(published, pending));

        List<ZoneResponse> zones = zoneService.getAllZones();

        assertThat(zones).extracting(ZoneResponse::getId).containsExactly(10);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(zoneRepository).findByPublicationStatusOrderByDateAscIdAsc(
                eq(ZonePublicationStatus.PUBLISHED),
                pageable.capture()
        );
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("전체 공개 목록의 요청 상한을 초과하면 조회하지 않는다")
    void publicZoneListRejectsExcessiveLimit() {
        assertThatThrownBy(() -> zoneService.getAllZones(201))
                .isInstanceOf(com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException.class)
                .hasMessageContaining("200");

        verifyNoInteractions(zoneRepository);
    }

    @Test
    @DisplayName("페이지 조회 크기는 공개 목록 최대 상한으로 축소한다")
    void pagedPublicZoneListCapsRequestedPageSize() {
        when(zoneRepository.findAllByPublicationStatus(
                eq(ZonePublicationStatus.PUBLISHED),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(3, 200), 0));

        zoneService.getAllZones(PageRequest.of(3, 10_000));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(zoneRepository).findAllByPublicationStatus(
                eq(ZonePublicationStatus.PUBLISHED),
                pageable.capture()
        );
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(3);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(200);
    }

    @Test
    @DisplayName("정상 서울 지도 영역은 요청 limit만큼 제한해 조회한다")
    void boundedZoneListUsesValidatedLimit() {
        when(zoneRepository.findByLocationBounds(
                eq(37.48),
                eq(37.60),
                eq(126.88),
                eq(127.12),
                eq(ZonePublicationStatus.PUBLISHED),
                any(Pageable.class)
        )).thenReturn(List.of(validZone()));

        List<ZoneResponse> zones = zoneService.getZonesByBounds(
                37.48,
                37.60,
                126.88,
                127.12,
                42
        );

        assertThat(zones).hasSize(1);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(zoneRepository).findByLocationBounds(
                eq(37.48),
                eq(37.60),
                eq(126.88),
                eq(127.12),
                eq(ZonePublicationStatus.PUBLISHED),
                pageable.capture()
        );
        assertThat(pageable.getValue().getPageSize()).isEqualTo(42);
    }

    @Test
    @DisplayName("뒤집힌 지도 영역은 DB 조회 전에 거부한다")
    void boundedZoneListRejectsReversedBounds() {
        assertThatThrownBy(() -> zoneService.getZonesByBounds(
                37.60,
                37.48,
                126.88,
                127.12,
                200
        )).isInstanceOf(com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException.class);

        verifyNoInteractions(zoneRepository);
    }

    @Test
    @DisplayName("지나치게 큰 지도 영역은 DB 조회 전에 거부한다")
    void boundedZoneListRejectsExcessiveArea() {
        assertThatThrownBy(() -> zoneService.getZonesByBounds(
                30.0,
                35.0,
                120.0,
                125.0,
                200
        )).isInstanceOf(com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException.class)
                .hasMessageContaining("너무 큽니다");

        verifyNoInteractions(zoneRepository);
    }

    @Test
    @DisplayName("유한하지 않은 좌표와 과도한 bounds limit은 거부한다")
    void boundedZoneListRejectsNonFiniteCoordinatesAndLimit() {
        assertThatThrownBy(() -> zoneService.getZonesByBounds(
                Double.NaN,
                37.60,
                126.88,
                127.12,
                200
        )).isInstanceOf(com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException.class);

        assertThatThrownBy(() -> zoneService.getZonesByBounds(
                37.48,
                37.60,
                126.88,
                127.12,
                501
        )).isInstanceOf(com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException.class)
                .hasMessageContaining("500");

        verifyNoInteractions(zoneRepository);
    }

    @Test
    @DisplayName("반경 조회 응답도 요청 limit으로 제한한다")
    void radiusZoneListUsesValidatedLimit() {
        Zone first = validZone();
        first.setId(10);
        Zone second = validZone();
        second.setId(11);
        when(zoneRepository.findNearbyZones(
                eq(37.5665),
                eq(126.9780),
                eq(1.0),
                eq(ZonePublicationStatus.PUBLISHED),
                any(Pageable.class)
        ))
                .thenReturn(List.of(first, second));

        List<ZoneResponse> zones = zoneService.searchZonesByRadius(
                37.5665,
                126.9780,
                1_000,
                1
        );

        assertThat(zones).extracting(ZoneResponse::getId).containsExactly(10);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(zoneRepository).findNearbyZones(
                eq(37.5665),
                eq(126.9780),
                eq(1.0),
                eq(ZonePublicationStatus.PUBLISHED),
                pageable.capture()
        );
        assertThat(pageable.getValue().getPageSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("반경 조회는 잘못된 좌표와 과도한 반경을 DB 조회 전에 거부한다")
    void radiusZoneListRejectsInvalidLocationAndRadius() {
        assertThatThrownBy(() -> zoneService.searchZonesByRadius(Double.NaN, 126.9780, 1_000, 20))
                .isInstanceOf(com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException.class);
        assertThatThrownBy(() -> zoneService.searchZonesByRadius(37.5665, 126.9780, -1, 20))
                .isInstanceOf(com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException.class);
        assertThatThrownBy(() -> zoneService.searchZonesByRadius(37.5665, 126.9780, 50_001, 20))
                .isInstanceOf(com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException.class)
                .hasMessageContaining("50km");

        verifyNoInteractions(zoneRepository);
    }

    @Test
    @DisplayName("키워드 검색은 정규화한 검색어와 제한된 Pageable을 사용한다")
    void keywordSearchUsesBoundedPageable() {
        Zone zone = validZone();
        when(zoneRepository.findByKeyword(
                eq("성수"),
                eq(ZonePublicationStatus.PUBLISHED),
                any(Pageable.class)
        )).thenReturn(List.of(zone));

        List<ZoneResponse> zones = zoneService.searchZones("  성수  ", null, null, 15);

        assertThat(zones).hasSize(1);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(zoneRepository).findByKeyword(
                eq("성수"),
                eq(ZonePublicationStatus.PUBLISHED),
                pageable.capture()
        );
        assertThat(pageable.getValue().getPageSize()).isEqualTo(15);
    }

    @Test
    @DisplayName("키워드 검색은 일부 위치 파라미터와 과도한 요청을 거부한다")
    void keywordSearchRejectsInvalidParameters() {
        assertThatThrownBy(() -> zoneService.searchZones("성수", 37.55, null, 20))
                .isInstanceOf(com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException.class)
                .hasMessageContaining("함께");
        assertThatThrownBy(() -> zoneService.searchZones("성수", null, null, 201))
                .isInstanceOf(com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException.class)
                .hasMessageContaining("200");
        assertThatThrownBy(() -> zoneService.searchZones("가".repeat(101), null, null, 20))
                .isInstanceOf(com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException.class)
                .hasMessageContaining("100자");

        verifyNoInteractions(zoneRepository);
    }

    @Test
    @DisplayName("검토 대기 장소는 공개 상세 조회에서 찾을 수 없는 것으로 처리한다")
    void pendingZoneIsNotAvailableThroughPublicDetail() {
        when(zoneRepository.findByIdAndPublicationStatus(11, ZonePublicationStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> zoneService.getZone(11))
                .isInstanceOf(com.neogulmap.neogul_map.config.exceptionHandling.exception.NotFoundException.class);
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
    @DisplayName("공개된 장소를 사용자가 수정하면 다시 검토 대기로 전환한다")
    void userUpdateReturnsPublishedZoneToPendingModeration() {
        User owner = User.builder().id(1L).email("owner@nugulmap.com").build();
        Zone zone = validZone();
        zone.setCreator(owner);
        when(zoneRepository.findById(10)).thenReturn(Optional.of(zone));

        ZoneResponse response = zoneService.updateZone(10, validZoneRequest(), null, owner);

        assertThat(response.getPublicationStatus()).isEqualTo(ZonePublicationStatus.PENDING);
        assertThat(zone.getPublicationStatus()).isEqualTo(ZonePublicationStatus.PENDING);
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
