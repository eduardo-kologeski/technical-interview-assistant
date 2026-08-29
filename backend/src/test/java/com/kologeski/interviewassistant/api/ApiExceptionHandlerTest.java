package com.kologeski.interviewassistant.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void mapsDomainValidationToBadRequestInsteadOfNotFound() {
        var problem = handler.invalidRequest(new IllegalArgumentException("Campo inválido."));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo("Campo inválido.");
    }

    @Test
    void mapsExplicitMissingResourceToNotFound() {
        var problem = handler.notFound(new ResourceNotFoundException("Vaga não encontrada."));

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getDetail()).isEqualTo("Vaga não encontrada.");
    }

    @Test
    void hidesUnexpectedExceptionAndReturnsCorrelationId() {
        MDC.put("correlationId", "request-123");

        var problem = handler.unexpected(new RuntimeException("password=secret"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).doesNotContain("secret");
        assertThat(problem.getProperties()).containsEntry("correlationId", "request-123");
    }
}
