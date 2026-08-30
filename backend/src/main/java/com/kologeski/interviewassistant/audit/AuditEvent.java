package com.kologeski.interviewassistant.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent {
    @Id private UUID id;
    @Column(name = "company_id", nullable = false) private UUID companyId;
    @Column(nullable = false, length = 40) private String category;
    @Column(name = "event_type", nullable = false, length = 100) private String eventType;
    @Column(name = "resource_type", length = 60) private String resourceType;
    @Column(name = "resource_id", length = 100) private String resourceId;
    @Column(name = "actor_user_id") private Long actorUserId;
    @Column(name = "correlation_id", length = 64) private String correlationId;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;

    protected AuditEvent() { }
    public AuditEvent(UUID companyId, String category, String eventType, String resourceType,
                      String resourceId, Long actorUserId, String correlationId, Instant occurredAt) {
        this.id = UUID.randomUUID(); this.companyId = companyId; this.category = category;
        this.eventType = eventType; this.resourceType = resourceType; this.resourceId = resourceId;
        this.actorUserId = actorUserId; this.correlationId = correlationId; this.occurredAt = occurredAt;
    }
    public UUID id() { return id; }
    public UUID companyId() { return companyId; }
    public String category() { return category; }
    public String eventType() { return eventType; }
    public String resourceType() { return resourceType; }
    public String resourceId() { return resourceId; }
    public Long actorUserId() { return actorUserId; }
    public String correlationId() { return correlationId; }
    public Instant occurredAt() { return occurredAt; }
}
