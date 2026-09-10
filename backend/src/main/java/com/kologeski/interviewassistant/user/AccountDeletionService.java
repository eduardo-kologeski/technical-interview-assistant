package com.kologeski.interviewassistant.user;

import com.kologeski.interviewassistant.audit.CurrentActor;
import com.kologeski.interviewassistant.auth.IdentityTokenRepository;
import com.kologeski.interviewassistant.auth.RefreshTokenRepository;
import com.kologeski.interviewassistant.company.CompanyInvitationRepository;
import com.kologeski.interviewassistant.company.CompanyMembershipRepository;
import com.kologeski.interviewassistant.interview.InterviewRepository;
import com.kologeski.interviewassistant.profile.ProfileDeletionService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class AccountDeletionService {
    private final UserRepository users;
    private final InterviewRepository assessments;
    private final RefreshTokenRepository refreshTokens;
    private final IdentityTokenRepository identityTokens;
    private final CompanyMembershipRepository memberships;
    private final CompanyInvitationRepository invitations;
    private final ProfileDeletionService profiles;
    private final PasswordEncoder passwords;
    private final CurrentActor actor;
    private final Clock clock;

    public AccountDeletionService(UserRepository users, InterviewRepository assessments,
                                  RefreshTokenRepository refreshTokens, IdentityTokenRepository identityTokens,
                                  CompanyMembershipRepository memberships, CompanyInvitationRepository invitations,
                                  ProfileDeletionService profiles, PasswordEncoder passwords,
                                  CurrentActor actor, Clock clock) {
        this.users = users; this.assessments = assessments; this.refreshTokens = refreshTokens;
        this.identityTokens = identityTokens; this.memberships = memberships; this.invitations = invitations;
        this.profiles = profiles; this.passwords = passwords; this.actor = actor; this.clock = clock;
    }

    @Transactional
    public void delete(String password) {
        Long userId = Long.valueOf(actor.required().id());
        User user = users.findById(userId).orElseThrow();
        if (user.isDeleted() || !passwords.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Senha atual inválida.");
        }
        Instant now = Instant.now(clock);
        String email = user.getEmail();
        assessments.deleteAll(assessments.findByOwnerUserId(userId));
        assessments.flush();
        profiles.deleteForUser(userId);
        refreshTokens.deleteByUserId(userId);
        identityTokens.deleteByUserId(userId);
        memberships.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .filter(membership -> membership.active()).forEach(membership -> membership.deactivate(now));
        invitations.findByEmailIgnoreCase(email).forEach(invitation -> invitation.redactRecipient(now));
        user.anonymize(now);
        users.saveAndFlush(user);
    }
}
