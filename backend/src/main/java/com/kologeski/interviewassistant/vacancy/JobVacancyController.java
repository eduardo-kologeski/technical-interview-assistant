package com.kologeski.interviewassistant.vacancy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/vacancies")
public class JobVacancyController {
    private final JobVacancyService service;

    public JobVacancyController(JobVacancyService service) { this.service = service; }

    @PostMapping
    VacancyResponse create(@Valid @RequestBody VacancyCommand command) {
        return VacancyResponse.from(service.create(command.title(), command.requirements()));
    }

    @PutMapping("/{id}")
    VacancyResponse update(@PathVariable UUID id, @Valid @RequestBody VacancyCommand command) {
        return VacancyResponse.from(service.update(id, command.title(), command.requirements()));
    }

    @PatchMapping("/{id}/deactivate")
    VacancyResponse deactivate(@PathVariable UUID id) {
        return VacancyResponse.from(service.deactivate(id));
    }

    public record VacancyCommand(@NotBlank String title, List<String> requirements) { }

    public record VacancyResponse(UUID id, String title, List<String> requirements, boolean active,
                                  Instant createdAt, String createdBy, Instant updatedAt, String updatedBy) {
        static VacancyResponse from(JobVacancy vacancy) {
            return new VacancyResponse(vacancy.id(), vacancy.title(), vacancy.requirements(), vacancy.active(),
                    vacancy.createdAt(), vacancy.createdBy(), vacancy.updatedAt(), vacancy.updatedBy());
        }
    }
}
