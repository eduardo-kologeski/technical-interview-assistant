package com.kologeski.interviewassistant.company;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "company_memberships", uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "user_id"}))
public class CompanyMembership {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "company_id", nullable = false) private UUID companyId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private CompanyRole role;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected CompanyMembership() { }

    public CompanyMembership(UUID companyId, Long userId, CompanyRole role, Instant now) {
        this.companyId = companyId;
        this.userId = userId;
        this.role = role;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long id() { return id; }
    public UUID companyId() { return companyId; }
    public Long userId() { return userId; }
    public CompanyRole role() { return role; }
    public boolean active() { return active; }

    public void deactivate(Instant now) {
        active = false;
        updatedAt = now;
    }
    public void changeRole(CompanyRole value, Instant now) { role = java.util.Objects.requireNonNull(value); updatedAt = now; }
}
