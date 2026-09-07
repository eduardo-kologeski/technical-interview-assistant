package com.kologeski.interviewassistant.interview;

import com.kologeski.interviewassistant.api.ResourceNotFoundException;
import com.kologeski.interviewassistant.audit.CurrentActor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Personal ownership is always resolved from the authenticated actor, never a company header. */
@Service
public class PersonalAssessmentService {
    private final InterviewRepository repository;
    private final CurrentActor actor;
    private final Clock clock;

    public PersonalAssessmentService(InterviewRepository repository, CurrentActor actor, Clock clock) {
        this.repository = repository;
        this.actor = actor;
        this.clock = clock;
    }

    @Transactional
    public Interview create(String title, String personName, String context) {
        return repository.save(Interview.personal(ownerId(), title, personName, context, Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public Page<Interview> list(Pageable pageable) {
        return repository.findByOwnerUserId(ownerId(), pageable);
    }

    @Transactional(readOnly = true)
    public Interview get(UUID id) {
        return repository.findByIdAndOwnerUserId(id, ownerId())
                .orElseThrow(() -> new ResourceNotFoundException("Avaliação não encontrada."));
    }

    private Long ownerId() { return Long.valueOf(actor.required().id()); }
}
