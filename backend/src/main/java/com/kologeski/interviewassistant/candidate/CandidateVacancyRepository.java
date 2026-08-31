package com.kologeski.interviewassistant.candidate;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
interface CandidateVacancyRepository extends JpaRepository<CandidateVacancyLink,UUID>{Optional<CandidateVacancyLink> findByCandidateIdAndVacancyId(UUID candidate,UUID vacancy);}
