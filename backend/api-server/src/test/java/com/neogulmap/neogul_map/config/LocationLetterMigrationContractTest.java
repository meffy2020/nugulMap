package com.neogulmap.neogul_map.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocationLetterMigrationContractTest {

    @Test
    void canonicalAndGuardedSchemasKeepCoordinatesCoarseAndReportsActionable() throws IOException {
        String schema = Files.readString(Path.of("src/main/resources/schema.sql"));
        String migration = Files.readString(
                Path.of("src/main/resources/db/manual/20260901_location_letters.sql")
        );

        assertThat(schema).contains("CREATE TABLE IF NOT EXISTS `location_letter`");
        assertThat(schema).contains("`latitude_bucket` DECIMAL(7,4) NOT NULL");
        assertThat(schema).contains("`longitude_bucket` DECIMAL(8,4) NOT NULL");
        assertThat(schema).contains("CREATE TABLE IF NOT EXISTS `location_letter_report`");
        assertThat(schema).contains("CREATE TABLE IF NOT EXISTS `location_letter_like`");
        assertThat(schema).contains("UNIQUE KEY `uk_location_letter_like_user` (`letter_id`, `user_id`)");
        assertThat(schema).contains("`resolved_at` DATETIME NULL");
        assertThat(schema).contains("idx_location_letter_report_status_resolved");
        assertThat(migration).contains("information_schema.columns");
        assertThat(migration).contains("fk_location_letter_report_letter");
        assertThat(migration).contains("fk_location_letter_like_letter");
        assertThat(migration).contains("fk_location_letter_like_user");
        assertThat(migration).contains("ON DELETE CASCADE ON UPDATE CASCADE");
    }
}
