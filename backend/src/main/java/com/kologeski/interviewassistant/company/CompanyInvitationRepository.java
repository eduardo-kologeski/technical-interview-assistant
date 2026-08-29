package com.kologeski.interviewassistant.company;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CompanyInvitationRepository extends JpaRepository<CompanyInvitation, UUID> {
    Optional<CompanyInvitation> findByTokenHash(String tokenHash);
    List<CompanyInvitation> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);
}
