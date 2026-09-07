package com.kologeski.interviewassistant.interview;

import com.kologeski.interviewassistant.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/assessments")
@Validated
public class PersonalAssessmentController {
    private final PersonalAssessmentService service;

    public PersonalAssessmentController(PersonalAssessmentService service) { this.service = service; }

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

    public record CreateAssessmentCommand(@NotBlank @Size(max = 160) String title,
                                           @NotBlank @Size(max = 160) String personName,
                                           @Size(max = 5000) String context) { }

    public record AssessmentResponse(UUID id, String title, String personName, String context,
                                     InterviewStatus status, long version, Instant createdAt, Instant updatedAt) {
        static AssessmentResponse from(Interview value) {
            return new AssessmentResponse(value.id(), value.title(), value.candidateName(), value.context(),
                    value.status(), value.version(), value.createdAt(), value.updatedAt());
        }
    }
}
