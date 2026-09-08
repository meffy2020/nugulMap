package com.neogulmap.neogul_map.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DemoZoneQuarantineMigrationTest {
    @Test
    void onlyExactPublishedDemoSignaturesAreQuarantinedWithoutDeletingAnything() throws Exception {
        var resource = new ClassPathResource("db/manual/known-demo-zones.json");
        List<Map<String, Object>> signatures;
        try (var input = resource.getInputStream()) {
            signatures = new ObjectMapper().readValue(input, new TypeReference<>() {});
        }
        assertThat(signatures).hasSize(10);
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL")) {
            connection.createStatement().execute("""
                    CREATE TABLE zone (id INT PRIMARY KEY, address VARCHAR(100), latitude DECIMAL(10,7),
                    longitude DECIMAL(10,7), description TEXT, publication_status VARCHAR(20))
                    """);
            for (int index = 0; index < signatures.size(); index++) {
                insert(connection, 100 + index, signatures.get(index), "PUBLISHED");
            }
            // Numeric fixture IDs are not a criterion: real or corrected places keep publishing.
            var first = signatures.getFirst();
            for (int index = 0; index < 4; index++) {
                var modified = new java.util.HashMap<>(first);
                switch (index) {
                    case 0 -> modified.put("address", "현장 확인 주소");
                    case 1 -> modified.put("latitude", 37.55);
                    case 2 -> modified.put("longitude", 127.1);
                    case 3 -> modified.put("description", "현장에서 확인한 안내");
                }
                insert(connection, index + 1, modified, "PUBLISHED");
            }
            var trailingSpace = new java.util.HashMap<>(first);
            trailingSpace.put("description", first.get("description") + " ");
            insert(connection, 5, trailingSpace, "PUBLISHED");
            insert(connection, 200, first, "PENDING");
            insert(connection, 201, first, "REJECTED");

            var migration = new ClassPathResource("db/manual/20260907_quarantine_demo_zones.sql");
            ScriptUtils.executeSqlScript(connection, migration);
            assertThat(count(connection, "publication_status = 'PUBLISHED'")).isEqualTo(5);
            assertThat(count(connection, "id BETWEEN 100 AND 109 AND publication_status = 'PENDING'")).isEqualTo(10);
            assertThat(count(connection, "id = 201 AND publication_status = 'REJECTED'")).isEqualTo(1);
            assertThat(count(connection, "1=1")).isEqualTo(17);
            ScriptUtils.executeSqlScript(connection, migration);
            assertThat(count(connection, "publication_status = 'PUBLISHED'")).isEqualTo(5);
            assertThat(count(connection, "1=1")).isEqualTo(17);
        }
    }

    private void insert(Connection connection, int id, Map<String, Object> row, String status) throws Exception {
        try (var statement = connection.prepareStatement("INSERT INTO zone VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setInt(1, id);
            statement.setString(2, (String) row.get("address"));
            statement.setDouble(3, ((Number) row.get("latitude")).doubleValue());
            statement.setDouble(4, ((Number) row.get("longitude")).doubleValue());
            statement.setString(5, (String) row.get("description"));
            statement.setString(6, status);
            statement.executeUpdate();
        }
    }

    private int count(Connection connection, String predicate) throws Exception {
        try (var result = connection.createStatement().executeQuery("SELECT COUNT(*) FROM zone WHERE " + predicate)) {
            result.next();
            return result.getInt(1);
        }
    }
}
