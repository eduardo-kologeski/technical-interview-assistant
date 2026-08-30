package com.kologeski.interviewassistant.audit;

import com.kologeski.interviewassistant.api.PageResponse;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;

@RestController @RequestMapping("/api/audit-events") @Validated
public class AuditEventController {
    private final AuditTrailService audit;
    public AuditEventController(AuditTrailService audit) { this.audit = audit; }
    @GetMapping
    PageResponse<Response> list(@RequestParam(required = false) @Size(max = 40) String category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        return PageResponse.from(audit.list(category, pageable), Response::from);
    }
    public record Response(UUID id, String category, String eventType, String resourceType,
                           String resourceId, Long actorUserId, String correlationId, Instant occurredAt) {
        static Response from(AuditEvent event) { return new Response(event.id(), event.category(), event.eventType(),
                event.resourceType(), event.resourceId(), event.actorUserId(), event.correlationId(), event.occurredAt()); }
    }
}
