package com.neogulmap.neogul_map.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseProfileSafetyTest {
    private ApplicationContextRunner profiles(String profiles) {
        return new ApplicationContextRunner()
                .withPropertyValues("spring.config.location=classpath:/application.yml",
                        "spring.profiles.active=" + profiles)
                .withInitializer(new ConfigDataApplicationContextInitializer());
    }

    @ParameterizedTest
    @CsvSource(value = {"mysql,prod;none", "mysql,prod;update", "prod,mysql;none", "prod,mysql;update"}, delimiter = ';')
    void productionUsesHibernateValidationRegardlessOfMysqlDdlPreference(String activeProfiles, String mysqlDdlAuto) {
        profiles(activeProfiles).withPropertyValues("MYSQL_DDL_AUTO=" + mysqlDdlAuto)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    Binder binder = Binder.get(context.getEnvironment());
                    JpaProperties jpa = binder.bind("spring.jpa", JpaProperties.class).get();
                    HibernateProperties hibernate = binder.bind("spring.jpa.hibernate", HibernateProperties.class).get();
                    // Check the effective map passed to Hibernate, including vendor-property precedence.
                    assertThat(hibernate.determineHibernateProperties(jpa.getProperties(), new HibernateSettings()))
                            .containsEntry("hibernate.hbm2ddl.auto", "validate");
                });
    }

    @Test
    void mysqlProfileDoesNotAutomaticallySeedSchemaOrDemoData() {
        profiles("mysql").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(Binder.get(context.getEnvironment())
                    .bind("spring.sql.init", SqlInitializationProperties.class).get().getMode().name())
                    .isEqualTo("NEVER");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "mysql,prod", "prod,mysql"})
    void initializationCannotBeEnabledThroughLegacyMysqlOptIn(String activeProfiles) {
        profiles(activeProfiles).withPropertyValues("MYSQL_SQL_INIT_MODE=always").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(Binder.get(context.getEnvironment())
                    .bind("spring.sql.init", SqlInitializationProperties.class).get().getMode().name())
                    .isEqualTo("NEVER");
        });
    }
}
