package com.kologeski.interviewassistant.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from RefreshToken token
             where token.expiresAt < :cutoff
                or (token.revoked = true and token.revokedAt < :cutoff)
            """)
    int deleteEligibleForCleanup(Instant cutoff);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken token set token.revoked = true, token.revokedAt = :now where token.user.id = :userId and token.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") Instant now);
    long deleteByUserId(Long userId);
}
