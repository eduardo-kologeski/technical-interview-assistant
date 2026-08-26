package com.kologeski.interviewassistant.security;

import com.kologeski.interviewassistant.audit.CurrentActor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class IdentityController {
    private final CurrentActor currentActor;

    public IdentityController(CurrentActor currentActor) { this.currentActor = currentActor; }

    @GetMapping
    AuthenticatedUserResponse currentUser() {
        var actor = currentActor.required();
        return new AuthenticatedUserResponse(actor.id(), actor.displayName());
    }

    public record AuthenticatedUserResponse(String id, String displayName) { }
}
