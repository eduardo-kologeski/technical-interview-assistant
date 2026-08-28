package com.kologeski.interviewassistant.interview;

import com.kologeski.interviewassistant.audit.AuditableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "interviews")
public class Interview extends AuditableEntity {
    @Id private UUID id;
    @Column(name = "vacancy_id", nullable = false, updatable = false) private UUID vacancyId;
    @Column(name = "candidate_name", nullable = false) private String candidateName;
    @Column(name = "vacancy_title_snapshot", nullable = false, updatable = false) private String vacancyTitleSnapshot;
    @Column(name = "seniority_snapshot", nullable = false, updatable = false) private String senioritySnapshot;
    @Column(name = "client_company") private String clientCompany;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "interview_requirement_snapshots", joinColumns = @JoinColumn(name = "interview_id"))
    @Column(name = "requirement_name", nullable = false)
    private List<String> requirementSnapshot = new ArrayList<>();
    @Column(name = "interviewer_id") private String interviewerId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private InterviewStatus status;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "assessment_finalized_at") private Instant assessmentFinalizedAt;
    @Column(name = "assessment_finalized_by") private String assessmentFinalizedBy;
    @Column(name = "last_pdf_generated_at") private Instant lastPdfGeneratedAt;
    @Column(name = "last_pdf_generated_by") private String lastPdfGeneratedBy;

    protected Interview() { }

    public Interview(UUID vacancyId, String vacancyTitle, List<String> requirementSnapshot,
                     String candidateName, String actorId, Instant now) {
        this(vacancyId, vacancyTitle, "Não informada", requirementSnapshot, candidateName, "", actorId, now);
    }

    public Interview(UUID vacancyId, String vacancyTitle, String seniority, List<String> requirementSnapshot,
                     String candidateName, String clientCompany, String actorId, Instant now) {
        this.id = UUID.randomUUID();
        this.vacancyId = vacancyId;
        this.vacancyTitleSnapshot = requiredText(vacancyTitle, "vacancyTitle");
        this.senioritySnapshot = requiredText(seniority, "seniority");
        this.clientCompany = clientCompany == null ? "" : clientCompany.trim();
        this.requirementSnapshot = new ArrayList<>(requirementSnapshot == null ? List.of() : requirementSnapshot);
        this.candidateName = requiredText(candidateName, "candidateName");
        this.status = InterviewStatus.CREATED;
        registerCreation(actorId, now);
    }

    public void start(String actorId, Instant now) {
        if (status != InterviewStatus.CREATED) throw new IllegalStateException("interview cannot be started");
        interviewerId = requiredText(actorId, "actorId");
        startedAt = now;
        status = InterviewStatus.IN_PROGRESS;
        registerUpdate(actorId, now);
    }

    public void complete(String actorId, Instant now) {
        if (status != InterviewStatus.IN_PROGRESS) throw new IllegalStateException("interview is not in progress");
        completedAt = now;
        status = InterviewStatus.COMPLETED;
        registerUpdate(actorId, now);
    }

    public void finalizeAssessment(String actorId, Instant now) {
        if (status != InterviewStatus.COMPLETED) throw new IllegalStateException("interview must be completed");
        assessmentFinalizedAt = now;
        assessmentFinalizedBy = requiredText(actorId, "actorId");
        registerUpdate(actorId, now);
    }

    public void registerPdfGeneration(String actorId, Instant now) {
        lastPdfGeneratedAt = now;
        lastPdfGeneratedBy = requiredText(actorId, "actorId");
        registerUpdate(actorId, now);
    }

    public void renameCandidate(String candidateName, String actorId, Instant now) {
        this.candidateName = requiredText(candidateName, "candidateName");
        registerUpdate(actorId, now);
    }

    public UUID id() { return id; }
    public UUID vacancyId() { return vacancyId; }
    public String candidateName() { return candidateName; }
    public String vacancyTitleSnapshot() { return vacancyTitleSnapshot; }
    public String senioritySnapshot() { return senioritySnapshot; }
    public String clientCompany() { return clientCompany; }
    public List<String> requirementSnapshot() { return List.copyOf(requirementSnapshot); }
    public String interviewerId() { return interviewerId; }
    public InterviewStatus status() { return status; }
    public Instant startedAt() { return startedAt; }
    public Instant completedAt() { return completedAt; }
    public Instant assessmentFinalizedAt() { return assessmentFinalizedAt; }
    public String assessmentFinalizedBy() { return assessmentFinalizedBy; }
    public Instant lastPdfGeneratedAt() { return lastPdfGeneratedAt; }
    public String lastPdfGeneratedBy() { return lastPdfGeneratedBy; }
}
