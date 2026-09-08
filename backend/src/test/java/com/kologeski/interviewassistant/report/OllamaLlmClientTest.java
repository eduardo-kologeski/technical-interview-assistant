package com.kologeski.interviewassistant.report;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaLlmClientTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ReportPrompt prompt = new ReportPrompt("report-draft-v1", "System MINUTA", "User context");

    @Test void sendsANonStreamingStructuredRequestAndParsesTheDraft() throws Exception {
        AtomicReference<byte[]> captured = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            captured.set(exchange.getRequestBody().readAllBytes());
            respond(exchange, 200, envelope(validDraft()));
        });
        try {
            OllamaLlmClient client = new OllamaLlmClient(properties(server, Duration.ofSeconds(2)), json);

            LlmClient.LlmDraftResponse response = client.generateDraft(prompt);

            assertThat(response.model()).isEqualTo("local-test-model");
            assertThat(response.draft().observedKnowledge().evidenceReferences()).containsExactly("evidence-1");
            JsonNode request = json.readTree(captured.get());
            assertThat(request.get("model").asText()).isEqualTo("local-test-model");
            assertThat(request.get("stream").asBoolean()).isFalse();
            assertThat(request.get("format").get("additionalProperties").asBoolean()).isFalse();
            assertThat(request.get("system").asText()).contains("MINUTA");
        } finally {
            server.stop(0);
        }
    }

    @Test void mapsMissingModelInvalidResponseAndTimeoutWithoutReturningProviderContent() throws Exception {
        HttpServer missing = server(exchange -> respond(exchange, 404, "{\"error\":\"secret provider detail\"}"));
        try {
            assertFailure(new OllamaLlmClient(properties(missing, Duration.ofSeconds(2)), json),
                    LlmGenerationException.Kind.MODEL_NOT_FOUND, "modelo configurado");
        } finally {
            missing.stop(0);
        }

        HttpServer invalid = server(exchange -> respond(exchange, 200,
                "{\"done\":true,\"response\":\"not-json\"}"));
        try {
            assertFailure(new OllamaLlmClient(properties(invalid, Duration.ofSeconds(2)), json),
                    LlmGenerationException.Kind.INVALID_RESPONSE, "formato inválido");
        } finally {
            invalid.stop(0);
        }

        HttpServer wrongShape = server(exchange -> respond(exchange, 200,
                "{\"done\":true,\"response\":\"{\\\"unexpected\\\":{}}\"}"));
        try {
            assertFailure(new OllamaLlmClient(properties(wrongShape, Duration.ofSeconds(2)), json),
                    LlmGenerationException.Kind.INVALID_RESPONSE, "seções diferentes");
        } finally {
            wrongShape.stop(0);
        }

        HttpServer slow = server(exchange -> {
            try { Thread.sleep(250); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            respond(exchange, 200, envelope(validDraft()));
        });
        try {
            assertFailure(new OllamaLlmClient(properties(slow, Duration.ofMillis(50)), json),
                    LlmGenerationException.Kind.TIMEOUT, "tempo limite");
        } finally {
            slow.stop(0);
        }
    }

    @Test void refusesMissingCloudOrExternalConfigurationBeforeAnyRequest() {
        OllamaProperties missing = new OllamaProperties();
        assertFailure(new OllamaLlmClient(missing, json),
                LlmGenerationException.Kind.CONFIGURATION, "OLLAMA_BASE_URL");

        OllamaProperties external = new OllamaProperties();
        external.setBaseUrl("https://example.test:11434");
        external.setModel("local-model");
        assertFailure(new OllamaLlmClient(external, json),
                LlmGenerationException.Kind.CONFIGURATION, "host local");

        OllamaProperties cloud = new OllamaProperties();
        cloud.setBaseUrl("http://localhost:11434");
        cloud.setModel("example:cloud");
        assertFailure(new OllamaLlmClient(cloud, json),
                LlmGenerationException.Kind.CONFIGURATION, "localmente");

        OllamaProperties limited = new OllamaProperties();
        limited.setBaseUrl("http://localhost:11434");
        limited.setModel("local-model");
        limited.setMaxInputChars(5);
        assertFailure(new OllamaLlmClient(limited, json),
                LlmGenerationException.Kind.INPUT_TOO_LARGE, "excede o limite");
    }

    private OllamaProperties properties(HttpServer server, Duration responseTimeout) {
        OllamaProperties properties = new OllamaProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setModel("local-test-model");
        properties.setResponseTimeout(responseTimeout);
        return properties;
    }

    private byte[] envelope(ReportDraft draft) throws IOException {
        return json.writeValueAsBytes(Map.of(
                "done", true,
                "response", json.writeValueAsString(draft)));
    }

    private ReportDraft validDraft() {
        var context = new ReportDraft.Section("Contexto técnico.", List.of(), false);
        var evidenced = new ReportDraft.Section("Registro sustentado.", List.of("evidence-1"), false);
        return new ReportDraft(context, evidenced, evidenced,
                new ReportDraft.Section("Informações insuficientes.", List.of(), true), evidenced);
    }

    private HttpServer server(ThrowingHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", exchange -> {
            try { handler.handle(exchange); }
            catch (Exception error) { exchange.close(); }
        });
        server.start();
        return server;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        respond(exchange, status, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void assertFailure(OllamaLlmClient client, LlmGenerationException.Kind kind, String message) {
        assertThatThrownBy(() -> client.generateDraft(prompt))
                .isInstanceOfSatisfying(LlmGenerationException.class, error -> {
                    assertThat(error.kind()).isEqualTo(kind);
                    assertThat(error.getMessage()).contains(message).doesNotContain("secret provider detail");
                });
    }

    @FunctionalInterface
    private interface ThrowingHandler { void handle(HttpExchange exchange) throws Exception; }
}
