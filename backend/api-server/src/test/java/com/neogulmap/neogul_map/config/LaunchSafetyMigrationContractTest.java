package com.neogulmap.neogul_map.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class LaunchSafetyMigrationContractTest {

    private static final String MIGRATION = "/db/manual/20260710_launch_safety.sql";
    private static final List<String> TABLES = List.of(
            "user_block",
            "zone_review_report",
            "zone_report",
            "support_request"
    );

    @Test
    void manualProductionMigrationIsIdempotentAndMatchesCanonicalSchema() throws IOException {
        String migrationSql = readResource(MIGRATION);
        String canonicalSchema = readResource("/schema.sql");

        for (String table : TABLES) {
            String migrationDefinition = tableDefinition(migrationSql, table);
            String canonicalDefinition = tableDefinition(canonicalSchema, table);

            assertThat(migrationDefinition)
                    .as("manual migration for %s", table)
                    .startsWith("CREATE TABLE IF NOT EXISTS")
                    .isEqualTo(canonicalDefinition);
        }
    }

    @Test
    void userBlockForeignKeysDoNotUseAnIncompatibleMySqlCheckConstraint() throws IOException {
        String migrationDefinition = tableDefinition(readResource(MIGRATION), "user_block");

        assertThat(migrationDefinition)
                .doesNotContainIgnoringCase("CHECK (`blocker_id` <> `blocked_id`)");
    }

    private String tableDefinition(String sql, String table) {
        Pattern pattern = Pattern.compile(
                "CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS\\s+`"
                        + Pattern.quote(table)
                        + "`\\s*\\(.*?\\)\\s*ENGINE=InnoDB\\s+DEFAULT\\s+CHARSET=utf8mb4\\s+"
                        + "COLLATE=utf8mb4_unicode_ci;",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(sql);
        assertThat(matcher.find()).as("table definition exists: %s", table).isTrue();
        return matcher.group().replaceAll("\\s+", " ").trim();
    }

    private String readResource(String path) throws IOException {
        try (var stream = LaunchSafetyMigrationContractTest.class.getResourceAsStream(path)) {
            assertThat(stream).as("classpath resource %s", path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
