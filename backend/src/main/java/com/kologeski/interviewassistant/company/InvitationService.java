package com.kologeski.interviewassistant.company;

import com.kologeski.interviewassistant.audit.CurrentActor;
import com.kologeski.interviewassistant.audit.AuditTrailService;
import com.kologeski.interviewassistant.user.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class InvitationService {
    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);
    private final CompanyInvitationRepository invitations;
    private final CompanyMembershipRepository memberships;
    private final UserRepository users;
    private final CompanyContext context;
    private final CurrentActor actor;
    private final Clock clock;
    private final CompanyRepository companies;
    private final InvitationDelivery delivery;
    private final CompanyAuthorizationPolicy authorization;
    private final AuditTrailService audit;
    private final SecureRandom random = new SecureRandom();

    public InvitationService(CompanyInvitationRepository invitations, CompanyMembershipRepository memberships,
                             UserRepository users, CompanyContext context, CurrentActor actor, Clock clock,
                             CompanyRepository companies, InvitationDelivery delivery,
                             CompanyAuthorizationPolicy authorization, AuditTrailService audit) {
        this.invitations = invitations; this.memberships = memberships; this.users = users;
        this.context = context; this.actor = actor; this.clock = clock;
        this.companies = companies; this.delivery = delivery; this.authorization = authorization; this.audit = audit;
    }

    @Transactional
    public CreatedInvitation invite(String email, CompanyRole role) {
        CompanyContext.ActiveCompany active = authorization.require(CompanyCapability.MANAGE_INVITATIONS);
        String normalizedEmail = normalize(email);
        Instant now = Instant.now(clock);
        invitations.lockPendingByCompanyAndEmail(active.companyId(), normalizedEmail)
                .forEach(item -> { item.revoke(now); invitations.save(item); });
        return createAndDeliver(active, normalizedEmail, role == null ? CompanyRole.INTERVIEWER : role, now);
    }

    @Transactional(readOnly = true)
    public List<InvitationSummary> list() {
        CompanyContext.ActiveCompany active = authorization.require(CompanyCapability.MANAGE_INVITATIONS);
        Instant now = Instant.now(clock);
        return invitations.findByCompanyIdOrderByCreatedAtDesc(active.companyId()).stream()
                .map(item -> new InvitationSummary(item.id(), item.email(), item.role(),
                        item.status() == InvitationStatus.PENDING && !item.expiresAt().isAfter(now)
                                ? InvitationStatus.EXPIRED : item.status(), item.expiresAt(), item.createdAt()))
                .toList();
    }

    @Transactional
    public void revoke(UUID id) {
        CompanyContext.ActiveCompany active = authorization.require(CompanyCapability.MANAGE_INVITATIONS);
        CompanyInvitation invitation = invitations.lockByIdAndCompanyId(id, active.companyId())
                .orElseThrow(InvitationUnavailableException::new);
        invitation.revoke(Instant.now(clock));
        invitations.save(invitation);
        audit.record(active.companyId(), active.userId(), "SECURITY", "INVITATION_REVOKED", "INVITATION", id);
        log.info("event=company_invitation_revoked companyId={} invitationId={} actorId={}",
                active.companyId(), id, active.userId());
    }

    @Transactional
    public CreatedInvitation resend(UUID id) {
        CompanyContext.ActiveCompany active = authorization.require(CompanyCapability.MANAGE_INVITATIONS);
        CompanyInvitation previous = invitations.lockByIdAndCompanyId(id, active.companyId())
                .orElseThrow(InvitationUnavailableException::new);
        Instant now = Instant.now(clock);
        if (previous.status() == InvitationStatus.PENDING) previous.revoke(now);
        else if (previous.status() != InvitationStatus.EXPIRED) throw new InvitationUnavailableException();
        invitations.save(previous);
        return createAndDeliver(active, previous.email(), previous.role(), now);
    }

    @Transactional(readOnly = true)
    public InvitationDetails inspect(String token) {
        CompanyInvitation invitation = invitations.findByTokenHash(hash(token))
                .orElseThrow(InvitationUnavailableException::new);
        if (invitation.status() != InvitationStatus.PENDING || !invitation.expiresAt().isAfter(Instant.now(clock)))
            throw new InvitationUnavailableException();
        return new InvitationDetails(invitation.email(), invitation.role(), invitation.expiresAt());
    }

    @Transactional
    public UUID accept(String token) {
        Long userId = Long.valueOf(actor.required().id());
        User user = users.findById(userId).orElseThrow();
        if (!user.isEmailVerified()) throw new EmailVerificationRequiredException();
        CompanyInvitation invitation = invitations.lockByTokenHash(hash(token))
                .orElseThrow(InvitationUnavailableException::new);
        if (!normalize(user.getEmail()).equals(invitation.email())) throw new InvitationEmailMismatchException();
        Instant now = Instant.now(clock);
        invitation.accept(now);
        if (!memberships.existsByCompanyIdAndUserIdAndActiveTrue(invitation.companyId(), userId))
            memberships.save(new CompanyMembership(invitation.companyId(), userId, invitation.role(), now));
        invitations.save(invitation);
        return invitation.companyId();
    }

    private CreatedInvitation createAndDeliver(CompanyContext.ActiveCompany active, String email, CompanyRole role, Instant now) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        CompanyInvitation invitation = invitations.save(new CompanyInvitation(active.companyId(), email, role,
                hash(token), active.userId(), now, now.plus(Duration.ofDays(7))));
        audit.record(active.companyId(), active.userId(), "SECURITY", "INVITATION_CREATED", "INVITATION", invitation.id());
        String companyName = companies.findById(active.companyId()).orElseThrow().name();
        InvitationDelivery.DeliveryReceipt receipt = delivery.deliver(new InvitationDelivery.InvitationMessage(
                invitation.id(), invitation.email(), companyName, token, invitation.expiresAt()));
        log.info("event=company_invitation_created companyId={} invitationId={} actorId={} deliveryStatus={}",
                active.companyId(), invitation.id(), active.userId(), receipt.status());
        return new CreatedInvitation(invitation.id(), invitation.email(), invitation.role(), invitation.expiresAt(),
                receipt.status(), receipt.developmentPath());
    }

    private String normalize(String email) {
        if (email == null || !email.contains("@")) throw new IllegalArgumentException("valid email is required");
        return email.trim().toLowerCase(Locale.ROOT);
    }
    private String hash(String token) {
        if (token == null || token.isBlank()) throw new InvitationUnavailableException();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    public record CreatedInvitation(UUID id, String email, CompanyRole role, Instant expiresAt,
                                    String deliveryStatus, String developmentPath) { }
    public record InvitationSummary(UUID id, String email, CompanyRole role, InvitationStatus status,
                                    Instant expiresAt, Instant createdAt) { }
    public record InvitationDetails(String email, CompanyRole role, Instant expiresAt) { }
    public static final class InvitationUnavailableException extends RuntimeException {
        public InvitationUnavailableException() { super("Convite inválido ou expirado."); }
    }
    public static final class InvitationEmailMismatchException extends RuntimeException {
        public InvitationEmailMismatchException() { super("Entre com o mesmo e-mail que recebeu o convite."); }
    }
    public static final class EmailVerificationRequiredException extends RuntimeException {
        public EmailVerificationRequiredException() { super("Verifique seu e-mail antes de aceitar o convite."); }
    }
}
