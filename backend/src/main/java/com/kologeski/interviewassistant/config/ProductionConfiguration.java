package com.kologeski.interviewassistant.config;

import java.net.URI;
import java.util.Base64;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ProductionConfiguration {
    @Bean
    static BeanFactoryPostProcessor validateProductionEnvironment(Environment environment) {
        // Run before creating the datasource or JWT service; never echo secret values.
        return factory -> validate(environment);
    }

    static void validate(Environment environment) {
        String database = required(environment, "DATABASE_URL");
        required(environment, "DATABASE_USER");
        required(environment, "DATABASE_PASSWORD");
        if (!database.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("DATABASE_URL must use JDBC PostgreSQL syntax.");
        }
        String secret = required(environment, "JWT_SECRET");
        try {
            if (Base64.getDecoder().decode(secret).length < 32) throw new IllegalArgumentException();
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("JWT_SECRET must be Base64 encoding at least 32 random bytes.");
        }
        for (String origin : required(environment, "CORS_ALLOWED_ORIGINS").split(",", -1)) {
            try {
                URI uri = URI.create(origin.trim());
                if (!"https".equals(uri.getScheme()) || uri.getHost() == null || origin.contains("*")
                        || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                        || uri.getRawFragment() != null || !uri.getRawPath().isEmpty()) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException error) {
                throw new IllegalStateException("CORS_ALLOWED_ORIGINS must contain explicit HTTPS origins without paths or wildcards.");
            }
        }
    }

    private static String required(Environment environment, String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required in prod.");
        return value;
    }
}
