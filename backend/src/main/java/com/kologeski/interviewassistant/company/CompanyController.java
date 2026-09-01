package com.kologeski.interviewassistant.company;

import com.kologeski.interviewassistant.api.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

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
    @GetMapping("/members/page") public PageResponse<CompanyService.CompanyMemberSummary> membersPage(@PageableDefault(size=20) Pageable pageable) {
        return PageResponse.from(companies.members(pageable), member -> member);
    }
    @PatchMapping("/members/{userId}/role") public CompanyService.CompanyMemberSummary changeRole(@PathVariable Long userId, @Valid @RequestBody RoleRequest request) { return companies.changeRole(userId, request.role()); }
    @PatchMapping("/current") public CompanyService.CompanySummary rename(@Valid @RequestBody RenameRequest request) { return companies.rename(request.name()); }

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
    @GetMapping("/invitations/page") public PageResponse<InvitationService.InvitationSummary> invitationsPage(@PageableDefault(size=20,sort="createdAt",direction=Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(invitations.list(pageable), invitation -> invitation);
    }

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
    public record RoleRequest(@NotNull CompanyRole role) { }
    public record RenameRequest(@NotBlank @Size(max = 160) String name) { }
    public record AcceptRequest(String token) { }
    public record AcceptResponse(UUID companyId) { }
    public record InvitationResponse(UUID id, String email, CompanyRole role, java.time.Instant expiresAt,
                                     String deliveryStatus, String developmentPath) { }
}
