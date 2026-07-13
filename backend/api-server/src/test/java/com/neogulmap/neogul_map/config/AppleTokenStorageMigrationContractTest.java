package com.neogulmap.neogul_map.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AppleTokenStorageMigrationContractTest {

    @Test
    void productionMigrationAddsTheEncryptedAppleRefreshTokenColumnIdempotently() throws IOException {
        String migration = readResource("/db/manual/20260711_apple_token_storage.sql");
        String schema = readResource("/schema.sql");

        assertThat(migration)
                .contains("information_schema.columns")
                .contains("apple_refresh_token_ciphertext")
                .contains("ALTER TABLE `users` ADD COLUMN")
                .contains("IF(");
        assertThat(schema).contains("`apple_refresh_token_ciphertext` TEXT NULL");
    }

    private String readResource(String path) throws IOException {
        try (var stream = AppleTokenStorageMigrationContractTest.class.getResourceAsStream(path)) {
            assertThat(stream).as("classpath resource %s", path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
