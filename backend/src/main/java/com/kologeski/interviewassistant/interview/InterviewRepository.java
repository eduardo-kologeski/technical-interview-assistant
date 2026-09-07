package com.kologeski.interviewassistant.interview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InterviewRepository extends JpaRepository<Interview, UUID> {
    java.util.Optional<Interview> findByIdAndOwnerUserId(UUID id, Long ownerUserId);
    Page<Interview> findByOwnerUserId(Long ownerUserId, Pageable pageable);
    java.util.Optional<Interview> findByIdAndCompanyId(UUID id, UUID companyId);
    Page<Interview> findByCompanyId(UUID companyId, Pageable pageable);
    Page<Interview> findByCompanyIdAndStatus(UUID companyId, InterviewStatus status, Pageable pageable);
}
