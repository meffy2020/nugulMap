package com.neogulmap.neogul_map.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SupportRequestRetentionMigrationContractTest {

    @Test
    void migrationAddsGuardedCompletionTimeBackfillAndPurgeIndex() throws IOException {
        String migration = readResource("/db/manual/20260712_support_request_retention.sql");
        String schema = readResource("/schema.sql");

        assertThat(migration)
                .contains("information_schema.columns")
                .contains("column_name = 'resolved_at'")
                .contains("SET `resolved_at` = `created_at`")
                .contains("WHERE `status` IN ('RESOLVED', 'REJECTED')")
                .contains("information_schema.statistics")
                .contains("idx_support_request_status_resolved");
        assertThat(schema)
                .contains("`resolved_at` DATETIME NULL")
                .contains("INDEX `idx_support_request_status_resolved` (`status`, `resolved_at`)");
    }

    private String readResource(String path) throws IOException {
        try (var stream = getClass().getResourceAsStream(path)) {
            assertThat(stream).as("classpath resource %s", path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
