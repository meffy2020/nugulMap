package com.neogulmap.neogul_map.config;

import com.neogulmap.neogul_map.domain.recommendation.RecommendationEventType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationDataMigrationContractTest {

    @Test
    void repeatableMigrationAndBaselineSchemaContainRecommendationDataContracts() throws IOException {
        String migration = readResource("/db/manual/20260901_recommendation_data_foundation.sql");
        String limitsMigration = readResource("/db/manual/20260901_recommendation_data_limits.sql");
        String schema = readResource("/schema.sql");

        assertRecommendationTables(migration);
        assertRecommendationTables(schema);
        assertThat(migration)
                .contains("CREATE TABLE IF NOT EXISTS `user_preference`")
                .contains("CREATE TABLE IF NOT EXISTS `user_saved_item`")
                .contains("CREATE TABLE IF NOT EXISTS `recommendation_interaction`")
                .doesNotContain("DROP TABLE");
        assertThat(limitsMigration)
                .contains("information_schema.statistics")
                .contains("DELETE duplicate_row")
                .contains("duplicate_row.`id` > canonical_row.`id`")
                .contains("ALTER TABLE `recommendation_interaction` ADD UNIQUE KEY `uk_rec_interaction_event`")
                .contains("ALTER TABLE `recommendation_interaction` ADD INDEX `idx_rec_interaction_occurred`")
                .doesNotContain("DROP TABLE");
        assertThat(RecommendationEventType.values()).containsExactly(
                RecommendationEventType.IMPRESSION,
                RecommendationEventType.DETAIL_OPEN,
                RecommendationEventType.SAVE,
                RecommendationEventType.UNSAVE,
                RecommendationEventType.ROUTE,
                RecommendationEventType.NOT_INTERESTED
        );
    }

    private void assertRecommendationTables(String sql) {
        assertThat(sql)
                .contains("`personalization_enabled` BOOLEAN NOT NULL DEFAULT FALSE")
                .contains("`preferred_tags` TEXT NOT NULL")
                .contains("`disliked_tags` TEXT NOT NULL")
                .contains("`budget_level` VARCHAR(20) NULL")
                .contains("`max_distance_meters` INT NULL")
                .contains("UNIQUE KEY `uk_user_saved_item_key` (`user_id`, `item_type`, `provider`, `item_id`)")
                .contains("`lat` DECIMAL(10,7) NOT NULL")
                .contains("`lng` DECIMAL(10,7) NOT NULL")
                .contains("`request_id` VARCHAR(64) NOT NULL")
                .contains("`event_type` VARCHAR(40) NOT NULL")
                .contains("`intent` VARCHAR(40) NOT NULL")
                .contains("`context_region` VARCHAR(100) NULL")
                .contains("`model_version` VARCHAR(64) NOT NULL")
                .contains("`occurred_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP")
                .contains("UNIQUE KEY `uk_rec_interaction_event` (`user_id`, `request_id`, `item_type`, `provider`, `item_id`, `event_type`)")
                .contains("INDEX `idx_rec_interaction_occurred` (`occurred_at`)")
                .contains("FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE");
    }

    private String readResource(String path) throws IOException {
        try (var stream = getClass().getResourceAsStream(path)) {
            assertThat(stream).as("classpath resource %s", path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
