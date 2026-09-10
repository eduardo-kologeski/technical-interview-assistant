package com.kologeski.interviewassistant.report;

import com.kologeski.interviewassistant.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "academic_peer_evaluations")
public class AcademicPeerEvaluation extends AuditableEntity {
    @Id private UUID id;
    @Column(name = "study_id", nullable = false, updatable = false) private UUID studyId;
    @Column(name = "assessment_id", nullable = false, updatable = false) private UUID assessmentId;
    @Column(name = "owner_user_id", nullable = false, updatable = false) private Long ownerUserId;
    @Column(name = "reviewer_code", nullable = false, length = 80, updatable = false) private String reviewerCode;
    @Column(nullable = false, columnDefinition = "text") private String evaluation;
    @Column(name = "finalized_at") private Instant finalizedAt;
    @Column(name = "finalized_by", length = 100) private String finalizedBy;
    @Version @Column(nullable = false) private long version;

    protected AcademicPeerEvaluation() { }

    public static AcademicPeerEvaluation create(UUID studyId, UUID assessmentId, Long ownerUserId,
                                                 String reviewerCode, String evaluation, String actor, Instant now) {
        AcademicPeerEvaluation value = new AcademicPeerEvaluation();
        value.id = UUID.randomUUID();
        value.studyId = studyId;
        value.assessmentId = assessmentId;
        value.ownerUserId = ownerUserId;
        value.reviewerCode = requiredCode(reviewerCode);
        value.evaluation = requiredText(evaluation, "evaluation");
        value.registerCreation(actor, now);
        return value;
    }

    public void revise(String evaluation, String actor, Instant now) {
        requireOpen();
        this.evaluation = requiredText(evaluation, "evaluation");
        registerUpdate(actor, now);
    }

    public void finalizeEvaluation(String evaluation, String actor, Instant now) {
        requireOpen();
        this.evaluation = requiredText(evaluation, "evaluation");
        this.finalizedAt = now;
        this.finalizedBy = requiredText(actor, "actor");
        registerUpdate(actor, now);
    }

    private void requireOpen() {
        if (finalizedAt != null) throw new IllegalStateException("A avaliação independente finalizada é imutável.");
    }

    private static String requiredCode(String value) {
        String code = requiredText(value, "reviewerCode");
        if (code.length() < 2 || code.length() > 80)
            throw new IllegalArgumentException("O código do avaliador deve ter entre 2 e 80 caracteres.");
        return code;
    }

    public UUID id() { return id; }
    public UUID studyId() { return studyId; }
    public UUID assessmentId() { return assessmentId; }
    public Long ownerUserId() { return ownerUserId; }
    public String reviewerCode() { return reviewerCode; }
    public String evaluation() { return evaluation; }
    public Instant finalizedAt() { return finalizedAt; }
    public String finalizedBy() { return finalizedBy; }
    public long version() { return version; }
    public boolean finalized() { return finalizedAt != null; }
}
