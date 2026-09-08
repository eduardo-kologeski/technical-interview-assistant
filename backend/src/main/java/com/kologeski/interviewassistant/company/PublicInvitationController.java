package com.kologeski.interviewassistant.company;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/invitations")
public class PublicInvitationController {
    private final InvitationService invitations;
    public PublicInvitationController(InvitationService invitations) { this.invitations = invitations; }
    @GetMapping("/{token}") public InvitationService.InvitationDetails inspect(@PathVariable String token) {
        return invitations.inspect(token);
    }
}
