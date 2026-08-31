package com.kologeski.interviewassistant.deployment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/setup")
public class InstallationSetupController {
    private final InstallationSetupService setup;
    public InstallationSetupController(InstallationSetupService setup) { this.setup = setup; }

    @GetMapping
    SetupStatus status() { return new SetupStatus(setup.setupRequired()); }

    @PostMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    void configure(@Valid @RequestBody SetupRequest request) {
        setup.setup(new InstallationSetupService.SetupCommand(request.organizationName(), request.adminName(),
                request.adminEmail(), request.password()));
    }

    record SetupStatus(boolean setupRequired) { }
    record SetupRequest(@NotBlank @Size(max = 160) String organizationName,
                        @NotBlank @Size(max = 120) String adminName,
                        @NotBlank @Email @Size(max = 180) String adminEmail,
                        @NotBlank @Size(min = 8, max = 72) String password) { }
}
