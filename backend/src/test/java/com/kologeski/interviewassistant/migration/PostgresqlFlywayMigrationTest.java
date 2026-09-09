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
import java.sql.Statement;
import java.util.UUID;

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
                .cleanDisabled(false)
                .load();

        var migration = flyway.migrate();

        assertThat(migration.success).isTrue();
        assertThat(migration.migrationsExecuted).isEqualTo(28);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            DatabaseMetaData metadata = connection.getMetaData();
            assertThat(tableExists(metadata, "job_vacancies")).isTrue();
            assertThat(columnExists(metadata, "job_vacancies", "version")).isTrue();
            assertThat(tableExists(metadata, "interviews")).isTrue();
            assertThat(columnExists(metadata, "interviews", "assessment_data")).isTrue();
            assertThat(columnExists(metadata, "interviews", "version")).isTrue();
            assertThat(columnExists(metadata, "interviews", "kit_snapshot")).isTrue();
            assertThat(tableExists(metadata, "users")).isTrue();
            assertThat(tableExists(metadata, "refresh_tokens")).isTrue();
            assertThat(tableExists(metadata, "interview_availability")).isTrue();
            assertThat(tableExists(metadata, "interview_kits")).isTrue();
            assertThat(tableExists(metadata, "interview_kit_versions")).isTrue();
            assertThat(tableExists(metadata, "interview_kit_items")).isTrue();
            assertThat(tableExists(metadata, "candidates")).isTrue();
            assertThat(tableExists(metadata, "candidate_vacancies")).isTrue();
            assertThat(tableExists(metadata, "candidate_timeline_events")).isTrue();
            assertThat(columnExists(metadata, "interviews", "candidate_id")).isTrue();
            assertThat(tableExists(metadata, "companies")).isTrue();
            assertThat(tableExists(metadata, "company_memberships")).isTrue();
            assertThat(tableExists(metadata, "company_invitations")).isTrue();
            assertThat(tableExists(metadata, "interview_participants")).isTrue();
            assertThat(tableExists(metadata, "identity_tokens")).isTrue();
            assertThat(tableExists(metadata, "audit_events")).isTrue();
            assertThat(tableExists(metadata, "installation_state")).isTrue();
            assertThat(tableExists(metadata, "saas_plans")).isTrue();
            assertThat(tableExists(metadata, "saas_entitlements")).isTrue();
            assertThat(tableExists(metadata, "saas_subscriptions")).isTrue();
            assertThat(tableExists(metadata, "saas_usage")).isTrue();
            assertThat(columnExists(metadata, "users", "email_verified_at")).isTrue();
            assertThat(columnExists(metadata, "users", "session_version")).isTrue();
            assertThat(columnExists(metadata, "job_vacancies", "company_id")).isTrue();
            assertThat(columnExists(metadata, "interviews", "company_id")).isTrue();
            assertThat(tableExists(metadata, "assessment_reports")).isTrue();
            assertThat(columnExists(metadata, "assessment_reports", "context_snapshot")).isTrue();
            assertThat(columnExists(metadata, "assessment_reports", "original_draft")).isTrue();
            assertThat(columnExists(metadata, "assessment_reports", "reviewed_draft")).isTrue();
            assertThat(columnExists(metadata, "assessment_reports", "human_conclusion")).isTrue();
            assertThat(columnExists(metadata, "assessment_reports", "approved_at")).isTrue();
            assertThat(columnExists(metadata, "users", "deleted_at")).isTrue();
            assertThat(tableExists(metadata, "assessment_studies")).isTrue();
            assertThat(columnExists(metadata, "assessment_studies", "report_evaluation")).isTrue();
            assertThat(tableExists(metadata, "report_generation_claims")).isTrue();
            assertThat(columnExists(metadata, "assessment_studies", "manual_active_millis")).isTrue();
            assertThat(columnExists(metadata, "assessment_reports", "generation_latency_millis")).isTrue();
        }

        flyway.clean();
        Flyway legacy = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target("11")
                .load();
        assertThat(legacy.migrate().migrationsExecuted).isEqualTo(11);

        UUID vacancyId = UUID.randomUUID();
        UUID interviewId = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users(name, email, password_hash, created_at, updated_at)
                    VALUES ('Legacy User', 'legacy@example.test', 'test-only-hash', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """);
            statement.executeUpdate("""
                    INSERT INTO job_vacancies(id, title, active, created_at, created_by, updated_at, updated_by)
                    VALUES ('%s', 'Legacy Vacancy', TRUE, CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, '1')
                    """.formatted(vacancyId));
            statement.executeUpdate("""
                    INSERT INTO interviews(id, vacancy_id, candidate_name, vacancy_title_snapshot, interviewer_id,
                                           status, created_at, created_by, updated_at, updated_by)
                    VALUES ('%s', '%s', 'Legacy Candidate', 'Legacy Vacancy', '1', 'IN_PROGRESS',
                            CURRENT_TIMESTAMP, '1', CURRENT_TIMESTAMP, '1')
                    """.formatted(interviewId, vacancyId));
        }

        Flyway baseline20 = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").target("20").load();
        assertThat(baseline20.migrate().migrationsExecuted).isEqualTo(9);

        Flyway upgrade = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(8);
        assertThat(upgrade.validateWithResult().validationSuccessful).isTrue();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            assertThat(singleValue(statement,
                    "SELECT company_id::text FROM job_vacancies WHERE id = '" + vacancyId + "'"))
                    .isEqualTo("00000000-0000-0000-0000-000000000001");
            assertThat(singleValue(statement,
                    "SELECT company_id::text FROM interviews WHERE id = '" + interviewId + "'"))
                    .isEqualTo("00000000-0000-0000-0000-000000000001");
            assertThat(singleValue(statement,
                    "SELECT (owner_user_id IS NULL)::text FROM interviews WHERE id = '" + interviewId + "'"))
                    .isEqualTo("true");
            assertThat(singleValue(statement,
                    "SELECT COUNT(*)::text FROM company_memberships WHERE user_id = 1 AND active = TRUE"))
                    .isEqualTo("1");
            assertThat(singleValue(statement,
                    "SELECT COUNT(*)::text FROM interview_participants WHERE interview_id = '" + interviewId + "'"))
                    .isEqualTo("1");
            assertThat(singleValue(statement,
                    "SELECT setup_completed::text FROM installation_state WHERE id = 1"))
                    .isEqualTo("true");
            assertThat(singleValue(statement,
                    "SELECT COUNT(*)::text FROM interview_kits WHERE vacancy_id = '" + vacancyId + "'"))
                    .isEqualTo("1");
            assertThat(singleValue(statement,
                    "SELECT COUNT(*)::text FROM interview_kit_versions WHERE kit_id = '" + vacancyId + "' AND status = 'PUBLISHED'"))
                    .isEqualTo("1");
            assertThat(singleValue(statement,
                    "SELECT COUNT(*)::text FROM candidates WHERE normalized_name = 'legacy candidate'"))
                    .isEqualTo("1");
            assertThat(singleValue(statement,
                    "SELECT COUNT(*)::text FROM candidate_timeline_events WHERE interview_id = '" + interviewId + "'"))
                    .isEqualTo("1");
            assertThat(singleValue(statement,
                    "SELECT plan_code FROM saas_subscriptions WHERE company_id = '00000000-0000-0000-0000-000000000001'"))
                    .isEqualTo("STARTER");
            assertThat(singleValue(statement,
                    "SELECT quantity::text FROM saas_usage WHERE company_id = '00000000-0000-0000-0000-000000000001' AND metric = 'MONTHLY_INTERVIEWS'"))
                    .isEqualTo("1");
        }
    }

    private String singleValue(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
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
