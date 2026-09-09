package com.kologeski.interviewassistant.report;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface AssessmentStudyRepository extends JpaRepository<AssessmentStudy,UUID>{Optional<AssessmentStudy> findByAssessmentIdAndOwnerUserId(UUID assessmentId,Long ownerUserId);}
