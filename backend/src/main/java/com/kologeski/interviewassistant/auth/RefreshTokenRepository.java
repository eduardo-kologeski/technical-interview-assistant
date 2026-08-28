package com.kologeski.interviewassistant.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.time.Instant;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from RefreshToken token
             where token.expiresAt < :cutoff
                or (token.revoked = true and token.revokedAt < :cutoff)
            """)
    int deleteEligibleForCleanup(Instant cutoff);
}
