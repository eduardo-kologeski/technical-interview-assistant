package com.kologeski.interviewassistant.config;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class ProductionConfigurationTest {
    private MockEnvironment valid() {
        return new MockEnvironment()
                .withProperty("DATABASE_URL", "jdbc:postgresql://db.example.test/demo?sslmode=require")
                .withProperty("DATABASE_USER", "test-user")
                .withProperty("DATABASE_PASSWORD", "test-only")
                .withProperty("JWT_SECRET", Base64.getEncoder().encodeToString(new byte[64]))
                .withProperty("CORS_ALLOWED_ORIGINS", "https://demo.example.test,https://other.example.test");
    }

    @Test void acceptsExternalConfiguration() {
        assertThatCode(() -> ProductionConfiguration.validate(valid())).doesNotThrowAnyException();
    }

    @Test void rejectsMissingAndBlankRequiredValuesWithoutLeakingThem() {
        for (String name : new String[]{"DATABASE_URL", "DATABASE_USER", "DATABASE_PASSWORD", "JWT_SECRET", "CORS_ALLOWED_ORIGINS"}) {
            var missing = new MockEnvironment();
            for (String key : new String[]{"DATABASE_URL", "DATABASE_USER", "DATABASE_PASSWORD", "JWT_SECRET", "CORS_ALLOWED_ORIGINS"}) {
                if (!key.equals(name)) missing.withProperty(key, valid().getProperty(key));
            }
            assertThatThrownBy(() -> ProductionConfiguration.validate(missing)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> ProductionConfiguration.validate(valid().withProperty(name, " ")))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining(name);
        }
    }

    @Test void rejectsWeakOrMalformedSecrets() {
        for (String secret : new String[]{"invalid!", Base64.getEncoder().encodeToString(new byte[16])}) {
            assertThatThrownBy(() -> ProductionConfiguration.validate(valid().withProperty("JWT_SECRET", secret)))
                    .hasMessageContaining("JWT_SECRET").hasMessageNotContaining(secret);
        }
    }

    @Test void rejectsNonJdbcDatabaseUrl() {
        assertThatThrownBy(() -> ProductionConfiguration.validate(valid().withProperty("DATABASE_URL", "postgresql://db/demo")))
                .hasMessageContaining("JDBC");
    }

    @Test void rejectsUnsafeCorsOrigins() {
        for (String origin : new String[]{"*", "https://*.example.test", "http://localhost:4200", "https://example.test/path",
                "https://user:pass@example.test", "https://example.test?x=1", "https://example.test,"}) {
            assertThatThrownBy(() -> ProductionConfiguration.validate(valid().withProperty("CORS_ALLOWED_ORIGINS", origin)))
                    .hasMessageContaining("CORS_ALLOWED_ORIGINS");
        }
    }
}
