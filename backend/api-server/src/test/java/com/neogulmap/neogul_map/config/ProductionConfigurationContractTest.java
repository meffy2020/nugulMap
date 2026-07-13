package com.neogulmap.neogul_map.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionConfigurationContractTest {

    @Test
    void productionSecurityContractsDisableSwaggerAndKeepNativeOAuthForTenMinutes() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
                "application-prod",
                new ClassPathResource("application-prod.yml")
        );

        assertThat(property(sources, "app.swagger.enabled")).isEqualTo(false);
        assertThat(property(sources, "springdoc.swagger-ui.enabled")).isEqualTo(false);
        assertThat(property(sources, "springdoc.api-docs.enabled")).isEqualTo(false);
        assertThat(property(sources, "app.security.csrf.enabled")).isEqualTo(true);
        assertThat(property(sources, "app.security.sensitive-rate-limit.enabled")).isEqualTo(true);
        assertThat(property(sources, "app.security.sensitive-rate-limit.requests-per-window")).isEqualTo(30);
        assertThat(property(sources, "app.security.public-zone-rate-limit.enabled")).isEqualTo(true);
        assertThat(property(sources, "app.security.public-zone-rate-limit.requests-per-window")).isEqualTo(240);
        assertThat(property(sources, "app.security.public-zone-rate-limit.window-seconds")).isEqualTo(60);
        assertThat(property(sources, "app.moderation.closed-report-retention-days")).isEqualTo(30);
        assertThat(property(sources, "app.moderation.closed-report-purge-cron")).isEqualTo("0 45 3 * * *");
        assertThat(property(sources, "app.support.closed-request-retention-days")).isEqualTo(30);
        assertThat(property(sources, "app.support.closed-request-purge-cron")).isEqualTo("0 30 3 * * *");
        assertThat(property(sources, "server.servlet.session.timeout")).isEqualTo("10m");
    }

    private Object property(List<PropertySource<?>> sources, String name) {
        return sources.stream()
                .map(source -> source.getProperty(name))
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }
}
