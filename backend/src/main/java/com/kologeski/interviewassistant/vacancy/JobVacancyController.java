package com.kologeski.interviewassistant.vacancy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

@RestController
@RequestMapping({"/api/job-positions", "/api/vacancies"})
@Validated
public class JobVacancyController {
    private final JobVacancyService service;

    public JobVacancyController(JobVacancyService service) { this.service = service; }

    @PostMapping
    VacancyResponse create(@Valid @RequestBody VacancyCommand command) {
        return VacancyResponse.from(service.create(command.title(), command.seniority(), command.description(), command.requirements()));
    }

    @GetMapping PageResponse<VacancyResponse> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "") @Size(max = 120) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return PageResponse.from(service.list(active, query, pageable), VacancyResponse::from);
    }
    @GetMapping("/{id}") VacancyResponse get(@PathVariable UUID id) { return VacancyResponse.from(service.get(id)); }

    @PutMapping("/{id}")
    VacancyResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateVacancyCommand command) {
        return VacancyResponse.from(service.update(id, command.version(), command.title(), command.seniority(), command.description(), command.requirements()));
    }

    @PatchMapping("/{id}/deactivate")
    VacancyResponse deactivate(@PathVariable UUID id, @Valid @RequestBody VersionCommand command) {
        return VacancyResponse.from(service.deactivate(id, command.version()));
    }

    public record VacancyCommand(@NotBlank String title, @NotBlank String seniority, String description,
                                 List<String> requirements) { }
    public record UpdateVacancyCommand(@NotBlank String title, @NotBlank String seniority,
                                       String description, List<String> requirements,
                                       @NotNull @PositiveOrZero Long version) { }
    public record VersionCommand(@NotNull @PositiveOrZero Long version) { }

    public record VacancyResponse(UUID id, String title, String seniority, String description,
                                  List<String> requirements, boolean active,
                                  Instant createdAt, String createdBy, Instant updatedAt, String updatedBy,
                                  long version) {
        static VacancyResponse from(JobVacancy vacancy) {
            return new VacancyResponse(vacancy.id(), vacancy.title(), vacancy.seniority(), vacancy.description(),
                    vacancy.requirements(), vacancy.active(),
                    vacancy.createdAt(), vacancy.createdBy(), vacancy.updatedAt(), vacancy.updatedBy(),
                    vacancy.version());
        }
    }
}
