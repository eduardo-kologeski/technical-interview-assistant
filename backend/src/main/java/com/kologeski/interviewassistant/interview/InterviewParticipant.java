package com.kologeski.interviewassistant.interview;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "interview_participants", uniqueConstraints = @UniqueConstraint(columnNames = {"interview_id", "user_id"}))
public class InterviewParticipant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "interview_id", nullable = false) private UUID interviewId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private InterviewStatus status;
    @Column(name = "assessment_data", nullable = false, columnDefinition = "text") private String assessmentData;
    @Version @Column(nullable = false) private long version;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected InterviewParticipant() { }
    public InterviewParticipant(UUID interviewId, Long userId, Instant now) {
        this.interviewId = interviewId; this.userId = userId; this.status = InterviewStatus.IN_PROGRESS;
        this.assessmentData = "{}"; this.createdAt = now; this.updatedAt = now;
    }
    public void save(String data, Instant now) {
        if (status == InterviewStatus.COMPLETED) throw new IllegalStateException("parecer individual já finalizado");
        if (data == null || data.isBlank() || data.length() > 1_000_000) throw new IllegalArgumentException("assessmentData inválido");
        assessmentData = data; updatedAt = now;
    }
    public void complete(Instant now) { if (status != InterviewStatus.COMPLETED) { status = InterviewStatus.COMPLETED; completedAt = now; updatedAt = now; } }
    public Long userId() { return userId; }
    public InterviewStatus status() { return status; }
    public String assessmentData() { return assessmentData; }
    public long version() { return version; }
    public Instant completedAt() { return completedAt; }
}
