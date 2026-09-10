package com.kologeski.interviewassistant.company;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "company_invitations")
public class CompanyInvitation {
    @Id private UUID id;
    @Column(name = "company_id", nullable = false) private UUID companyId;
    @Column(nullable = false, length = 180) private String email;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private CompanyRole role;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private InvitationStatus status;
    @Column(name = "invited_by_user_id", nullable = false) private Long invitedByUserId;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "accepted_at") private Instant acceptedAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected CompanyInvitation() { }

    public CompanyInvitation(UUID companyId, String email, CompanyRole role, String tokenHash,
                             Long invitedByUserId, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.email = email.trim().toLowerCase();
        this.role = role;
        this.tokenHash = tokenHash;
        this.status = InvitationStatus.PENDING;
        this.invitedByUserId = invitedByUserId;
        this.createdAt = now;
        this.updatedAt = now;
        this.expiresAt = expiresAt;
    }

    public UUID id() { return id; }
    public UUID companyId() { return companyId; }
    public String email() { return email; }
    public CompanyRole role() { return role; }
    public InvitationStatus status() { return status; }
    public Instant expiresAt() { return expiresAt; }
    public Long invitedByUserId() { return invitedByUserId; }
    public Instant createdAt() { return createdAt; }

    public void redactRecipient(Instant now) {
        email = "deleted-invite+" + id + "@invalid.local";
        updatedAt = now;
    }

    public void accept(Instant now) {
        requirePending(now);
        status = InvitationStatus.ACCEPTED;
        acceptedAt = now;
        updatedAt = now;
    }

    public void revoke(Instant now) {
        if (status != InvitationStatus.PENDING) throw new InvitationService.InvitationUnavailableException();
        status = InvitationStatus.REVOKED;
        revokedAt = now;
        updatedAt = now;
    }

    public void expire(Instant now) {
        if (status == InvitationStatus.PENDING && !expiresAt.isAfter(now)) {
            status = InvitationStatus.EXPIRED;
            updatedAt = now;
        }
    }

    private void requirePending(Instant now) {
        expire(now);
        if (status != InvitationStatus.PENDING) throw new IllegalStateException("invitation is not available");
    }
}
