package com.kologeski.interviewassistant.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);
    private static final Map<String, String> ENDPOINTS = Map.of(
            "/api/auth/login", "login",
            "/api/auth/register", "register",
            "/api/auth/refresh", "refresh"
    );

    private final Clock clock;
    private final long windowSeconds;
    private final Map<String, Integer> limits;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(Clock clock,
                               @Value("${app.security.rate-limit.window:60s}") java.time.Duration window,
                               @Value("${app.security.rate-limit.login:10}") int loginLimit,
                               @Value("${app.security.rate-limit.register:5}") int registerLimit,
                               @Value("${app.security.rate-limit.refresh:30}") int refreshLimit) {
        this.clock = clock;
        this.windowSeconds = Math.max(1, window.toSeconds());
        this.limits = Map.of("login", positive(loginLimit), "register", positive(registerLimit),
                "refresh", positive(refreshLimit));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String endpoint = "POST".equalsIgnoreCase(request.getMethod()) ? ENDPOINTS.get(request.getRequestURI()) : null;
        if (endpoint == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Instant now = clock.instant();
        String key = endpoint + ':' + clientAddress(request);
        AtomicBoolean allowed = new AtomicBoolean();
        AtomicLong retryAfter = new AtomicLong(windowSeconds);
        windows.compute(key, (ignored, current) -> {
            if (current == null || !current.expiresAt().isAfter(now)) {
                allowed.set(true);
                return new Window(1, now.plusSeconds(windowSeconds));
            }
            retryAfter.set(Math.max(1, current.expiresAt().getEpochSecond() - now.getEpochSecond()));
            if (current.count() >= limits.get(endpoint)) return current;
            allowed.set(true);
            return new Window(current.count() + 1, current.expiresAt());
        });
        if (windows.size() > 10_000) windows.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));

        if (allowed.get()) {
            filterChain.doFilter(request, response);
            return;
        }
        log.warn("event=auth_rate_limited endpoint={}", endpoint);
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfter.get()));
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"status\":429,\"title\":\"Too Many Requests\",\"detail\":\"Muitas tentativas. Aguarde antes de tentar novamente.\"}");
    }

    private String clientAddress(HttpServletRequest request) {
        String remote = sanitized(request.getRemoteAddr());
        if (isTrustedProxy(remote)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null) {
                String first = sanitized(forwarded.split(",", 2)[0]);
                if (!first.isBlank()) return first;
            }
        }
        return remote.isBlank() ? "unknown" : remote;
    }

    private String sanitized(String value) {
        if (value == null) return "";
        value = value.trim();
        return value.length() <= 45 && value.matches("[0-9A-Fa-f:.]+") ? value : "";
    }

    private boolean isTrustedProxy(String address) {
        if (address.equals("127.0.0.1") || address.equals("::1") || address.startsWith("10.") || address.startsWith("192.168."))
            return true;
        if (!address.startsWith("172.")) return false;
        String[] parts = address.split("\\.");
        if (parts.length < 2) return false;
        try { int second = Integer.parseInt(parts[1]); return second >= 16 && second <= 31; }
        catch (NumberFormatException ignored) { return false; }
    }

    private static int positive(int value) {
        if (value < 1) throw new IllegalArgumentException("rate limit must be positive");
        return value;
    }

    private record Window(int count, Instant expiresAt) { }
}
