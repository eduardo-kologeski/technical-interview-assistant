package com.kologeski.interviewassistant.security;

import com.kologeski.interviewassistant.audit.MutableClock;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitFilterTest {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-28T00:00:00Z"));
    private final AuthRateLimitFilter filter = new AuthRateLimitFilter(clock, Duration.ofSeconds(60), 2, 1, 3);

    @Test
    void limitsAuthenticationRequestsAndReturnsRetryAfter() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        assertThat(invoke("/api/auth/login", "203.0.113.10", calls).getStatus()).isEqualTo(200);
        assertThat(invoke("/api/auth/login", "203.0.113.10", calls).getStatus()).isEqualTo(200);

        MockHttpServletResponse limited = invoke("/api/auth/login", "203.0.113.10", calls);

        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isEqualTo("60");
        assertThat(limited.getContentAsString()).doesNotContain("203.0.113.10");
        assertThat(calls).hasValue(2);
    }

    @Test
    void resetsWindowAndSeparatesClientsAndEndpoints() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        invoke("/api/auth/register", "203.0.113.1", calls);
        assertThat(invoke("/api/auth/register", "203.0.113.1", calls).getStatus()).isEqualTo(429);
        assertThat(invoke("/api/auth/register", "203.0.113.2", calls).getStatus()).isEqualTo(200);
        assertThat(invoke("/api/auth/refresh", "203.0.113.1", calls).getStatus()).isEqualTo(200);

        clock.advanceTo(Instant.parse("2026-08-28T00:01:01Z"));

        assertThat(invoke("/api/auth/register", "203.0.113.1", calls).getStatus()).isEqualTo(200);
    }

    @Test
    void onlyTrustsForwardedAddressFromPrivateProxy() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        MockHttpServletRequest first = request("/api/auth/register", "10.0.0.8");
        first.addHeader("X-Forwarded-For", "198.51.100.20");
        filter.doFilter(first, new MockHttpServletResponse(), (request, response) -> calls.incrementAndGet());

        MockHttpServletRequest second = request("/api/auth/register", "10.0.0.8");
        second.addHeader("X-Forwarded-For", "198.51.100.21");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(second, response, (request, ignored) -> calls.incrementAndGet());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(calls).hasValue(2);
    }

    private MockHttpServletResponse invoke(String path, String address, AtomicInteger calls) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(path, address), response, (request, ignored) -> calls.incrementAndGet());
        return response;
    }

    private MockHttpServletRequest request(String path, String address) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(address);
        return request;
    }
}
