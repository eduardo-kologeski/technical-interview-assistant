package com.kologeski.interviewassistant.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, Long id);

    @Query("""
            select u from User u
            where (:technology = '' or exists (
                select s.id from UserTechnicalSkill s
                where s.user = u and s.normalizedName = :technology
            ))
            and ((:date is null and :time is null) or exists (
                select a.id from InterviewAvailability a
                where a.user = u
                  and a.availabilityDate >= :today
                  and (:date is null or a.availabilityDate = :date)
                  and (:time is null or (a.startTime <= :time and a.endTime > :time))
            ))
            """)
    Page<User> searchInterviewers(@Param("technology") String technology,
                                  @Param("today") LocalDate today,
                                  @Param("date") LocalDate date,
                                  @Param("time") LocalTime time,
                                  Pageable pageable);
}
