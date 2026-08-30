package com.kologeski.interviewassistant.interview;

import com.kologeski.interviewassistant.api.ResourceNotFoundException;
import com.kologeski.interviewassistant.audit.CurrentActor;
import com.kologeski.interviewassistant.company.*;
import com.kologeski.interviewassistant.user.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class InterviewCollaborationService {
    private final InterviewRepository interviews;
    private final InterviewParticipantRepository participants;
    private final CompanyMembershipRepository memberships;
    private final UserRepository users;
    private final CompanyContext companyContext;
    private final CurrentActor actor;
    private final Clock clock;
    private final CompanyAuthorizationPolicy authorization;

    public InterviewCollaborationService(InterviewRepository interviews, InterviewParticipantRepository participants,
            CompanyMembershipRepository memberships, UserRepository users, CompanyContext companyContext,
            CurrentActor actor, Clock clock, CompanyAuthorizationPolicy authorization) {
        this.interviews = interviews; this.participants = participants; this.memberships = memberships;
        this.users = users; this.companyContext = companyContext; this.actor = actor; this.clock = clock;
        this.authorization = authorization;
    }

    @Transactional
    public void register(Interview interview, Collection<Long> invitedUserIds) {
        var active = authorization.require(CompanyCapability.MANAGE_INTERVIEWS);
        LinkedHashSet<Long> ids = new LinkedHashSet<>(); ids.add(active.userId());
        if (invitedUserIds != null) ids.addAll(invitedUserIds);
        if (ids.size() > 10) throw new IllegalArgumentException("Uma entrevista pode ter no máximo 10 entrevistadores.");
        Instant now = Instant.now(clock);
        for (Long userId : ids) {
            if (!memberships.existsByCompanyIdAndUserIdAndActiveTrue(active.companyId(), userId))
                throw new CompanyContext.CompanyAccessDeniedException();
            participants.save(new InterviewParticipant(interview.id(), userId, now));
        }
    }

    @Transactional(readOnly = true)
    public Workspace workspace(UUID interviewId) {
        authorization.require(CompanyCapability.CONDUCT_ASSIGNED_INTERVIEW);
        Interview interview = requiredInterview(interviewId);
        InterviewParticipant own = requiredParticipant(interviewId);
        return view(interview, own);
    }

    @Transactional
    public Workspace save(UUID interviewId, long expectedVersion, String assessmentData) {
        authorization.require(CompanyCapability.CONDUCT_ASSIGNED_INTERVIEW);
        Interview interview = requiredInterview(interviewId);
        InterviewParticipant own = requiredParticipant(interviewId);
        if (own.version() != expectedVersion) throw new InterviewService.StaleInterviewException();
        own.save(assessmentData, Instant.now(clock));
        return view(interview, participants.saveAndFlush(own));
    }

    @Transactional
    public Workspace complete(UUID interviewId) {
        authorization.require(CompanyCapability.CONDUCT_ASSIGNED_INTERVIEW);
        Interview interview = requiredInterview(interviewId);
        InterviewParticipant own = requiredParticipant(interviewId);
        own.complete(Instant.now(clock)); participants.saveAndFlush(own);
        if (!participants.existsByInterviewIdAndStatusNot(interviewId, InterviewStatus.COMPLETED)
                && interview.status() == InterviewStatus.IN_PROGRESS) {
            interview.complete(actor.required().id(), Instant.now(clock)); interviews.save(interview);
        }
        return view(interview, own);
    }

    private Interview requiredInterview(UUID id) {
        return interviews.findByIdAndCompanyId(id, companyContext.required().companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Entrevista não encontrada."));
    }
    private InterviewParticipant requiredParticipant(UUID id) {
        Long userId = Long.valueOf(actor.required().id());
        return participants.findByInterviewIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Você não participa desta entrevista."));
    }
    private Workspace view(Interview interview, InterviewParticipant own) {
        List<ParticipantSummary> team = participants.findByInterviewIdOrderByIdAsc(interview.id()).stream().map(item -> {
            User user = users.findById(item.userId()).orElseThrow();
            return new ParticipantSummary(user.getId(), user.getName(), item.status());
        }).toList();
        return new Workspace(interview, own.assessmentData(), own.version(), own.status(), own.completedAt(), team);
    }

    public record ParticipantSummary(Long id, String name, InterviewStatus status) { }
    public record Workspace(Interview interview, String assessmentData, long version, InterviewStatus status,
                            Instant completedAt, List<ParticipantSummary> participants) { }
}
