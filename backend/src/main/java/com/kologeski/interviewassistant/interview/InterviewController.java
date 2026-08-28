package com.kologeski.interviewassistant.interview;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.kologeski.interviewassistant.api.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/interviews")
@Validated
public class InterviewController {
    private final InterviewService service;

    public InterviewController(InterviewService service) { this.service = service; }

    @PostMapping
    InterviewResponse create(@Valid @RequestBody CreateInterviewCommand command) {
        return InterviewResponse.from(service.createAndStart(command.jobPositionId(), command.candidateName(), command.clientCompany()));
    }

    @GetMapping("/{id}") InterviewResponse get(@PathVariable UUID id) { return InterviewResponse.from(service.get(id)); }

    @GetMapping
    PageResponse<InterviewResponse> list(
            @RequestParam(required = false) InterviewStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return PageResponse.from(service.list(status, pageable), InterviewResponse::from);
    }

    @PatchMapping("/{id}/start")
    InterviewResponse start(@PathVariable UUID id) { return InterviewResponse.from(service.start(id)); }

    @PatchMapping("/{id}/complete")
    InterviewResponse complete(@PathVariable UUID id) { return InterviewResponse.from(service.complete(id)); }

    @PatchMapping("/{id}/assessment/finalize")
    InterviewResponse finalizeAssessment(@PathVariable UUID id) {
        return InterviewResponse.from(service.finalizeAssessment(id));
    }

    @PatchMapping("/{id}/assessment")
    InterviewResponse saveAssessment(@PathVariable UUID id,
                                     @Valid @RequestBody SaveAssessmentCommand command) {
        return InterviewResponse.from(service.saveAssessment(id, command.version(), command.assessmentData()));
    }

    @PostMapping("/{id}/pdf-generation")
    InterviewResponse registerPdfGeneration(@PathVariable UUID id) {
        return InterviewResponse.from(service.registerPdfGeneration(id));
    }

    public record CreateInterviewCommand(@NotNull UUID jobPositionId, @NotBlank String candidateName,
                                         String clientCompany) { }
    public record SaveAssessmentCommand(@PositiveOrZero long version,
                                        @NotBlank @Size(max = 1_000_000) String assessmentData) { }

    public record InterviewResponse(UUID id, UUID vacancyId, String candidateName,
                                    String vacancyTitleSnapshot, String senioritySnapshot, String clientCompany,
                                    List<String> requirementSnapshot,
                                    String interviewerId, InterviewStatus status, Instant startedAt,
                                    Instant completedAt, Instant assessmentFinalizedAt,
                                    String assessmentFinalizedBy, Instant lastPdfGeneratedAt,
                                    String lastPdfGeneratedBy, Instant createdAt, String createdBy,
                                    Instant updatedAt, String updatedBy, long version,
                                    String assessmentData) {
        static InterviewResponse from(Interview interview) {
            return new InterviewResponse(interview.id(), interview.vacancyId(), interview.candidateName(),
                    interview.vacancyTitleSnapshot(), interview.senioritySnapshot(), interview.clientCompany(),
                    interview.requirementSnapshot(), interview.interviewerId(),
                    interview.status(), interview.startedAt(), interview.completedAt(),
                    interview.assessmentFinalizedAt(), interview.assessmentFinalizedBy(),
                    interview.lastPdfGeneratedAt(), interview.lastPdfGeneratedBy(), interview.createdAt(),
                    interview.createdBy(), interview.updatedAt(), interview.updatedBy(), interview.version(),
                    interview.assessmentData());
        }
    }
}
