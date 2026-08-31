package com.kologeski.interviewassistant.candidate;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
interface CandidateTimelineRepository extends JpaRepository<CandidateTimelineEvent,UUID>{List<CandidateTimelineEvent> findByCompanyIdAndCandidateIdOrderByOccurredAtDesc(UUID company,UUID candidate);}
