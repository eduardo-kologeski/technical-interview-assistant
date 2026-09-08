package com.kologeski.interviewassistant.report;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** HTTP and serialization adapter for a local Ollama server. */
@Component
public class OllamaLlmClient implements LlmClient {
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "host.docker.internal");
    private final OllamaProperties properties;
    private final ObjectMapper json;
    private final ObjectMapper strictJson;
    private final HttpClient http;

    public OllamaLlmClient(OllamaProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
        this.strictJson = json.rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        this.http = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
    }

    @Override
    public LlmDraftResponse generateDraft(ReportPrompt prompt) {
        URI endpoint = endpoint();
        requireInputLimit(prompt);
        byte[] requestBody = write(new GenerateRequest(properties.getModel(), prompt.userPrompt(),
                prompt.systemPrompt(), false, responseSchema()));
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(properties.getResponseTimeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                .build();
        try {
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            byte[] body;
            try (InputStream stream = response.body()) {
                body = stream.readNBytes(properties.getMaxResponseChars() + 1);
            }
            if (body.length > properties.getMaxResponseChars()) {
                throw failure(LlmGenerationException.Kind.INVALID_RESPONSE,
                        "A resposta do Ollama excedeu o limite permitido.");
            }
            if (response.statusCode() == 404) {
                throw failure(LlmGenerationException.Kind.MODEL_NOT_FOUND,
                        "O modelo configurado não está disponível no Ollama local.");
            }
            if (response.statusCode() == 503) {
                throw failure(LlmGenerationException.Kind.UNAVAILABLE,
                        "O Ollama local está indisponível ou sobrecarregado.");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw failure(LlmGenerationException.Kind.INVALID_RESPONSE,
                        "O Ollama local recusou a solicitação de geração.");
            }
            return new LlmDraftResponse(readDraft(body), properties.getModel());
        } catch (HttpTimeoutException error) {
            throw new LlmGenerationException(LlmGenerationException.Kind.TIMEOUT,
                    "O Ollama local excedeu o tempo limite de geração.", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new LlmGenerationException(LlmGenerationException.Kind.UNAVAILABLE,
                    "A geração da minuta foi interrompida.", error);
        } catch (IOException error) {
            throw new LlmGenerationException(LlmGenerationException.Kind.UNAVAILABLE,
                    "Não foi possível conectar ao Ollama local.", error);
        }
    }

    private ReportDraft readDraft(byte[] body) {
        try {
            JsonNode envelope = json.readTree(body);
            JsonNode done = envelope.get("done");
            JsonNode response = envelope.get("response");
            if (done == null || !done.asBoolean() || response == null || !response.isString()
                    || response.asText().isBlank()) {
                throw failure(LlmGenerationException.Kind.INVALID_RESPONSE,
                        "O Ollama retornou uma resposta incompleta.");
            }
            validateDraftShape(json.readTree(response.asText()));
            return strictJson.readValue(response.asText(), ReportDraft.class);
        } catch (JacksonException error) {
            throw new LlmGenerationException(LlmGenerationException.Kind.INVALID_RESPONSE,
                    "O Ollama retornou uma minuta em formato inválido.", error);
        }
    }

    private URI endpoint() {
        String rawUrl = properties.getBaseUrl();
        String model = properties.getModel();
        if (rawUrl.isBlank() || model.isBlank()) {
            throw failure(LlmGenerationException.Kind.CONFIGURATION,
                    "Configure OLLAMA_BASE_URL e OLLAMA_MODEL para gerar minutas.");
        }
        String normalizedModel = model.toLowerCase(Locale.ROOT);
        if (normalizedModel.endsWith(":cloud") || normalizedModel.endsWith("-cloud")) {
            throw failure(LlmGenerationException.Kind.CONFIGURATION,
                    "O modelo configurado deve ser executado localmente.");
        }
        try {
            URI base = URI.create(rawUrl);
            if (!"http".equalsIgnoreCase(base.getScheme()) || base.getHost() == null
                    || !LOCAL_HOSTS.contains(base.getHost().toLowerCase(Locale.ROOT))
                    || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
                throw new IllegalArgumentException("non-local Ollama URL");
            }
            URI normalized = rawUrl.endsWith("/") ? base : URI.create(rawUrl + "/");
            return normalized.resolve("api/generate");
        } catch (IllegalArgumentException error) {
            throw new LlmGenerationException(LlmGenerationException.Kind.CONFIGURATION,
                    "OLLAMA_BASE_URL deve apontar para o Ollama no host local.", error);
        }
    }

    private void requireInputLimit(ReportPrompt prompt) {
        int length = prompt.systemPrompt().length() + prompt.userPrompt().length();
        if (length > properties.getMaxInputChars()) {
            throw failure(LlmGenerationException.Kind.INPUT_TOO_LARGE,
                    "O contexto excede o limite configurado; nenhum conteúdo foi truncado.");
        }
    }

    private static void validateDraftShape(JsonNode draft) {
        List<String> names = List.of("context", "observedKnowledge", "strengths", "concerns", "synthesis");
        if (draft == null || !draft.isObject() || draft.size() != names.size()
                || names.stream().anyMatch(name -> draft.get(name) == null)) {
            throw failure(LlmGenerationException.Kind.INVALID_RESPONSE,
                    "O Ollama retornou seções diferentes do contrato.");
        }
        for (String name : names) {
            JsonNode section = draft.get(name);
            JsonNode text = section == null ? null : section.get("text");
            JsonNode references = section == null ? null : section.get("evidenceReferences");
            JsonNode insufficient = section == null ? null : section.get("insufficientData");
            if (section == null || !section.isObject() || section.size() != 3
                    || text == null || !text.isString()
                    || references == null || !references.isArray()
                    || insufficient == null || !insufficient.isBoolean()) {
                throw failure(LlmGenerationException.Kind.INVALID_RESPONSE,
                        "O Ollama retornou uma seção em formato inválido.");
            }
            for (JsonNode reference : references) {
                if (!reference.isString()) {
                    throw failure(LlmGenerationException.Kind.INVALID_RESPONSE,
                            "O Ollama retornou uma referência em formato inválido.");
                }
            }
        }
    }

    private byte[] write(Object value) {
        try {
            return json.writeValueAsBytes(value);
        } catch (JacksonException error) {
            throw new LlmGenerationException(LlmGenerationException.Kind.INVALID_RESPONSE,
                    "Não foi possível montar a solicitação para o Ollama.", error);
        }
    }

    private static Map<String, Object> responseSchema() {
        Map<String, Object> sectionProperties = new LinkedHashMap<>();
        sectionProperties.put("text", Map.of("type", "string"));
        sectionProperties.put("evidenceReferences", Map.of("type", "array", "items", Map.of("type", "string")));
        sectionProperties.put("insufficientData", Map.of("type", "boolean"));
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("type", "object");
        section.put("properties", sectionProperties);
        section.put("required", List.of("text", "evidenceReferences", "insufficientData"));
        section.put("additionalProperties", false);
        Map<String, Object> sections = new LinkedHashMap<>();
        List.of("context", "observedKnowledge", "strengths", "concerns", "synthesis")
                .forEach(name -> sections.put(name, section));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", sections);
        schema.put("required", List.copyOf(sections.keySet()));
        schema.put("additionalProperties", false);
        return schema;
    }

    private static LlmGenerationException failure(LlmGenerationException.Kind kind, String message) {
        return new LlmGenerationException(kind, message);
    }

    private record GenerateRequest(String model, String prompt, String system, boolean stream,
                                   Map<String, Object> format) { }
}
