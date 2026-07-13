package com.neogulmap.neogul_map.controller;

import com.neogulmap.neogul_map.service.ImageService;
import com.neogulmap.neogul_map.service.ZoneModerationService;
import com.neogulmap.neogul_map.service.ZoneService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ZoneControllerReadContractTest {

    private final ZoneService zoneService = mock(ZoneService.class);
    private final ZoneController controller = new ZoneController(
            zoneService,
            mock(ImageService.class),
            mock(ZoneModerationService.class)
    );

    @Test
    void boundsResponseKeepsNativeCollectionShapeAndForwardsLimit() {
        when(zoneService.getZonesByBounds(37.48, 37.60, 126.88, 127.12, 200))
                .thenReturn(List.of());

        ResponseEntity<?> response = controller.getZonesByBounds(
                37.48,
                37.60,
                126.88,
                127.12,
                200
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isInstanceOf(Map.class);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("success")).isEqualTo(true);
        assertThat(body.get("data")).isInstanceOf(Map.class);
        Map<?, ?> data = (Map<?, ?>) body.get("data");
        assertThat(data.get("zones")).isEqualTo(List.of());
        assertThat(data.get("count")).isEqualTo(0);
        verify(zoneService).getZonesByBounds(37.48, 37.60, 126.88, 127.12, 200);
    }

    @Test
    void rootResponseKeepsNativeCollectionShapeAndForwardsLimit() {
        when(zoneService.getAllZones(100)).thenReturn(List.of());

        ResponseEntity<?> response = controller.getAllZones(null, null, null, 100);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        Map<?, ?> data = (Map<?, ?>) body.get("data");
        assertThat(data.get("zones")).isEqualTo(List.of());
        assertThat(data.get("count")).isEqualTo(0);
        verify(zoneService).getAllZones(100);
    }

    @Test
    void radiusResponseKeepsCollectionShapeAndForwardsLimit() {
        when(zoneService.searchZonesByRadius(37.55, 127.05, 2_000, 50)).thenReturn(List.of());

        ResponseEntity<?> response = controller.getAllZones(37.55, 127.05, 2.0, 50);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        Map<?, ?> data = (Map<?, ?>) body.get("data");
        assertThat(data.get("zones")).isEqualTo(List.of());
        assertThat(data.get("count")).isEqualTo(0);
        verify(zoneService).searchZonesByRadius(37.55, 127.05, 2_000, 50);
    }

    @Test
    void partialRadiusParametersAreRejectedInsteadOfReturningAllZones() {
        assertThatThrownBy(() -> controller.getAllZones(37.55, null, null, 100))
                .isInstanceOf(com.neogulmap.neogul_map.config.exceptionHandling.exception.ValidationException.class)
                .hasMessageContaining("모두 필요");
    }

    @Test
    void searchForwardsExplicitLimit() {
        when(zoneService.searchZones("성수", 37.55, 127.05, 25)).thenReturn(List.of());

        ResponseEntity<?> response = controller.searchZones("성수", 37.55, 127.05, 25);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(zoneService).searchZones("성수", 37.55, 127.05, 25);
    }
}
