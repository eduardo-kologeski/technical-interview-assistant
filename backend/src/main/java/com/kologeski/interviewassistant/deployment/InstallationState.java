package com.kologeski.interviewassistant.deployment;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "installation_state")
public class InstallationState {
    @Id private Short id;
    @Column(name = "setup_completed", nullable = false) private boolean setupCompleted;
    @Column(name = "completed_at") private Instant completedAt;

    protected InstallationState() { }
    public InstallationState(short id) { this.id = id; }

    public boolean setupCompleted() { return setupCompleted; }
    public Instant completedAt() { return completedAt; }
    public void complete(Instant now) { setupCompleted = true; completedAt = now; }
}
