package com.kologeski.interviewassistant.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PostgresqlFlywayMigrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6-alpine")
            .withDatabaseName("interview_assistant")
            .withUsername("interview_app")
            .withPassword("test-only-password");

    @Test
    void migratesAnEmptyPostgresqlDatabaseAndValidatesTheCriticalSchema() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();

        var migration = flyway.migrate();

        assertThat(migration.success).isTrue();
        assertThat(migration.migrationsExecuted).isEqualTo(13);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            DatabaseMetaData metadata = connection.getMetaData();
            assertThat(tableExists(metadata, "job_vacancies")).isTrue();
            assertThat(columnExists(metadata, "job_vacancies", "version")).isTrue();
            assertThat(tableExists(metadata, "interviews")).isTrue();
            assertThat(columnExists(metadata, "interviews", "assessment_data")).isTrue();
            assertThat(columnExists(metadata, "interviews", "version")).isTrue();
            assertThat(tableExists(metadata, "users")).isTrue();
            assertThat(tableExists(metadata, "refresh_tokens")).isTrue();
            assertThat(tableExists(metadata, "interview_availability")).isTrue();
            assertThat(tableExists(metadata, "companies")).isTrue();
            assertThat(tableExists(metadata, "company_memberships")).isTrue();
            assertThat(tableExists(metadata, "company_invitations")).isTrue();
            assertThat(tableExists(metadata, "interview_participants")).isTrue();
            assertThat(columnExists(metadata, "job_vacancies", "company_id")).isTrue();
            assertThat(columnExists(metadata, "interviews", "company_id")).isTrue();
        }
    }

    private boolean tableExists(DatabaseMetaData metadata, String table) throws Exception {
        try (ResultSet result = metadata.getTables(null, "public", table, new String[]{"TABLE"})) {
            return result.next();
        }
    }

    private boolean columnExists(DatabaseMetaData metadata, String table, String column) throws Exception {
        try (ResultSet result = metadata.getColumns(null, "public", table, column)) {
            return result.next();
        }
    }
}
