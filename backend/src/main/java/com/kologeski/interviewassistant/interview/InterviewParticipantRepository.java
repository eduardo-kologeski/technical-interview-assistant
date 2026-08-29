package com.kologeski.interviewassistant.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface InterviewParticipantRepository extends JpaRepository<InterviewParticipant, Long> {
    Optional<InterviewParticipant> findByInterviewIdAndUserId(UUID interviewId, Long userId);
    List<InterviewParticipant> findByInterviewIdOrderByIdAsc(UUID interviewId);
    boolean existsByInterviewIdAndStatusNot(UUID interviewId, InterviewStatus status);
}
