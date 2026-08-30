package com.kologeski.interviewassistant.company;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyMembershipRepository extends JpaRepository<CompanyMembership, Long> {
    List<CompanyMembership> findByUserIdAndActiveTrueOrderByCreatedAtAsc(Long userId);
    Optional<CompanyMembership> findByCompanyIdAndUserIdAndActiveTrue(UUID companyId, Long userId);
    boolean existsByCompanyIdAndUserIdAndActiveTrue(UUID companyId, Long userId);
    List<CompanyMembership> findByCompanyIdAndActiveTrueOrderByCreatedAtAsc(UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select membership from CompanyMembership membership where membership.companyId = :companyId and membership.active = true")
    List<CompanyMembership> lockActiveByCompanyId(@Param("companyId") UUID companyId);
}
