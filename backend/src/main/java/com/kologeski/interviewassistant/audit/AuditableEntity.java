package com.kologeski.interviewassistant.audit;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import java.time.Instant;

@MappedSuperclass
public abstract class AuditableEntity {
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    protected void registerCreation(String actorId, Instant occurredAt) {
        if (createdAt != null) throw new IllegalStateException("creation audit is immutable");
        createdAt = required(occurredAt, "occurredAt");
        createdBy = requiredText(actorId, "actorId");
        updatedAt = createdAt;
        updatedBy = createdBy;
    }

    protected void registerUpdate(String actorId, Instant occurredAt) {
        if (createdAt == null) throw new IllegalStateException("creation audit must be registered first");
        updatedAt = required(occurredAt, "occurredAt");
        updatedBy = requiredText(actorId, "actorId");
    }

    private static <T> T required(T value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    protected static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    public Instant createdAt() { return createdAt; }
    public String createdBy() { return createdBy; }
    public Instant updatedAt() { return updatedAt; }
    public String updatedBy() { return updatedBy; }
}
