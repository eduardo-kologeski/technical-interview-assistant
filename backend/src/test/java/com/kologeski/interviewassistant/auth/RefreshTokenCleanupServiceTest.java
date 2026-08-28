package com.kologeski.interviewassistant.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kologeski.interviewassistant.audit.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RefreshTokenCleanupServiceTest {
    @Test
    void deletesOnlyTokensOlderThanTheConfiguredRetention() {
        var repository = Mockito.mock(RefreshTokenRepository.class);
        var now = Instant.parse("2026-08-28T12:00:00Z");
        var cutoff = Instant.parse("2026-07-29T12:00:00Z");
        when(repository.deleteEligibleForCleanup(cutoff)).thenReturn(4);
        var cleanup = new RefreshTokenCleanupService(
                repository, new MutableClock(now), Duration.ofDays(30));

        assertEquals(4, cleanup.removeExpiredAndRevokedTokens());
        verify(repository).deleteEligibleForCleanup(cutoff);
    }
}
