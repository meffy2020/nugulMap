package com.neogulmap.neogul_map.dto;

import com.neogulmap.neogul_map.config.web.PublicUrlBuilder;
import com.neogulmap.neogul_map.domain.User;
import com.neogulmap.neogul_map.domain.Zone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PublicImageUrlTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("ZoneResponse는 네이티브 앱에서 바로 읽을 수 있는 절대 이미지 URL을 제공한다")
    void zoneResponseProvidesAbsoluteImageUrl() {
        bindRequest();
        Zone zone = Zone.builder()
                .id(1)
                .region("서울")
                .type("흡연구역")
                .latitude(BigDecimal.valueOf(37.5665))
                .longitude(BigDecimal.valueOf(126.9780))
                .address("서울특별시 중구 세종대로")
                .image("zones/sample.jpg")
                .build();

        ZoneResponse response = ZoneResponse.from(zone);

        assertThat(response.getImage()).isEqualTo("zones/sample.jpg");
        assertThat(response.getImageUrl()).isEqualTo("https://api.nugulmap.com/api/images/sample.jpg");
    }

    @Test
    @DisplayName("UserResponse는 프로필 이미지 절대 URL을 함께 제공한다")
    void userResponseProvidesAbsoluteProfileImageUrl() {
        bindRequest();
        User user = User.builder()
                .id(1L)
                .email("user@nugulmap.com")
                .oauthId("kakao-1")
                .oauthProvider("kakao")
                .nickname("너굴")
                .profileImage("profiles/me.png")
                .build();

        UserResponse response = UserResponse.from(user);

        assertThat(response.getProfileImage()).isEqualTo("/images/profiles/me.png");
        assertThat(response.getProfileImageUrl()).isEqualTo("https://api.nugulmap.com/api/images/me.png");
    }

    @Test
    @DisplayName("요청 컨텍스트가 없으면 하위 호환 상대 경로를 반환한다")
    void imageUrlFallsBackToRelativePathWithoutRequest() {
        assertThat(PublicUrlBuilder.imageUrl("zones/sample.jpg")).isEqualTo("/images/sample.jpg");
    }

    private void bindRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerName("api.nugulmap.com");
        request.setServerPort(443);
        request.setContextPath("/api");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
