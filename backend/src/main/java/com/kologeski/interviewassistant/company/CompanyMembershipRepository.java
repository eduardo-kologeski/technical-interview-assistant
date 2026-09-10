package com.kologeski.interviewassistant.company;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CompanyMembershipRepository extends JpaRepository<CompanyMembership, Long> {
    List<CompanyMembership> findByUserIdAndActiveTrueOrderByCreatedAtAsc(Long userId);
    List<CompanyMembership> findByUserIdOrderByCreatedAtAsc(Long userId);
    Optional<CompanyMembership> findByCompanyIdAndUserIdAndActiveTrue(UUID companyId, Long userId);
    boolean existsByCompanyIdAndUserIdAndActiveTrue(UUID companyId, Long userId);
    List<CompanyMembership> findByCompanyIdAndActiveTrueOrderByCreatedAtAsc(UUID companyId);
    @Query("""
            select membership.userId as id, user.name as name, user.email as email, membership.role as role
              from CompanyMembership membership
              join User user on user.id = membership.userId
             where membership.companyId = :companyId and membership.active = true
            """)
    Page<MemberView> findActiveMemberViews(@Param("companyId") UUID companyId, Pageable pageable);

    interface MemberView {
        Long getId();
        String getName();
        String getEmail();
        CompanyRole getRole();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select membership from CompanyMembership membership where membership.companyId = :companyId and membership.active = true")
    List<CompanyMembership> lockActiveByCompanyId(@Param("companyId") UUID companyId);
}
