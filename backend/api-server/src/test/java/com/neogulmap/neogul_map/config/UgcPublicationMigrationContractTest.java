package com.neogulmap.neogul_map.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class UgcPublicationMigrationContractTest {

    @Test
    void productionMigrationAddsPublicationStateAndPreservesAnonymousReviewsIdempotently() throws IOException {
        String migration = readResource("/db/manual/20260711_ugc_publication_safety.sql");
        String schema = readResource("/schema.sql");

        assertThat(migration)
                .contains("information_schema.columns")
                .contains("`publication_status` VARCHAR(20) NOT NULL DEFAULT ''PUBLISHED''")
                .contains("information_schema.referential_constraints")
                .contains("is_nullable = 'NO'")
                .contains("@review_author_nullable_ddl")
                .contains("MODIFY COLUMN `author_id` BIGINT NULL")
                .contains("ON DELETE SET NULL")
                .contains("IF(");
        assertThat(schema)
                .contains("`publication_status` VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED'")
                .contains("`author_id` BIGINT NULL")
                .contains("CONSTRAINT `fk_zone_review_author`")
                .contains("ON DELETE SET NULL");
    }

    private String readResource(String path) throws IOException {
        try (var stream = UgcPublicationMigrationContractTest.class.getResourceAsStream(path)) {
            assertThat(stream).as("classpath resource %s", path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
