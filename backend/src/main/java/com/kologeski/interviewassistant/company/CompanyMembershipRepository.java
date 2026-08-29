package com.kologeski.interviewassistant.company;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CompanyMembershipRepository extends JpaRepository<CompanyMembership, Long> {
    List<CompanyMembership> findByUserIdAndActiveTrueOrderByCreatedAtAsc(Long userId);
    Optional<CompanyMembership> findByCompanyIdAndUserIdAndActiveTrue(UUID companyId, Long userId);
    boolean existsByCompanyIdAndUserIdAndActiveTrue(UUID companyId, Long userId);
}
