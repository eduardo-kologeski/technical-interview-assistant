package com.kologeski.interviewassistant.audit;

import com.kologeski.interviewassistant.company.*;
import com.kologeski.interviewassistant.observability.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class AuditTrailService {
    private final AuditEventRepository events;
    private final CompanyAuthorizationPolicy authorization;
    private final Clock clock;
    private final Counter persistenceFailures;
    public AuditTrailService(AuditEventRepository events, CompanyAuthorizationPolicy authorization, Clock clock,
                             MeterRegistry metrics) {
        this.events = events; this.authorization = authorization; this.clock = clock;
        this.persistenceFailures = Counter.builder("audit.persistence.failures").register(metrics);
    }
    public void record(UUID companyId, Long actorUserId, String category, String eventType,
                       String resourceType, Object resourceId) {
        try {
            events.save(new AuditEvent(companyId, category, eventType, resourceType,
                    resourceId == null ? null : resourceId.toString(), actorUserId,
                    MDC.get(CorrelationIdFilter.MDC_KEY), Instant.now(clock)));
        } catch (RuntimeException failure) {
            persistenceFailures.increment();
            throw failure;
        }
    }
    @Transactional(readOnly = true)
    public Page<AuditEvent> list(String category, Pageable pageable) {
        UUID companyId = authorization.require(CompanyCapability.VIEW_AUDIT_LOG).companyId();
        return category == null || category.isBlank() ? events.findByCompanyId(companyId, pageable)
                : events.findByCompanyIdAndCategory(companyId, category.trim().toUpperCase(), pageable);
    }
}
