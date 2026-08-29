package com.kologeski.interviewassistant.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

interface InterviewAvailabilityRepository extends JpaRepository<InterviewAvailability, Long> {
    List<InterviewAvailability> findByUserIdOrderByAvailabilityDateAscStartTimeAsc(Long userId);
    List<InterviewAvailability> findByUserIdAndAvailabilityDateBetweenOrderByAvailabilityDateAscStartTimeAsc(
            Long userId, java.time.LocalDate from, java.time.LocalDate to);
    List<InterviewAvailability> findByUserIdInAndAvailabilityDateGreaterThanEqualOrderByUserIdAscAvailabilityDateAscStartTimeAsc(
            Collection<Long> userIds, java.time.LocalDate from);
    Optional<InterviewAvailability> findByIdAndUserId(Long id, Long userId);
}
