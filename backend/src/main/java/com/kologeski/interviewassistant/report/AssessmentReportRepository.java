package com.kologeski.interviewassistant.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssessmentReportRepository extends JpaRepository<AssessmentReport, UUID> {
    Optional<AssessmentReport> findByAssessmentIdAndOwnerUserId(UUID assessmentId, Long ownerUserId);
    Optional<AssessmentReport> findByIdempotencyKeyAndOwnerUserId(UUID idempotencyKey, Long ownerUserId);
    Optional<AssessmentReport> findByApprovalIdempotencyKeyAndOwnerUserId(UUID idempotencyKey, Long ownerUserId);
}
