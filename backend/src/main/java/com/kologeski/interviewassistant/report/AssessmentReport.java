package com.kologeski.interviewassistant.report;

import com.kologeski.interviewassistant.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "assessment_reports")
public class AssessmentReport extends AuditableEntity {
    @Id private UUID id;
    @Column(name = "assessment_id", nullable = false, updatable = false) private UUID assessmentId;
    @Column(name = "owner_user_id", nullable = false, updatable = false) private Long ownerUserId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ReportStatus status;
    @Column(name = "source_version", nullable = false, updatable = false) private long sourceVersion;
    @Column(name = "idempotency_key", nullable = false, updatable = false) private UUID idempotencyKey;
    @Column(name = "prompt_version", nullable = false, updatable = false) private String promptVersion;
    @Column(name = "model_name", nullable = false, updatable = false) private String modelName;
    @Column(name = "context_snapshot", nullable = false, updatable = false, columnDefinition = "text")
    private String contextSnapshot;
    @Column(name = "original_draft", nullable = false, updatable = false, columnDefinition = "text")
    private String originalDraft;
    @Version @Column(nullable = false) private long version;

    protected AssessmentReport() { }

    public static AssessmentReport draft(UUID assessmentId, Long ownerUserId, long sourceVersion,
                                         UUID idempotencyKey, String promptVersion, String modelName,
                                         String contextSnapshot, String originalDraft, Instant now) {
        AssessmentReport report = new AssessmentReport();
        report.id = UUID.randomUUID();
        report.assessmentId = Objects.requireNonNull(assessmentId);
        report.ownerUserId = Objects.requireNonNull(ownerUserId);
        report.status = ReportStatus.DRAFT;
        report.sourceVersion = sourceVersion;
        report.idempotencyKey = Objects.requireNonNull(idempotencyKey);
        report.promptVersion = requiredText(promptVersion, "promptVersion");
        report.modelName = requiredText(modelName, "modelName");
        report.contextSnapshot = requiredText(contextSnapshot, "contextSnapshot");
        report.originalDraft = requiredText(originalDraft, "originalDraft");
        report.registerCreation(ownerUserId.toString(), now);
        return report;
    }

    public UUID id() { return id; }
    public UUID assessmentId() { return assessmentId; }
    public Long ownerUserId() { return ownerUserId; }
    public ReportStatus status() { return status; }
    public long sourceVersion() { return sourceVersion; }
    public UUID idempotencyKey() { return idempotencyKey; }
    public String promptVersion() { return promptVersion; }
    public String modelName() { return modelName; }
    public String contextSnapshot() { return contextSnapshot; }
    public String originalDraft() { return originalDraft; }
    public long version() { return version; }
}
