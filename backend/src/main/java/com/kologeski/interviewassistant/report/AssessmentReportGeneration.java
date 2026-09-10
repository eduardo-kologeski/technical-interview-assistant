package com.kologeski.interviewassistant.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "assessment_report_generations")
public class AssessmentReportGeneration {
    public enum Kind { ORIGINAL, EXPERIMENTAL }

    @Id private UUID id;
    @Column(name = "report_id", nullable = false, updatable = false) private UUID reportId;
    @Column(name = "assessment_id", nullable = false, updatable = false) private UUID assessmentId;
    @Column(name = "owner_user_id", nullable = false, updatable = false) private Long ownerUserId;
    @Column(name = "sequence_number", nullable = false, updatable = false) private int sequence;
    @Column(name = "generation_kind", nullable = false, updatable = false) private String kind;
    @Column(name = "idempotency_key", nullable = false, updatable = false) private UUID idempotencyKey;
    @Column(length = 2000, updatable = false) private String justification;
    @Column(name = "prompt_version", nullable = false, updatable = false) private String promptVersion;
    @Column(name = "model_name", nullable = false, updatable = false) private String modelName;
    @Column(name = "generated_draft", nullable = false, updatable = false, columnDefinition = "text") private String generatedDraft;
    @Column(name = "generation_latency_millis", updatable = false) private Long generationLatencyMillis;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "created_by", nullable = false, updatable = false) private String createdBy;

    protected AssessmentReportGeneration() { }

    public static AssessmentReportGeneration original(AssessmentReport report, String actor) {
        return create(report, 1, Kind.ORIGINAL, report.idempotencyKey(), null, report.promptVersion(),
                report.modelName(), report.originalDraft(), report.generationLatencyMillis(), report.createdAt(), actor);
    }

    public static AssessmentReportGeneration experimental(AssessmentReport report, int sequence,
                                                           UUID idempotencyKey, String justification,
                                                           String promptVersion, String modelName,
                                                           String generatedDraft, long latencyMillis,
                                                           Instant now, String actor) {
        String reason = required(justification, "justification");
        if (reason.length() < 10 || reason.length() > 2_000) {
            throw new IllegalArgumentException("A justificativa da regeneração deve ter entre 10 e 2000 caracteres.");
        }
        return create(report, sequence, Kind.EXPERIMENTAL, idempotencyKey, reason, promptVersion,
                modelName, generatedDraft, latencyMillis, now, actor);
    }

    private static AssessmentReportGeneration create(AssessmentReport report, int sequence, Kind kind,
                                                       UUID idempotencyKey, String justification,
                                                       String promptVersion, String modelName,
                                                       String generatedDraft, Long latencyMillis,
                                                       Instant now, String actor) {
        if (sequence < 1 || latencyMillis != null && (latencyMillis < 0 || latencyMillis > 86_400_000L)) {
            throw new IllegalArgumentException("Metadados de geração inválidos.");
        }
        AssessmentReportGeneration value = new AssessmentReportGeneration();
        value.id = UUID.randomUUID(); value.reportId = report.id(); value.assessmentId = report.assessmentId();
        value.ownerUserId = report.ownerUserId(); value.sequence = sequence; value.kind = kind.name();
        value.idempotencyKey = Objects.requireNonNull(idempotencyKey); value.justification = justification;
        value.promptVersion = required(promptVersion, "promptVersion"); value.modelName = required(modelName, "modelName");
        value.generatedDraft = required(generatedDraft, "generatedDraft"); value.generationLatencyMillis = latencyMillis;
        value.createdAt = Objects.requireNonNull(now); value.createdBy = required(actor, "actor");
        return value;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    public UUID id() { return id; }
    public UUID reportId() { return reportId; }
    public UUID assessmentId() { return assessmentId; }
    public Long ownerUserId() { return ownerUserId; }
    public int sequence() { return sequence; }
    public Kind kind() { return Kind.valueOf(kind); }
    public UUID idempotencyKey() { return idempotencyKey; }
    public String justification() { return justification; }
    public String promptVersion() { return promptVersion; }
    public String modelName() { return modelName; }
    public String generatedDraft() { return generatedDraft; }
    public Long generationLatencyMillis() { return generationLatencyMillis; }
    public Instant createdAt() { return createdAt; }
    public String createdBy() { return createdBy; }
}
