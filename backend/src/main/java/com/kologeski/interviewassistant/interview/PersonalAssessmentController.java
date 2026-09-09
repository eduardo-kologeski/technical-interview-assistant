package com.kologeski.interviewassistant.interview;

import com.kologeski.interviewassistant.api.PageResponse;
import com.kologeski.interviewassistant.report.PersonalReportService;
import com.kologeski.interviewassistant.report.ReportDraft;
import com.kologeski.interviewassistant.report.AssessmentStudyService;
import com.kologeski.interviewassistant.report.ManualReport;
import com.kologeski.interviewassistant.report.AcademicReportEvaluation;
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
    private final AssessmentStudyService studies;

    public PersonalAssessmentController(PersonalAssessmentService service, PersonalReportService reports, AssessmentStudyService studies) {
        this.service = service;
        this.reports = reports;
        this.studies = studies;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AssessmentResponse create(@Valid @RequestBody CreateAssessmentCommand command) {
        return AssessmentResponse.from(service.create(command.title(), command.personName(), command.context()));
    }

    @PostMapping("/{id}/copy-requirements")
    @ResponseStatus(HttpStatus.CREATED)
    AssessmentResponse copyRequirements(@PathVariable UUID id, @Valid @RequestBody CreateAssessmentCommand command) {
        return AssessmentResponse.from(service.copyRequirements(id, command.title(), command.personName(), command.context()));
    }

    @GetMapping
    PageResponse<AssessmentResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(service.list(PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "updatedAt", "id"))), AssessmentResponse::from);
    }

    @GetMapping("/{id}")
    AssessmentResponse get(@PathVariable UUID id) { return AssessmentResponse.from(service.get(id)); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) { service.delete(id); }

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

    @GetMapping("/{id}/report")
    ReportResponse report(@PathVariable UUID id) { return ReportResponse.from(reports.get(id)); }

    @PutMapping("/{id}/report")
    ReportResponse saveReport(@PathVariable UUID id, @Valid @RequestBody SaveReportCommand command) {
        return ReportResponse.from(reports.saveReview(id, command.version(), command.draft(),
                command.humanConclusion(), command.reviewConfirmed(), command.reviewActiveMillis()));
    }

    @PostMapping("/{id}/report/approve")
    ReportResponse approveReport(@PathVariable UUID id,
                                 @RequestHeader("Idempotency-Key") UUID idempotencyKey,
                                 @Valid @RequestBody ApproveReportCommand command) {
        return ReportResponse.from(reports.approve(id, command.version(), idempotencyKey, command.reviewActiveMillis()));
    }

    @GetMapping("/{id}/study")
    StudyResponse study(@PathVariable UUID id) { return StudyResponse.from(studies.get(id)); }

    @PutMapping("/{id}/study/manual")
    StudyResponse saveManual(@PathVariable UUID id, @Valid @RequestBody SaveManualCommand command) {
        return StudyResponse.from(studies.save(id, command.assessmentVersion(), command.version(), command.manualReport(), command.manualActiveMillis()));
    }

    @PostMapping("/{id}/study/manual/finalize")
    StudyResponse finalizeManual(@PathVariable UUID id, @Valid @RequestBody FinalizeManualCommand command) {
        return StudyResponse.from(studies.finalizeManual(id, command.version(), command.manualActiveMillis()));
    }

    @PutMapping("/{id}/study/evaluation")
    StudyResponse saveEvaluation(@PathVariable UUID id, @Valid @RequestBody SaveEvaluationCommand command) {
        return StudyResponse.from(studies.saveEvaluation(id, command.version(), command.evaluation()));
    }

    @PostMapping("/{id}/study/evaluation/finalize")
    StudyResponse finalizeEvaluation(@PathVariable UUID id, @Valid @RequestBody VersionCommand command) {
        return StudyResponse.from(studies.finalizeEvaluation(id, command.version()));
    }

    public record CreateAssessmentCommand(@NotBlank @Size(max = 160) String title,
                                           @NotBlank @Size(max = 160) String personName,
                                           @Size(max = 5000) String context) { }

    public record VersionCommand(@NotNull @PositiveOrZero Long version) { }

    public record SaveCollectionCommand(@NotNull @PositiveOrZero Long version,
                                        @NotNull @Valid PersonalAssessmentDocument document) { }

    public record SaveReportCommand(@NotNull @PositiveOrZero Long version, @NotNull ReportDraft draft,
                                    @Size(max = 8000) String humanConclusion,
                                    boolean reviewConfirmed, @PositiveOrZero @Max(604800000) Long reviewActiveMillis) { }
    public record ApproveReportCommand(@NotNull @PositiveOrZero Long version,
                                       @PositiveOrZero @Max(604800000) Long reviewActiveMillis) { }
    public record SaveManualCommand(@NotNull @PositiveOrZero Long assessmentVersion,
                                    @NotNull @PositiveOrZero Long version, @NotNull ManualReport manualReport,
                                    @PositiveOrZero @Max(604800000) Long manualActiveMillis) { }
    public record FinalizeManualCommand(@NotNull @PositiveOrZero Long version,
                                        @PositiveOrZero @Max(604800000) Long manualActiveMillis) { }
    public record SaveEvaluationCommand(@NotNull @PositiveOrZero Long version,
                                        @NotNull AcademicReportEvaluation evaluation) { }

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

    public record ReportResponse(UUID id, UUID assessmentId, String status, long sourceVersion, long version,
                                 String promptVersion, String model, ReportDraft originalDraft, ReportDraft draft,
                                 String humanConclusion, boolean reviewConfirmed, Instant createdAt,
                                 Instant updatedAt, Instant approvedAt, Long generationLatencyMillis,
                                 long reviewActiveMillis, boolean replayed) {
        static ReportResponse from(PersonalReportService.ReportView value) {
            return new ReportResponse(value.id(), value.assessmentId(), value.status().name(),
                    value.sourceVersion(), value.version(), value.promptVersion(), value.model(),
                    value.originalDraft(), value.draft(), value.humanConclusion(), value.reviewConfirmed(),
                    value.createdAt(), value.updatedAt(), value.approvedAt(), value.generationLatencyMillis(),
                    value.reviewActiveMillis(), value.replayed());
        }
    }
    public record StudyResponse(UUID id,UUID assessmentId,long sourceVersion,long version,ManualReport manualReport,
                                boolean finalized,String contextHash,Instant createdAt,Instant updatedAt,Instant finalizedAt,String finalizedBy,
                                AcademicReportEvaluation evaluation,boolean evaluationFinalized,Instant evaluationFinalizedAt,String evaluationFinalizedBy,String timingVersion,long manualActiveMillis){
        static StudyResponse from(AssessmentStudyService.View value){return new StudyResponse(value.id(),value.assessmentId(),value.sourceVersion(),value.version(),value.manualReport(),value.finalized(),value.contextHash(),value.createdAt(),value.updatedAt(),value.finalizedAt(),value.finalizedBy(),value.evaluation(),value.evaluationFinalized(),value.evaluationFinalizedAt(),value.evaluationFinalizedBy(),value.timingVersion(),value.manualActiveMillis());}
    }
}
