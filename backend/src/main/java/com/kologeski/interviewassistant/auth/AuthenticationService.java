package com.kologeski.interviewassistant.auth;

import com.kologeski.interviewassistant.user.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.kologeski.interviewassistant.deployment.DeploymentProperties;

@Service
public class AuthenticationService {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;
    private final DeploymentProperties deployment;

    public AuthenticationService(UserRepository users, PasswordEncoder passwords,
                                 JwtService jwt, RefreshTokenService refreshTokens,
                                  DeploymentProperties deployment) {
        this.users = users; this.passwords = passwords; this.jwt = jwt; this.refreshTokens = refreshTokens;
        this.deployment = deployment;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, RefreshTokenService.Metadata metadata) {
        if (!deployment.publicSignupEnabled()) throw new PublicSignupDisabledException();
        String email = normalize(request.email());
        if (users.existsByEmail(email)) throw new DuplicateEmailException();
        User user = users.save(new User(request.name().trim(), email,
                passwords.encode(request.password()), Instant.now()));
        log.info("event=user_registered userId={} rememberMe={}", user.getId(), Boolean.TRUE.equals(request.rememberMe()));
        return session(user, Boolean.TRUE.equals(request.rememberMe()), metadata);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, RefreshTokenService.Metadata metadata) {
        User user = users.findByEmail(normalize(request.email())).orElse(null);
        if (user == null || !passwords.matches(request.password(), user.getPasswordHash())) {
            log.warn("event=auth_login_failed reason=invalid_credentials");
            throw new BadCredentialsException("Credenciais inválidas.");
        }
        log.info("event=auth_login_succeeded userId={} rememberMe={}", user.getId(), request.rememberMe());
        return session(user, request.rememberMe(), metadata);
    }

    @Transactional
    public AuthResponse refresh(String rawToken, RefreshTokenService.Metadata metadata) {
        RefreshTokenService.Result result = refreshTokens.rotate(rawToken, metadata);
        log.info("event=auth_token_refreshed userId={}", result.token().getUser().getId());
        return persistent(result.token().getUser(), result.rawToken());
    }

    @Transactional public void logout(String rawToken) { refreshTokens.revoke(rawToken); }

    private AuthResponse session(User user, boolean rememberMe, RefreshTokenService.Metadata metadata) {
        if (!rememberMe) return new AuthResponse(jwt.generate(user), null, jwt.expirationSeconds());
        RefreshTokenService.Result result = refreshTokens.create(user, metadata);
        return persistent(user, result.rawToken());
    }
    private AuthResponse persistent(User user, String refreshToken) {
        return new AuthResponse(jwt.generate(user), refreshToken, jwt.expirationSeconds());
    }
    private String normalize(String email) { return email.trim().toLowerCase(); }

    public record AuthResponse(String accessToken, String refreshToken, long expiresIn) { }
    public static final class DuplicateEmailException extends RuntimeException {
        public DuplicateEmailException() { super("E-mail já cadastrado."); }
    }
    public static final class PublicSignupDisabledException extends RuntimeException {
        public PublicSignupDisabledException() { super("Cadastro público indisponível nesta instalação."); }
    }
}
