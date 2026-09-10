package com.kologeski.interviewassistant.user;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, unique = true, length = 180)
    private String email;
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;
    @Column(name = "session_version", nullable = false)
    private long sessionVersion;
    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected User() { }

    public User(String name, String email, String passwordHash, Instant now) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public boolean isEmailVerified() { return emailVerifiedAt != null; }
    public long getSessionVersion() { return sessionVersion; }
    public Instant getDeletedAt() { return deletedAt; }
    public boolean isDeleted() { return deletedAt != null; }

    public void verifyEmail(Instant now) {
        if (emailVerifiedAt == null) emailVerifiedAt = now;
        updatedAt = now;
    }

    public void resetPassword(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        this.sessionVersion++;
        this.updatedAt = now;
    }

    public void updateProfile(String name, String email, Instant now) {
        this.name = name;
        this.email = email;
        this.updatedAt = now;
    }

    public void anonymize(Instant now) {
        if (deletedAt != null) return;
        this.name = "Usuário excluído";
        this.email = "deleted+" + id + "+" + java.util.UUID.randomUUID() + "@invalid.local";
        this.passwordHash = java.util.UUID.randomUUID().toString();
        this.emailVerifiedAt = null;
        this.deletedAt = now;
        this.sessionVersion++;
        this.updatedAt = now;
    }
}
