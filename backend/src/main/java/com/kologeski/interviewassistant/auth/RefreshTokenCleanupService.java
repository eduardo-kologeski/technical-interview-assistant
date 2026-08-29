package com.kologeski.interviewassistant.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenCleanupService {
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupService.class);

    private final RefreshTokenRepository tokens;
    private final Clock clock;
    private final Duration retention;

    public RefreshTokenCleanupService(RefreshTokenRepository tokens, Clock clock,
                                      @Value("${app.refresh-token.cleanup-retention:P30D}") Duration retention) {
        if (retention.isNegative()) throw new IllegalArgumentException("cleanup retention must not be negative");
        this.tokens = tokens;
        this.clock = clock;
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${app.refresh-token.cleanup-interval:PT6H}",
               initialDelayString = "${app.refresh-token.cleanup-initial-delay:PT5M}")
    @Transactional
    public int removeExpiredAndRevokedTokens() {
        Instant cutoff = clock.instant().minus(retention);
        int deleted = tokens.deleteEligibleForCleanup(cutoff);
        if (deleted > 0) log.info("event=refresh_token_cleanup deletedCount={}", deleted);
        return deleted;
    }
}
