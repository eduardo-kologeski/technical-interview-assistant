package com.kologeski.interviewassistant.report;

public class LlmGenerationException extends RuntimeException {
    private final Kind kind;

    public LlmGenerationException(Kind kind, String message) { super(message); this.kind = kind; }
    public LlmGenerationException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() { return kind; }

    public enum Kind { CONFIGURATION, INPUT_TOO_LARGE, UNAVAILABLE, TIMEOUT, MODEL_NOT_FOUND, INVALID_RESPONSE }
}
