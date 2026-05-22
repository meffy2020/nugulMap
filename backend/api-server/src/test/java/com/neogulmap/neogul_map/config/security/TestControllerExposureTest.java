package com.neogulmap.neogul_map.config.security;

import com.neogulmap.neogul_map.config.security.jwt.TokenProvider;
import com.neogulmap.neogul_map.controller.TestController;
import com.neogulmap.neogul_map.service.ImageService;
import com.neogulmap.neogul_map.service.StorageService;
import com.neogulmap.neogul_map.service.UserService;
import com.neogulmap.neogul_map.service.ZoneService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TestControllerExposureTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestController.class)
            .withBean(UserService.class, () -> mock(UserService.class))
            .withBean(ZoneService.class, () -> mock(ZoneService.class))
            .withBean(ImageService.class, () -> mock(ImageService.class))
            .withBean(StorageService.class, () -> mock(StorageService.class))
            .withBean(TokenProvider.class, () -> mock(TokenProvider.class));

    @Test
    @DisplayName("/api/test/** 컨트롤러는 기본값으로 등록되지 않는다")
    void testEndpointsAreDisabledByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(TestController.class));
    }

    @Test
    @DisplayName("/api/test/** 컨트롤러는 명시적으로 켠 개발 환경에서만 등록된다")
    void testEndpointsRequireExplicitFlag() {
        contextRunner
                .withPropertyValues("app.test-endpoints.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(TestController.class));
    }

    @Test
    @DisplayName("mysql 운영 프로파일에서는 명시 플래그가 있어도 /api/test/** 컨트롤러가 등록되지 않는다")
    void mysqlProfileStillBlocksTestEndpoints() {
        contextRunner
                .withPropertyValues("app.test-endpoints.enabled=true", "spring.profiles.active=mysql")
                .run(context -> assertThat(context).doesNotHaveBean(TestController.class));
    }
}
