package com.kologeski.interviewassistant.company;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
    boolean existsBySlugIgnoreCase(String slug);
}
