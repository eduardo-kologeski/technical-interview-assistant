package com.kologeski.interviewassistant.vacancy;

import com.kologeski.interviewassistant.audit.MutableActor;
import com.kologeski.interviewassistant.audit.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobVacancyServiceAuditTest {
    @Test
    void preservesCreatorWhenAnotherAuthenticatedUserUpdatesVacancy() {
        var repository = mock(JobVacancyRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var actor = new MutableActor("user-a");
        var createdAt = Instant.parse("2026-08-25T12:10:00Z");
        var clock = new MutableClock(createdAt);
        var service = new JobVacancyService(repository, actor, clock);

        var vacancy = service.create("Backend Java Pleno", List.of("Java", "Spring Boot"));
        assertThat(vacancy.createdBy()).isEqualTo("user-a");
        assertThat(vacancy.createdAt()).isEqualTo(createdAt);

        when(repository.findById(vacancy.id())).thenReturn(Optional.of(vacancy));
        actor.use("user-b");
        var updatedAt = Instant.parse("2026-08-26T13:20:00Z");
        clock.advanceTo(updatedAt);
        service.update(vacancy.id(), "Backend Java Pleno", List.of("Java", "Spring Boot", "SQL"));

        assertThat(vacancy.createdBy()).isEqualTo("user-a");
        assertThat(vacancy.createdAt()).isEqualTo(createdAt);
        assertThat(vacancy.updatedBy()).isEqualTo("user-b");
        assertThat(vacancy.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void deactivationIsAuditedWithoutChangingOriginalAuthorship() {
        var repository = mock(JobVacancyRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var actor = new MutableActor("creator");
        var clock = new MutableClock(Instant.parse("2026-08-25T09:00:00Z"));
        var service = new JobVacancyService(repository, actor, clock);
        var vacancy = service.create("QA", List.of("Playwright"));
        when(repository.findById(vacancy.id())).thenReturn(Optional.of(vacancy));

        actor.use("another-user");
        clock.advanceTo(Instant.parse("2026-08-26T09:00:00Z"));
        service.deactivate(vacancy.id());

        assertThat(vacancy.active()).isFalse();
        assertThat(vacancy.createdBy()).isEqualTo("creator");
        assertThat(vacancy.updatedBy()).isEqualTo("another-user");
    }
}
