package com.kologeski.interviewassistant.report;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.llm.ollama")
public class OllamaProperties {
    private String baseUrl = "";
    private String model = "";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration responseTimeout = Duration.ofSeconds(60);
    private int maxInputChars = 120_000;
    private int maxResponseChars = 100_000;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl == null ? "" : baseUrl.trim(); }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model == null ? "" : model.trim(); }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration value) { connectTimeout = positive(value, "connectTimeout"); }
    public Duration getResponseTimeout() { return responseTimeout; }
    public void setResponseTimeout(Duration value) { responseTimeout = positive(value, "responseTimeout"); }
    public int getMaxInputChars() { return maxInputChars; }
    public void setMaxInputChars(int value) { maxInputChars = positive(value, "maxInputChars"); }
    public int getMaxResponseChars() { return maxResponseChars; }
    public void setMaxResponseChars(int value) { maxResponseChars = positive(value, "maxResponseChars"); }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " deve ser positivo.");
        }
        return value;
    }

    private static int positive(int value, String field) {
        if (value <= 0) throw new IllegalArgumentException(field + " deve ser positivo.");
        return value;
    }
}
