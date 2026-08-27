package com.kologeski.interviewassistant.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

interface InterviewAvailabilityRepository extends JpaRepository<InterviewAvailability, Long> {
    List<InterviewAvailability> findByUserIdOrderByDayOfWeekAscStartTimeAsc(Long userId);
    List<InterviewAvailability> findAllByOrderByUserIdAscDayOfWeekAscStartTimeAsc();
    Optional<InterviewAvailability> findByIdAndUserId(Long id, Long userId);
}
