package com.neogulmap.neogul_map.config;

import jakarta.persistence.Entity;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlTextMappingTest {
    @Test
    void canonicalTextColumnsUseTextRatherThanClobWithMysqlDialect() throws Exception {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .build();
        try {
            var sources = new MetadataSources(registry);
            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
            for (var entity : scanner.findCandidateComponents("com.neogulmap.neogul_map.domain")) {
                sources.addAnnotatedClass(Class.forName(entity.getBeanClassName()));
            }
            var metadata = sources.buildMetadata();
            Map<String, String[]> columns = Map.of(
                    "LocationLetter", new String[]{"content"},
                    "LocationLetterReport", new String[]{"details"},
                    "Zone", new String[]{"description"},
                    "ZoneReview", new String[]{"content"},
                    "ZoneReport", new String[]{"details"},
                    "ZoneReviewReport", new String[]{"details"},
                    "SupportRequest", new String[]{"message"},
                    "User", new String[]{"apple_refresh_token_ciphertext"},
                    "recommendation.UserPreference", new String[]{"preferred_tags", "disliked_tags"}
            );
            columns.forEach((entity, names) -> {
                var table = metadata.getEntityBinding("com.neogulmap.neogul_map.domain." + entity).getTable();
                for (String name : names) {
                    Column column = table.getColumn(new Column(name));
                    assertThat(column).as(entity + "." + name).isNotNull();
                    assertThat(column.getSqlTypeCode(metadata)).as(entity + "." + name + " JDBC type")
                            .isEqualTo(SqlTypes.LONGVARCHAR);
                    assertThat(column.getSqlType(metadata)).as(entity + "." + name + " SQL type")
                            .isEqualToIgnoringCase("TEXT");
                }
            });
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
