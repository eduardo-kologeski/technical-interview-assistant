package com.kologeski.interviewassistant.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReportGenerationClaimRepository extends JpaRepository<ReportGenerationClaim, UUID> {
    Optional<ReportGenerationClaim> findByAssessmentIdAndOwnerUserId(UUID assessmentId, Long ownerUserId);
    void deleteByAssessmentIdAndOwnerUserIdAndIdempotencyKey(UUID assessmentId, Long ownerUserId, UUID idempotencyKey);
}
