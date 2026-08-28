package com.kologeski.interviewassistant.vacancy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JobVacancyRepository extends JpaRepository<JobVacancy, UUID> { }
