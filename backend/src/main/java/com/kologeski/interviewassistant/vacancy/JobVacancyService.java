package com.kologeski.interviewassistant.vacancy;

import com.kologeski.interviewassistant.audit.CurrentActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;
import com.kologeski.interviewassistant.api.ResourceNotFoundException;

@Service
public class JobVacancyService {
    private static final Logger log = LoggerFactory.getLogger(JobVacancyService.class);
    private final JobVacancyRepository repository;
    private final CurrentActor currentActor;
    private final Clock clock;

    public JobVacancyService(JobVacancyRepository repository, CurrentActor currentActor, Clock clock) {
        this.repository = repository;
        this.currentActor = currentActor;
        this.clock = clock;
    }

    @Transactional
    public JobVacancy create(String title, String seniority, String description, List<String> requirements) {
        var actor = currentActor.required();
        var saved = repository.save(new JobVacancy(title, seniority, description, requirements, actor.id(), Instant.now(clock)));
        log.info("event=vacancy_created vacancyId={} actorId={}", saved.id(), actor.id());
        return saved;
    }
    public JobVacancy create(String title, List<String> requirements) {
        return create(title, "Não informada", "", requirements);
    }

    @Transactional(readOnly = true) public List<JobVacancy> list() { return repository.findAll(); }
    @Transactional(readOnly = true) public List<JobVacancy> list(Boolean active) {
        return repository.findAll().stream().filter(item -> active == null || item.active() == active).toList();
    }
    @Transactional(readOnly = true) public JobVacancy get(UUID id) { return required(id); }

    @Transactional
    public JobVacancy update(UUID id, long expectedVersion, String title, String seniority, String description, List<String> requirements) {
        var vacancy = required(id);
        requireVersion(vacancy, expectedVersion);
        var actor = currentActor.required();
        vacancy.update(title, seniority, description, requirements, actor.id(), Instant.now(clock));
        var saved = repository.save(vacancy);
        log.info("event=vacancy_updated vacancyId={} actorId={}", id, actor.id());
        return saved;
    }
    public JobVacancy update(UUID id, String title, List<String> requirements) {
        var vacancy = required(id);
        return update(id, vacancy.version(), title, vacancy.seniority(), vacancy.description(), requirements);
    }

    @Transactional
    public JobVacancy deactivate(UUID id, long expectedVersion) {
        var vacancy = required(id);
        requireVersion(vacancy, expectedVersion);
        var actor = currentActor.required();
        vacancy.deactivate(actor.id(), Instant.now(clock));
        var saved = repository.save(vacancy);
        log.info("event=vacancy_deactivated vacancyId={} actorId={}", id, actor.id());
        return saved;
    }

    private JobVacancy required(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada."));
    }

    private void requireVersion(JobVacancy vacancy, long expectedVersion) {
        if (vacancy.version() != expectedVersion) {
            log.warn("event=vacancy_update_conflict vacancyId={} expectedVersion={} actualVersion={}",
                    vacancy.id(), expectedVersion, vacancy.version());
            throw new StaleVacancyException();
        }
    }

    public static final class StaleVacancyException extends RuntimeException {
        public StaleVacancyException() {
            super("Este registro foi alterado por outro usuário. Recarregue os dados.");
        }
    }
}
