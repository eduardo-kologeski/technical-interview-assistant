package com.kologeski.interviewassistant.profile;

import com.kologeski.interviewassistant.user.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.List;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final ProfileService service;

    public ProfileController(ProfileService service) { this.service = service; }

    @GetMapping
    ProfileService.ProfileSnapshot get(@AuthenticationPrincipal UserPrincipal principal) {
        return service.profile(principal.user());
    }

    @PutMapping
    ProfileService.ProfileSnapshot update(@AuthenticationPrincipal UserPrincipal principal,
                                          @Valid @RequestBody ProfileCommand command) {
        return service.updateProfile(principal.user(), command.name(), command.email());
    }

    @GetMapping("/technical-skills")
    Object skills(@AuthenticationPrincipal UserPrincipal principal) {
        return service.profile(principal.user()).technicalSkills();
    }

    @PostMapping("/technical-skills") @ResponseStatus(HttpStatus.CREATED)
    ProfileService.SkillSnapshot addSkill(@AuthenticationPrincipal UserPrincipal principal,
                                          @Valid @RequestBody SkillCommand command) {
        return service.addSkill(principal.user(), command.name(), command.proficiencyLevel());
    }

    @PutMapping("/technical-skills/{id}")
    ProfileService.SkillSnapshot updateSkill(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable Long id, @Valid @RequestBody SkillCommand command) {
        return service.updateSkill(principal.user(), id, command.name(), command.proficiencyLevel());
    }

    @DeleteMapping("/technical-skills/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeSkill(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.removeSkill(principal.user(), id);
    }

    @GetMapping("/availability")
    Object availability(@AuthenticationPrincipal UserPrincipal principal,
                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.availability(principal.user(), from, to);
    }

    @PostMapping("/availability") @ResponseStatus(HttpStatus.CREATED)
    ProfileService.AvailabilitySnapshot addAvailability(@AuthenticationPrincipal UserPrincipal principal,
                                                        @Valid @RequestBody AvailabilityCommand command) {
        return service.addAvailability(principal.user(), command.date(), command.startTime(), command.endTime());
    }

    @PostMapping("/availability/batch") @ResponseStatus(HttpStatus.CREATED)
    List<ProfileService.AvailabilitySnapshot> addAvailabilityBatch(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody @Size(min = 1, max = 2) List<@Valid AvailabilityCommand> commands) {
        return service.addAvailabilityBatch(principal.user(), commands.stream()
                .map(command -> new ProfileService.AvailabilityPeriod(
                        command.date(), command.startTime(), command.endTime()))
                .toList());
    }

    @PutMapping("/availability/{id}")
    ProfileService.AvailabilitySnapshot updateAvailability(@AuthenticationPrincipal UserPrincipal principal,
                                                           @PathVariable Long id,
                                                           @Valid @RequestBody AvailabilityCommand command) {
        return service.updateAvailability(principal.user(), id, command.date(), command.startTime(), command.endTime());
    }

    @DeleteMapping("/availability/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeAvailability(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        service.removeAvailability(principal.user(), id);
    }

    public record ProfileCommand(@NotBlank @Size(max = 120) String name,
                                 @NotBlank @Email @Size(max = 180) String email) { }
    public record SkillCommand(@NotBlank @Size(max = 120) String name,
                               @NotNull ProficiencyLevel proficiencyLevel) { }
    public record AvailabilityCommand(@NotNull LocalDate date, @NotNull LocalTime startTime,
                                      @NotNull LocalTime endTime) { }
}
