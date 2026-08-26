package com.kologeski.interviewassistant.interview;

import com.kologeski.interviewassistant.audit.MutableActor;
import com.kologeski.interviewassistant.audit.MutableClock;
import com.kologeski.interviewassistant.vacancy.JobVacancy;
import com.kologeski.interviewassistant.vacancy.JobVacancyRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InterviewServiceAuditTest {
    @Test
    void createsStartedInterviewFromActiveJobPositionAndAuthenticatedUser() {
        var interviewRepository = mock(InterviewRepository.class);
        var vacancyRepository = mock(JobVacancyRepository.class);
        when(interviewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var vacancy = new JobVacancy("Backend Java", "Pleno", "", List.of("Java", "SQL"),
                "creator", Instant.EPOCH);
        when(vacancyRepository.findById(vacancy.id())).thenReturn(Optional.of(vacancy));
        var service = new InterviewService(interviewRepository, vacancyRepository,
                new MutableActor("authcore-user-42"), new MutableClock(Instant.parse("2026-08-26T12:00:00Z")));

        var interview = service.createAndStart(vacancy.id(), "João", "Empresa X");

        assertThat(interview.status()).isEqualTo(InterviewStatus.IN_PROGRESS);
        assertThat(interview.interviewerId()).isEqualTo("authcore-user-42");
        assertThat(interview.createdBy()).isEqualTo("authcore-user-42");
        assertThat(interview.senioritySnapshot()).isEqualTo("Pleno");
        assertThat(interview.requirementSnapshot()).containsExactly("Java", "SQL");
        assertThat(interview.clientCompany()).isEqualTo("Empresa X");
    }

    @Test
    void rejectsMissingOrInactiveJobPosition() {
        var interviewRepository = mock(InterviewRepository.class);
        var vacancyRepository = mock(JobVacancyRepository.class);
        var actor = new MutableActor("user");
        var clock = new MutableClock(Instant.EPOCH);
        var service = new InterviewService(interviewRepository, vacancyRepository, actor, clock);

        assertThatThrownBy(() -> service.createAndStart(java.util.UUID.randomUUID(), "Candidato", ""))
                .isInstanceOf(IllegalArgumentException.class);

        var inactive = new JobVacancy("QA", List.of("Cypress"), "creator", Instant.EPOCH);
        inactive.deactivate("creator", Instant.EPOCH.plusSeconds(1));
        when(vacancyRepository.findById(inactive.id())).thenReturn(Optional.of(inactive));
        assertThatThrownBy(() -> service.createAndStart(inactive.id(), "Candidato", ""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void capturesRequirementSnapshotAndAuditsInterviewLifecycle() {
        var interviewRepository = mock(InterviewRepository.class);
        var vacancyRepository = mock(JobVacancyRepository.class);
        when(interviewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var vacancy = new JobVacancy("Java Pleno", List.of("Java", "Spring Boot", "SQL"),
                "user-a", Instant.parse("2026-08-25T12:00:00Z"));
        when(vacancyRepository.findById(vacancy.id())).thenReturn(Optional.of(vacancy));
        var actor = new MutableActor("user-c");
        var createdAt = Instant.parse("2026-08-26T16:50:00Z");
        var clock = new MutableClock(createdAt);
        var service = new InterviewService(interviewRepository, vacancyRepository, actor, clock);

        var interview = service.create(vacancy.id(), "João Silva");
        when(interviewRepository.findById(interview.id())).thenReturn(Optional.of(interview));
        vacancy.update("Java Pleno", List.of("Java", "Kubernetes"), "user-b",
                Instant.parse("2026-08-26T16:55:00Z"));

        assertThat(interview.createdBy()).isEqualTo("user-c");
        assertThat(interview.createdAt()).isEqualTo(createdAt);
        assertThat(interview.requirementSnapshot()).containsExactly("Java", "Spring Boot", "SQL");

        var startedAt = Instant.parse("2026-08-26T17:00:00Z");
        clock.advanceTo(startedAt);
        service.start(interview.id());
        assertThat(interview.interviewerId()).isEqualTo("user-c");
        assertThat(interview.startedAt()).isEqualTo(startedAt);

        var completedAt = Instant.parse("2026-08-26T18:05:00Z");
        clock.advanceTo(completedAt);
        service.complete(interview.id());
        assertThat(interview.completedAt()).isEqualTo(completedAt);

        var finalizedAt = Instant.parse("2026-08-26T18:10:00Z");
        clock.advanceTo(finalizedAt);
        service.finalizeAssessment(interview.id());
        assertThat(interview.assessmentFinalizedBy()).isEqualTo("user-c");
        assertThat(interview.assessmentFinalizedAt()).isEqualTo(finalizedAt);
    }

    @Test
    void laterEditsTrackCurrentUserWithoutChangingCreatorOrInterviewer() {
        var interviewRepository = mock(InterviewRepository.class);
        var vacancyRepository = mock(JobVacancyRepository.class);
        when(interviewRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var vacancy = new JobVacancy("Backend", List.of("Java"), "creator", Instant.EPOCH);
        when(vacancyRepository.findById(vacancy.id())).thenReturn(Optional.of(vacancy));
        var actor = new MutableActor("interviewer");
        var clock = new MutableClock(Instant.parse("2026-08-26T10:00:00Z"));
        var service = new InterviewService(interviewRepository, vacancyRepository, actor, clock);
        var interview = service.create(vacancy.id(), "Candidato");
        when(interviewRepository.findById(interview.id())).thenReturn(Optional.of(interview));
        service.start(interview.id());

        actor.use("reviewer");
        clock.advanceTo(Instant.parse("2026-08-26T11:00:00Z"));
        service.renameCandidate(interview.id(), "Candidato Atualizado");

        assertThat(interview.createdBy()).isEqualTo("interviewer");
        assertThat(interview.interviewerId()).isEqualTo("interviewer");
        assertThat(interview.updatedBy()).isEqualTo("reviewer");
    }
}
