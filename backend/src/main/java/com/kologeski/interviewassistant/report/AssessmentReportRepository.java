package com.kologeski.interviewassistant.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface AssessmentReportRepository extends JpaRepository<AssessmentReport, UUID> {
    Optional<AssessmentReport> findByAssessmentIdAndOwnerUserId(UUID assessmentId, Long ownerUserId);
    Optional<AssessmentReport> findByIdempotencyKeyAndOwnerUserId(UUID idempotencyKey, Long ownerUserId);
    Optional<AssessmentReport> findByApprovalIdempotencyKeyAndOwnerUserId(UUID idempotencyKey, Long ownerUserId);
    List<AssessmentReport> findByOwnerUserId(Long ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select report from AssessmentReport report where report.id = :id and report.ownerUserId = :owner")
    Optional<AssessmentReport> findOwnedForUpdate(@Param("id") UUID id, @Param("owner") Long ownerUserId);
}
