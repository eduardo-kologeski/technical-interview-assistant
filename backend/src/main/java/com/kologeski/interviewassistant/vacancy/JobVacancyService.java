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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.kologeski.interviewassistant.company.CompanyContext;
import com.kologeski.interviewassistant.company.CompanyAuthorizationPolicy;
import com.kologeski.interviewassistant.company.CompanyCapability;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class JobVacancyService {
    private static final Logger log = LoggerFactory.getLogger(JobVacancyService.class);
    private final JobVacancyRepository repository;
    private final CurrentActor currentActor;
    private final Clock clock;
    private final CompanyContext companyContext;
    private final CompanyAuthorizationPolicy authorization;

    @Autowired
    public JobVacancyService(JobVacancyRepository repository, CurrentActor currentActor, Clock clock,
                             CompanyContext companyContext, CompanyAuthorizationPolicy authorization) {
        this.repository = repository; this.currentActor = currentActor; this.clock = clock;
        this.companyContext = companyContext; this.authorization = authorization;
    }
    public JobVacancyService(JobVacancyRepository repository, CurrentActor currentActor, Clock clock) {
        this(repository, currentActor, clock, null, null);
    }

    @Transactional
    public JobVacancy create(String title, String seniority, String description, List<String> requirements) {
        require(CompanyCapability.MANAGE_VACANCIES);
        var actor = currentActor.required();
        var saved = repository.save(new JobVacancy(companyId(), title, seniority, description, requirements, actor.id(), Instant.now(clock)));
        log.info("event=vacancy_created vacancyId={} actorId={}", saved.id(), actor.id());
        return saved;
    }
    public JobVacancy create(String title, List<String> requirements) {
        return create(title, "Não informada", "", requirements);
    }

    @Transactional(readOnly = true) public List<JobVacancy> list() {
        return companyContext == null ? repository.findAll() : repository.findAllByCompanyId(companyId());
    }
    @Transactional(readOnly = true) public List<JobVacancy> list(Boolean active) {
        return list().stream().filter(item -> active == null || item.active() == active).toList();
    }
    @Transactional(readOnly = true)
    public Page<JobVacancy> list(Boolean active, String query, Pageable pageable) {
        return repository.search(companyId(), active, query == null ? "" : query.trim(), pageable);
    }
    @Transactional(readOnly = true) public JobVacancy get(UUID id) { return required(id); }

    @Transactional
    public JobVacancy update(UUID id, long expectedVersion, String title, String seniority, String description, List<String> requirements) {
        require(CompanyCapability.MANAGE_VACANCIES);
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
        require(CompanyCapability.MANAGE_VACANCIES);
        var vacancy = required(id);
        requireVersion(vacancy, expectedVersion);
        var actor = currentActor.required();
        vacancy.deactivate(actor.id(), Instant.now(clock));
        var saved = repository.save(vacancy);
        log.info("event=vacancy_deactivated vacancyId={} actorId={}", id, actor.id());
        return saved;
    }

    private JobVacancy required(UUID id) {
        return (companyContext == null ? repository.findById(id) : repository.findByIdAndCompanyId(id, companyId()))
                .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada."));
    }

    private UUID companyId() {
        return companyContext == null ? JobVacancy.LEGACY_COMPANY_ID
                : authorization.require(CompanyCapability.VIEW_VACANCIES).companyId();
    }

    private void require(CompanyCapability capability) {
        if (authorization != null) authorization.require(capability);
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
