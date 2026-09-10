package com.kologeski.interviewassistant.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssessmentReportGenerationRepository extends JpaRepository<AssessmentReportGeneration, UUID> {
    List<AssessmentReportGeneration> findByReportIdAndOwnerUserIdOrderBySequenceAsc(UUID reportId, Long ownerUserId);
    Optional<AssessmentReportGeneration> findByIdempotencyKeyAndOwnerUserId(UUID idempotencyKey, Long ownerUserId);
    Optional<AssessmentReportGeneration> findFirstByReportIdAndOwnerUserIdOrderBySequenceDesc(UUID reportId, Long ownerUserId);
}
