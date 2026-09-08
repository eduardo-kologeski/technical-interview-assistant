package com.kologeski.interviewassistant.interview;

import com.kologeski.interviewassistant.api.PageResponse;
import com.kologeski.interviewassistant.report.PersonalReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/assessments")
@Validated
public class PersonalAssessmentController {
    private final PersonalAssessmentService service;
    private final PersonalReportService reports;

    public PersonalAssessmentController(PersonalAssessmentService service, PersonalReportService reports) {
        this.service = service;
        this.reports = reports;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AssessmentResponse create(@Valid @RequestBody CreateAssessmentCommand command) {
        return AssessmentResponse.from(service.create(command.title(), command.personName(), command.context()));
    }

    @GetMapping
    PageResponse<AssessmentResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(service.list(PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "updatedAt", "id"))), AssessmentResponse::from);
    }

    @GetMapping("/{id}")
    AssessmentResponse get(@PathVariable UUID id) { return AssessmentResponse.from(service.get(id)); }

    @GetMapping("/{id}/collection")
    CollectionResponse collection(@PathVariable UUID id) {
        return CollectionResponse.from(service.collection(id));
    }

    @PatchMapping("/{id}/collection/start")
    CollectionResponse startCollection(@PathVariable UUID id, @Valid @RequestBody VersionCommand command) {
        return CollectionResponse.from(service.startCollection(id, command.version()));
    }

    @PutMapping("/{id}/collection")
    CollectionResponse saveCollection(@PathVariable UUID id, @Valid @RequestBody SaveCollectionCommand command) {
        return CollectionResponse.from(service.saveCollection(id, command.version(), command.document()));
    }

    @PatchMapping("/{id}/collection/complete")
    CollectionResponse completeCollection(@PathVariable UUID id, @Valid @RequestBody VersionCommand command) {
        return CollectionResponse.from(service.completeCollection(id, command.version()));
    }

    @PostMapping("/{id}/report/generate")
    ResponseEntity<ReportResponse> generateReport(
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody VersionCommand command) {
        PersonalReportService.ReportView report = reports.generate(id, command.version(), idempotencyKey);
        return ResponseEntity.status(report.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ReportResponse.from(report));
    }

    public record CreateAssessmentCommand(@NotBlank @Size(max = 160) String title,
                                           @NotBlank @Size(max = 160) String personName,
                                           @Size(max = 5000) String context) { }

    public record VersionCommand(@NotNull @PositiveOrZero Long version) { }

    public record SaveCollectionCommand(@NotNull @PositiveOrZero Long version,
                                        @NotNull @Valid PersonalAssessmentDocument document) { }

    public record AssessmentResponse(UUID id, String title, String personName, String context,
                                     InterviewStatus status, long version, Instant createdAt, Instant updatedAt) {
        static AssessmentResponse from(Interview value) {
            return new AssessmentResponse(value.id(), value.title(), value.candidateName(), value.context(),
                    value.status(), value.version(), value.createdAt(), value.updatedAt());
        }
    }

    public record CollectionResponse(UUID id, InterviewStatus status, long version,
                                     PersonalAssessmentDocument document, boolean generationEligible,
                                     Instant updatedAt) {
        static CollectionResponse from(PersonalAssessmentService.CollectionView value) {
            Interview assessment = value.assessment();
            return new CollectionResponse(assessment.id(), assessment.status(), assessment.version(),
                    value.document(), value.generationEligible(), assessment.updatedAt());
        }
    }

    public record ReportResponse(UUID id, UUID assessmentId, String status, long sourceVersion,
                                 String promptVersion, String model,
                                 com.kologeski.interviewassistant.report.ReportDraft draft,
                                 Instant createdAt, boolean replayed) {
        static ReportResponse from(PersonalReportService.ReportView value) {
            return new ReportResponse(value.id(), value.assessmentId(), value.status().name(),
                    value.sourceVersion(), value.promptVersion(), value.model(), value.draft(),
                    value.createdAt(), value.replayed());
        }
    }
}
