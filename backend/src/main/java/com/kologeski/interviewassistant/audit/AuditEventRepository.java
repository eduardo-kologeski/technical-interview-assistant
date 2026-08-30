package com.kologeski.interviewassistant.audit;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    Page<AuditEvent> findByCompanyId(UUID companyId, Pageable pageable);
    Page<AuditEvent> findByCompanyIdAndCategory(UUID companyId, String category, Pageable pageable);
}
