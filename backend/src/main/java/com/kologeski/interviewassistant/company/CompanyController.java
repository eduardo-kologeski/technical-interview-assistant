package com.kologeski.interviewassistant.company;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {
    private final CompanyService companies;
    private final InvitationService invitations;
    private final CompanyContext context;

    public CompanyController(CompanyService companies, InvitationService invitations, CompanyContext context) {
        this.companies = companies; this.invitations = invitations; this.context = context;
    }

    @GetMapping public List<CompanyService.CompanySummary> mine() { return companies.mine(); }

    @GetMapping("/members")
    public List<CompanyService.CompanyMemberSummary> members() { return companies.members(context); }

    @DeleteMapping("/members/{userId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeMember(@PathVariable Long userId) { companies.revokeMember(context, userId); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public CompanyService.CompanySummary create(@RequestBody CreateCompanyRequest request) {
        Company company = companies.create(request.name());
        return new CompanyService.CompanySummary(company.id(), company.name(), company.slug(), CompanyRole.COMPANY_ADMIN);
    }

    @PostMapping("/invitations") @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse invite(@RequestBody InviteRequest request) {
        var invitation = invitations.invite(request.email(), request.role());
        return new InvitationResponse(invitation.id(), invitation.email(), invitation.role(), invitation.expiresAt(),
                invitation.deliveryStatus(), invitation.developmentPath());
    }

    @GetMapping("/invitations")
    public List<InvitationService.InvitationSummary> invitations() { return invitations.list(); }

    @DeleteMapping("/invitations/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeInvitation(@PathVariable UUID id) { invitations.revoke(id); }

    @PostMapping("/invitations/{id}/resend")
    public InvitationResponse resendInvitation(@PathVariable UUID id) {
        var invitation = invitations.resend(id);
        return new InvitationResponse(invitation.id(), invitation.email(), invitation.role(), invitation.expiresAt(),
                invitation.deliveryStatus(), invitation.developmentPath());
    }

    @PostMapping("/invitations/accept")
    public AcceptResponse accept(@RequestBody AcceptRequest request) {
        return new AcceptResponse(invitations.accept(request.token()));
    }

    public record CreateCompanyRequest(String name) { }
    public record InviteRequest(String email, CompanyRole role) { }
    public record AcceptRequest(String token) { }
    public record AcceptResponse(UUID companyId) { }
    public record InvitationResponse(UUID id, String email, CompanyRole role, java.time.Instant expiresAt,
                                     String deliveryStatus, String developmentPath) { }
}
