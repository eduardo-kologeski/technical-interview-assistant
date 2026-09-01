package com.kologeski.interviewassistant.company;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyInvitationRepository extends JpaRepository<CompanyInvitation, UUID> {
    Optional<CompanyInvitation> findByTokenHash(String tokenHash);
    List<CompanyInvitation> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);
    org.springframework.data.domain.Page<CompanyInvitation> findByCompanyId(UUID companyId, org.springframework.data.domain.Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from CompanyInvitation invitation where invitation.tokenHash = :tokenHash")
    Optional<CompanyInvitation> lockByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from CompanyInvitation invitation where invitation.id = :id and invitation.companyId = :companyId")
    Optional<CompanyInvitation> lockByIdAndCompanyId(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from CompanyInvitation invitation where invitation.companyId = :companyId and lower(invitation.email) = lower(:email) and invitation.status = com.kologeski.interviewassistant.company.InvitationStatus.PENDING")
    List<CompanyInvitation> lockPendingByCompanyAndEmail(@Param("companyId") UUID companyId, @Param("email") String email);
}
