package com.kologeski.interviewassistant.interview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InterviewRepository extends JpaRepository<Interview, UUID> {
    Page<Interview> findByStatus(InterviewStatus status, Pageable pageable);
}
