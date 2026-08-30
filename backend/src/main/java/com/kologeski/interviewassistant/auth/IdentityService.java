package com.kologeski.interviewassistant.auth;

import com.kologeski.interviewassistant.audit.CurrentActor;
import com.kologeski.interviewassistant.user.*;
import org.slf4j.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class IdentityService {
    private static final Logger log = LoggerFactory.getLogger(IdentityService.class);
    private final IdentityTokenRepository tokens;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwords;
    private final IdentityDelivery delivery;
    private final CurrentActor actor;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public IdentityService(IdentityTokenRepository tokens, UserRepository users, RefreshTokenRepository refreshTokens,
                           PasswordEncoder passwords, IdentityDelivery delivery, CurrentActor actor, Clock clock) {
        this.tokens = tokens; this.users = users; this.refreshTokens = refreshTokens; this.passwords = passwords;
        this.delivery = delivery; this.actor = actor; this.clock = clock;
    }

    @Transactional
    public IdentityDelivery.DeliveryReceipt requestVerification() {
        User user = users.findById(Long.valueOf(actor.required().id())).orElseThrow();
        if (user.isEmailVerified()) return new IdentityDelivery.DeliveryReceipt("ALREADY_VERIFIED", null);
        var receipt = issue(user, IdentityToken.Purpose.EMAIL_VERIFICATION, Duration.ofHours(24));
        log.info("event=email_verification_requested userId={} deliveryStatus={}", user.getId(), receipt.status());
        return receipt;
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        Instant now = clock.instant();
        IdentityToken token = required(rawToken, IdentityToken.Purpose.EMAIL_VERIFICATION);
        token.use(now); token.user().verifyEmail(now); tokens.save(token); users.save(token.user());
        log.info("event=email_verified userId={}", token.user().getId());
    }

    @Transactional
    public IdentityDelivery.DeliveryReceipt requestPasswordReset(String email) {
        User user = users.findByEmail(normalize(email)).orElse(null);
        if (user == null) return new IdentityDelivery.DeliveryReceipt("ACCEPTED", null);
        var receipt = issue(user, IdentityToken.Purpose.PASSWORD_RESET, Duration.ofMinutes(30));
        log.info("event=password_reset_requested userId={} deliveryStatus={}", user.getId(), receipt.status());
        return new IdentityDelivery.DeliveryReceipt("ACCEPTED", receipt.developmentPath());
    }

    @Transactional
    public void resetPassword(String rawToken, String password) {
        Instant now = clock.instant();
        IdentityToken token = required(rawToken, IdentityToken.Purpose.PASSWORD_RESET);
        token.use(now); token.user().resetPassword(passwords.encode(password), now);
        tokens.save(token); users.save(token.user()); refreshTokens.revokeAllByUserId(token.user().getId(), now);
        log.info("event=password_reset_completed userId={}", token.user().getId());
    }

    private IdentityDelivery.DeliveryReceipt issue(User user, IdentityToken.Purpose purpose, Duration ttl) {
        Instant now = clock.instant();
        tokens.lockActiveByUserAndPurpose(user.getId(), purpose).forEach(token -> token.invalidate(now));
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new IdentityToken(user, purpose, hash(raw), now.plus(ttl), now));
        return delivery.deliver(purpose, user.getEmail(), raw);
    }

    private IdentityToken required(String raw, IdentityToken.Purpose purpose) {
        if (raw == null || raw.length() > 4096) throw new IdentityToken.IdentityTokenUnavailableException();
        IdentityToken token = tokens.lockByHashAndPurpose(hash(raw), purpose)
                .orElseThrow(IdentityToken.IdentityTokenUnavailableException::new);
        if (!token.availableAt(clock.instant())) throw new IdentityToken.IdentityTokenUnavailableException();
        return token;
    }
    private String normalize(String email) { return email == null ? "" : email.trim().toLowerCase(Locale.ROOT); }
    private String hash(String raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
