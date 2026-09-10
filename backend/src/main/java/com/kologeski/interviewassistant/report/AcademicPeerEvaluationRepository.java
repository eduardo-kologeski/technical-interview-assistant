package com.kologeski.interviewassistant.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AcademicPeerEvaluationRepository extends JpaRepository<AcademicPeerEvaluation, UUID> {
    List<AcademicPeerEvaluation> findByStudyIdAndOwnerUserIdOrderByCreatedAtAsc(UUID studyId, Long ownerUserId);
    Optional<AcademicPeerEvaluation> findByIdAndAssessmentIdAndOwnerUserId(UUID id, UUID assessmentId, Long ownerUserId);
    boolean existsByStudyIdAndOwnerUserIdAndReviewerCodeIgnoreCase(UUID studyId, Long ownerUserId, String reviewerCode);
    List<AcademicPeerEvaluation> findByOwnerUserIdAndFinalizedAtIsNotNull(Long ownerUserId);
}
