package com.kologeski.interviewassistant.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface IdentityTokenRepository extends JpaRepository<IdentityToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from IdentityToken token where token.tokenHash = :hash and token.purpose = :purpose")
    Optional<IdentityToken> lockByHashAndPurpose(@Param("hash") String hash,
                                                  @Param("purpose") IdentityToken.Purpose purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from IdentityToken token where token.user.id = :userId and token.purpose = :purpose and token.usedAt is null")
    List<IdentityToken> lockActiveByUserAndPurpose(@Param("userId") Long userId,
                                                    @Param("purpose") IdentityToken.Purpose purpose);
    long deleteByUserId(Long userId);
}
