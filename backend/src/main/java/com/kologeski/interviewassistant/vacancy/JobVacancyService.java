package com.kologeski.interviewassistant.vacancy;

import com.kologeski.interviewassistant.audit.CurrentActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class JobVacancyService {
    private final JobVacancyRepository repository;
    private final CurrentActor currentActor;
    private final Clock clock;

    public JobVacancyService(JobVacancyRepository repository, CurrentActor currentActor, Clock clock) {
        this.repository = repository;
        this.currentActor = currentActor;
        this.clock = clock;
    }

    @Transactional
    public JobVacancy create(String title, List<String> requirements) {
        var actor = currentActor.required();
        return repository.save(new JobVacancy(title, requirements, actor.id(), Instant.now(clock)));
    }

    @Transactional
    public JobVacancy update(UUID id, String title, List<String> requirements) {
        var vacancy = required(id);
        var actor = currentActor.required();
        vacancy.update(title, requirements, actor.id(), Instant.now(clock));
        return repository.save(vacancy);
    }

    @Transactional
    public JobVacancy deactivate(UUID id) {
        var vacancy = required(id);
        var actor = currentActor.required();
        vacancy.deactivate(actor.id(), Instant.now(clock));
        return repository.save(vacancy);
    }

    private JobVacancy required(UUID id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("vacancy not found"));
    }
}
