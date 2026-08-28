package com.kologeski.interviewassistant.auth;

import com.kologeski.interviewassistant.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(name = "device_name", length = 120)
    private String deviceName;
    @Column(name = "user_agent", length = 512)
    private String userAgent;
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "last_used_at")
    private Instant lastUsedAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Column(nullable = false)
    private boolean revoked;

    protected RefreshToken() { }
    public RefreshToken(User user, String tokenHash, String deviceName, String userAgent,
                        String ipAddress, Instant createdAt, Instant expiresAt) {
        this.user = user; this.tokenHash = tokenHash; this.deviceName = deviceName;
        this.userAgent = userAgent; this.ipAddress = ipAddress;
        this.createdAt = createdAt; this.expiresAt = expiresAt;
    }
    public User getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public String getDeviceName() { return deviceName; }
    public String getUserAgent() { return userAgent; }
    public String getIpAddress() { return ipAddress; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public boolean isRevoked() { return revoked; }
    public void markUsed(Instant now) { lastUsedAt = now; }
    public void revoke(Instant now) { revoked = true; revokedAt = now; }
    void expireAt(Instant instant) { expiresAt = instant; }
}
