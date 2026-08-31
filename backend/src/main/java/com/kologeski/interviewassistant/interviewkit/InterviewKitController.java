package com.kologeski.interviewassistant.interviewkit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping({"/api/vacancies/{vacancyId}/interview-kit", "/api/job-positions/{vacancyId}/interview-kit"})
public class InterviewKitController {
    private final InterviewKitService service;
    public InterviewKitController(InterviewKitService service) { this.service = service; }
    @GetMapping InterviewKitService.KitView get(@PathVariable UUID vacancyId) { return service.get(vacancyId); }
    @PutMapping("/draft") InterviewKitService.VersionView saveDraft(@PathVariable UUID vacancyId, @Valid @RequestBody KitCommand command) {
        return service.saveDraft(vacancyId, command.name(), command.items().stream()
                .map(item -> new InterviewKitService.ItemInput(item.criterion(), item.question(), item.weight(), item.rubric())).toList());
    }
    @PostMapping("/versions/{versionId}/publish") InterviewKitService.VersionView publish(@PathVariable UUID vacancyId, @PathVariable UUID versionId) {
        return service.publish(vacancyId, versionId);
    }
    public record KitCommand(@NotBlank @Size(max=160) String name, @Size(min=1,max=100) List<@Valid ItemCommand> items) { }
    public record ItemCommand(@NotBlank @Size(max=160) String criterion, @NotBlank @Size(max=2000) String question,
                              @Min(1) @Max(10) int weight, @NotBlank @Size(max=5000) String rubric) { }
}
