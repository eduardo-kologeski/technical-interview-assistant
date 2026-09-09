package com.kologeski.interviewassistant.interview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface InterviewRepository extends JpaRepository<Interview, UUID> {
    java.util.Optional<Interview> findByIdAndOwnerUserId(UUID id, Long ownerUserId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select interview from Interview interview where interview.id = :id and interview.ownerUserId = :ownerUserId")
    java.util.Optional<Interview> findPersonalForUpdate(@Param("id") UUID id, @Param("ownerUserId") Long ownerUserId);
    Page<Interview> findByOwnerUserId(Long ownerUserId, Pageable pageable);
    java.util.List<Interview> findByOwnerUserId(Long ownerUserId);
    java.util.Optional<Interview> findByIdAndCompanyId(UUID id, UUID companyId);
    Page<Interview> findByCompanyId(UUID companyId, Pageable pageable);
    Page<Interview> findByCompanyIdAndStatus(UUID companyId, InterviewStatus status, Pageable pageable);
}
