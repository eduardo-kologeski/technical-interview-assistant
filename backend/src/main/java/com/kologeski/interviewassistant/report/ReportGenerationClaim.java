package com.kologeski.interviewassistant.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "report_generation_claims")
public class ReportGenerationClaim {
    @Id @Column(name = "assessment_id") private UUID assessmentId;
    @Column(name = "owner_user_id", nullable = false, updatable = false) private Long ownerUserId;
    @Column(name = "idempotency_key", nullable = false, updatable = false) private UUID idempotencyKey;
    @Column(name = "source_version", nullable = false, updatable = false) private long sourceVersion;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false, updatable = false) private Instant expiresAt;

    protected ReportGenerationClaim() { }

    public ReportGenerationClaim(UUID assessmentId, Long ownerUserId, UUID idempotencyKey,
                                 long sourceVersion, Instant createdAt, Instant expiresAt) {
        this.assessmentId = java.util.Objects.requireNonNull(assessmentId);
        this.ownerUserId = java.util.Objects.requireNonNull(ownerUserId);
        this.idempotencyKey = java.util.Objects.requireNonNull(idempotencyKey);
        this.sourceVersion = sourceVersion;
        this.createdAt = java.util.Objects.requireNonNull(createdAt);
        this.expiresAt = java.util.Objects.requireNonNull(expiresAt);
        if (!expiresAt.isAfter(createdAt)) throw new IllegalArgumentException("expiresAt must be after createdAt");
    }

    public UUID assessmentId() { return assessmentId; }
    public Long ownerUserId() { return ownerUserId; }
    public UUID idempotencyKey() { return idempotencyKey; }
    public long sourceVersion() { return sourceVersion; }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt; }
    public boolean expiredAt(Instant instant) { return !expiresAt.isAfter(instant); }
}
