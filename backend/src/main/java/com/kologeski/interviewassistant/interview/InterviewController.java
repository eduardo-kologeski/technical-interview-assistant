package com.kologeski.interviewassistant.interview;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {
    private final InterviewService service;

    public InterviewController(InterviewService service) { this.service = service; }

    @PostMapping
    InterviewResponse create(@Valid @RequestBody CreateInterviewCommand command) {
        return InterviewResponse.from(service.create(command.vacancyId(), command.candidateName()));
    }

    @PatchMapping("/{id}/start")
    InterviewResponse start(@PathVariable UUID id) { return InterviewResponse.from(service.start(id)); }

    @PatchMapping("/{id}/complete")
    InterviewResponse complete(@PathVariable UUID id) { return InterviewResponse.from(service.complete(id)); }

    @PatchMapping("/{id}/assessment/finalize")
    InterviewResponse finalizeAssessment(@PathVariable UUID id) {
        return InterviewResponse.from(service.finalizeAssessment(id));
    }

    @PostMapping("/{id}/pdf-generation")
    InterviewResponse registerPdfGeneration(@PathVariable UUID id) {
        return InterviewResponse.from(service.registerPdfGeneration(id));
    }

    public record CreateInterviewCommand(@NotNull UUID vacancyId, @NotBlank String candidateName) { }

    public record InterviewResponse(UUID id, UUID vacancyId, String candidateName,
                                    String vacancyTitleSnapshot, List<String> requirementSnapshot,
                                    String interviewerId, InterviewStatus status, Instant startedAt,
                                    Instant completedAt, Instant assessmentFinalizedAt,
                                    String assessmentFinalizedBy, Instant lastPdfGeneratedAt,
                                    String lastPdfGeneratedBy, Instant createdAt, String createdBy,
                                    Instant updatedAt, String updatedBy) {
        static InterviewResponse from(Interview interview) {
            return new InterviewResponse(interview.id(), interview.vacancyId(), interview.candidateName(),
                    interview.vacancyTitleSnapshot(), interview.requirementSnapshot(), interview.interviewerId(),
                    interview.status(), interview.startedAt(), interview.completedAt(),
                    interview.assessmentFinalizedAt(), interview.assessmentFinalizedBy(),
                    interview.lastPdfGeneratedAt(), interview.lastPdfGeneratedBy(), interview.createdAt(),
                    interview.createdBy(), interview.updatedAt(), interview.updatedBy());
        }
    }
}
