package com.kologeski.interviewassistant.interview;

import com.kologeski.interviewassistant.audit.CurrentActor;
import com.kologeski.interviewassistant.vacancy.JobVacancyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class InterviewService {
    private final InterviewRepository repository;
    private final JobVacancyRepository vacancyRepository;
    private final CurrentActor currentActor;
    private final Clock clock;

    public InterviewService(InterviewRepository repository, JobVacancyRepository vacancyRepository,
                            CurrentActor currentActor, Clock clock) {
        this.repository = repository;
        this.vacancyRepository = vacancyRepository;
        this.currentActor = currentActor;
        this.clock = clock;
    }

    @Transactional
    public Interview create(UUID vacancyId, String candidateName) {
        var vacancy = vacancyRepository.findById(vacancyId)
                .orElseThrow(() -> new IllegalArgumentException("vacancy not found"));
        var actor = currentActor.required();
        var interview = new Interview(vacancy.id(), vacancy.title(), vacancy.requirements(),
                candidateName, actor.id(), Instant.now(clock));
        return repository.save(interview);
    }

    @Transactional public Interview start(UUID id) { return mutate(id, Interview::start); }
    @Transactional public Interview complete(UUID id) { return mutate(id, Interview::complete); }
    @Transactional public Interview finalizeAssessment(UUID id) { return mutate(id, Interview::finalizeAssessment); }
    @Transactional public Interview registerPdfGeneration(UUID id) { return mutate(id, Interview::registerPdfGeneration); }

    @Transactional
    public Interview renameCandidate(UUID id, String name) {
        var interview = required(id);
        var actor = currentActor.required();
        interview.renameCandidate(name, actor.id(), Instant.now(clock));
        return repository.save(interview);
    }

    private Interview mutate(UUID id, AuditMutation operation) {
        var interview = required(id);
        var actor = currentActor.required();
        operation.apply(interview, actor.id(), Instant.now(clock));
        return repository.save(interview);
    }

    private Interview required(UUID id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("interview not found"));
    }

    @FunctionalInterface
    private interface AuditMutation {
        void apply(Interview interview, String actorId, Instant occurredAt);
    }
}
