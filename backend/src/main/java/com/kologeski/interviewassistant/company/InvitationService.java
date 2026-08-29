package com.kologeski.interviewassistant.company;

import com.kologeski.interviewassistant.audit.CurrentActor;
import com.kologeski.interviewassistant.user.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class InvitationService {
    private final CompanyInvitationRepository invitations;
    private final CompanyMembershipRepository memberships;
    private final UserRepository users;
    private final CompanyContext context;
    private final CurrentActor actor;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public InvitationService(CompanyInvitationRepository invitations, CompanyMembershipRepository memberships,
                             UserRepository users, CompanyContext context, CurrentActor actor, Clock clock) {
        this.invitations = invitations; this.memberships = memberships; this.users = users;
        this.context = context; this.actor = actor; this.clock = clock;
    }

    @Transactional
    public CreatedInvitation invite(String email, CompanyRole role) {
        CompanyContext.ActiveCompany active = context.requiredAdmin();
        String normalizedEmail = normalize(email);
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now(clock);
        CompanyInvitation invitation = invitations.save(new CompanyInvitation(active.companyId(), normalizedEmail,
                role == null ? CompanyRole.INTERVIEWER : role, hash(token), active.userId(), now, now.plus(Duration.ofDays(7))));
        return new CreatedInvitation(invitation.id(), token, invitation.email(), invitation.role(), invitation.expiresAt());
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
        CompanyInvitation invitation = invitations.findByTokenHash(hash(token))
                .orElseThrow(InvitationUnavailableException::new);
        if (!normalize(user.getEmail()).equals(invitation.email())) throw new InvitationEmailMismatchException();
        Instant now = Instant.now(clock);
        invitation.accept(now);
        if (!memberships.existsByCompanyIdAndUserIdAndActiveTrue(invitation.companyId(), userId))
            memberships.save(new CompanyMembership(invitation.companyId(), userId, invitation.role(), now));
        invitations.save(invitation);
        return invitation.companyId();
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

    public record CreatedInvitation(UUID id, String token, String email, CompanyRole role, Instant expiresAt) { }
    public record InvitationDetails(String email, CompanyRole role, Instant expiresAt) { }
    public static final class InvitationUnavailableException extends RuntimeException {
        public InvitationUnavailableException() { super("Convite inválido ou expirado."); }
    }
    public static final class InvitationEmailMismatchException extends RuntimeException {
        public InvitationEmailMismatchException() { super("Entre com o mesmo e-mail que recebeu o convite."); }
    }
}
