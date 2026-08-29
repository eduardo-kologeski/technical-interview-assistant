package com.kologeski.interviewassistant.vacancy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface JobVacancyRepository extends JpaRepository<JobVacancy, UUID> {
    @Query("""
            select vacancy from JobVacancy vacancy
            where (:active is null or vacancy.active = :active)
              and (:query = '' or lower(vacancy.title) like lower(concat('%', :query, '%'))
                   or exists (select requirement from vacancy.requirements requirement
                              where lower(requirement) like lower(concat('%', :query, '%'))))
            """)
    Page<JobVacancy> search(@Param("active") Boolean active, @Param("query") String query, Pageable pageable);
}
