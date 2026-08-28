package com.kologeski.interviewassistant.auth;

import com.kologeski.interviewassistant.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository repository;
    private final Duration expiration;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository,
                               @Value("${app.refresh-token.expiration}") Duration expiration) {
        this.repository = repository; this.expiration = expiration;
    }

    @Transactional
    public Result create(User user, Metadata metadata) {
        String raw = generate(); Instant now = Instant.now();
        RefreshToken token = repository.save(new RefreshToken(user, hash(raw),
                trim(metadata.deviceName(), 120), trim(metadata.userAgent(), 512),
                trim(metadata.ipAddress(), 45), now, now.plus(expiration)));
        return new Result(raw, token);
    }

    @Transactional
    public Result rotate(String raw, Metadata metadata) {
        RefreshToken current = valid(raw); Instant now = Instant.now();
        current.markUsed(now); current.revoke(now);
        return create(current.getUser(), new Metadata(
                fallback(metadata.deviceName(), current.getDeviceName()),
                fallback(metadata.userAgent(), current.getUserAgent()),
                fallback(metadata.ipAddress(), current.getIpAddress())));
    }

    @Transactional public void revoke(String raw) { valid(raw).revoke(Instant.now()); }

    private RefreshToken valid(String raw) {
        RefreshToken token = repository.findByTokenHash(hash(raw))
                .orElseThrow(() -> new BadCredentialsException("Refresh token inválido."));
        if (token.isRevoked() || !token.getExpiresAt().isAfter(Instant.now()))
            throw new BadCredentialsException("Refresh token inválido.");
        return token;
    }
    private String generate() { byte[] value = new byte[64]; random.nextBytes(value); return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    private String hash(String raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private String fallback(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String trim(String value, int max) { if (value == null || value.isBlank()) return null; value = value.trim(); return value.length() <= max ? value : value.substring(0, max); }
    public record Metadata(String deviceName, String userAgent, String ipAddress) { }
    public record Result(String rawToken, RefreshToken token) { }
}
