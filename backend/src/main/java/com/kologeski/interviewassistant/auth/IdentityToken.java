package com.kologeski.interviewassistant.auth;

import com.kologeski.interviewassistant.user.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "identity_tokens")
public class IdentityToken {
    public enum Purpose { EMAIL_VERIFICATION, PASSWORD_RESET }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private Purpose purpose;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "used_at")
    private Instant usedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdentityToken() { }
    public IdentityToken(User user, Purpose purpose, String tokenHash, Instant expiresAt, Instant createdAt) {
        this.user = user; this.purpose = purpose; this.tokenHash = tokenHash;
        this.expiresAt = expiresAt; this.createdAt = createdAt;
    }
    public User user() { return user; }
    public Purpose purpose() { return purpose; }
    public boolean availableAt(Instant now) { return usedAt == null && expiresAt.isAfter(now); }
    public void use(Instant now) {
        if (!availableAt(now)) throw new IdentityTokenUnavailableException();
        usedAt = now;
    }
    public void invalidate(Instant now) { if (usedAt == null) usedAt = now; }

    public static final class IdentityTokenUnavailableException extends RuntimeException {
        public IdentityTokenUnavailableException() { super("Token inválido ou expirado."); }
    }
}
