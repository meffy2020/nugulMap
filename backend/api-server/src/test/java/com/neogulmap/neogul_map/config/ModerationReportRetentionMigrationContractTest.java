package com.neogulmap.neogul_map.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ModerationReportRetentionMigrationContractTest {

    @Test
    void migrationAddsGuardedResolutionTimeBackfillAndPurgeIndexes() throws IOException {
        String migration = readResource("/db/manual/20260712_moderation_report_retention.sql");
        String schema = readResource("/schema.sql");
        String migrationReadme = readResource("/db/manual/README.md");

        assertThat(migration)
                .contains("information_schema.columns")
                .contains("table_name = 'zone_review_report'")
                .contains("table_name = 'zone_report'")
                .contains("column_name = 'resolved_at'")
                .contains("SET `resolved_at` = `created_at`")
                .contains("WHERE `status` IN ('RESOLVED', 'DISMISSED')")
                .contains("information_schema.statistics")
                .contains("idx_zone_review_report_status_resolved")
                .contains("idx_zone_report_status_resolved");
        assertThat(schema)
                .contains("INDEX `idx_zone_review_report_status_resolved` (`status`, `resolved_at`)")
                .contains("INDEX `idx_zone_report_status_resolved` (`status`, `resolved_at`)");
        assertThat(migrationReadme)
                .contains("20260712_moderation_report_retention.sql")
                .contains("처리일로부터 30일");
        assertThat(migrationReadme.indexOf("20260710_launch_safety.sql"))
                .isLessThan(migrationReadme.indexOf("20260712_moderation_report_retention.sql"));
    }

    private String readResource(String path) throws IOException {
        try (var stream = getClass().getResourceAsStream(path)) {
            assertThat(stream).as("classpath resource %s", path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
