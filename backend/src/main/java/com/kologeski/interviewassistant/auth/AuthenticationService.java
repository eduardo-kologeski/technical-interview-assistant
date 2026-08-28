package com.kologeski.interviewassistant.auth;

import com.kologeski.interviewassistant.user.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class AuthenticationService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;

    public AuthenticationService(UserRepository users, PasswordEncoder passwords,
                                 JwtService jwt, RefreshTokenService refreshTokens) {
        this.users = users; this.passwords = passwords; this.jwt = jwt; this.refreshTokens = refreshTokens;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, RefreshTokenService.Metadata metadata) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) throw new DuplicateEmailException();
        User user = users.save(new User(request.name().trim(), email,
                passwords.encode(request.password()), Instant.now()));
        return session(user, Boolean.TRUE.equals(request.rememberMe()), metadata);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, RefreshTokenService.Metadata metadata) {
        User user = users.findByEmail(normalize(request.email()))
                .orElseThrow(() -> new BadCredentialsException("Credenciais inválidas."));
        if (!passwords.matches(request.password(), user.getPasswordHash()))
            throw new BadCredentialsException("Credenciais inválidas.");
        return session(user, request.rememberMe(), metadata);
    }

    @Transactional
    public AuthResponse refresh(String rawToken, RefreshTokenService.Metadata metadata) {
        RefreshTokenService.Result result = refreshTokens.rotate(rawToken, metadata);
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
}
