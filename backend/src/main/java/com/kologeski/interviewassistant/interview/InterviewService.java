package com.kologeski.interviewassistant.interview;

import com.kologeski.interviewassistant.audit.CurrentActor;
import com.kologeski.interviewassistant.vacancy.JobVacancyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;
import com.kologeski.interviewassistant.api.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.kologeski.interviewassistant.company.CompanyContext;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class InterviewService {
    private static final Logger log = LoggerFactory.getLogger(InterviewService.class);
    private final InterviewRepository repository;
    private final JobVacancyRepository vacancyRepository;
    private final CurrentActor currentActor;
    private final Clock clock;
    private final CompanyContext companyContext;

    @Autowired
    public InterviewService(InterviewRepository repository, JobVacancyRepository vacancyRepository,
                            CurrentActor currentActor, Clock clock, CompanyContext companyContext) {
        this.repository = repository; this.vacancyRepository = vacancyRepository;
        this.currentActor = currentActor; this.clock = clock; this.companyContext = companyContext;
    }
    public InterviewService(InterviewRepository repository, JobVacancyRepository vacancyRepository,
                            CurrentActor currentActor, Clock clock) {
        this(repository, vacancyRepository, currentActor, clock, null);
    }

    @Transactional
    public Interview create(UUID vacancyId, String candidateName) {
        UUID companyId = companyId();
        var vacancy = (companyContext == null ? vacancyRepository.findById(vacancyId)
                : vacancyRepository.findByIdAndCompanyId(vacancyId, companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada."));
        var actor = currentActor.required();
        var interview = new Interview(companyId, vacancy.id(), vacancy.title(), vacancy.seniority(), vacancy.requirements(),
                candidateName, "", actor.id(), Instant.now(clock));
        var saved = repository.save(interview);
        log.info("event=interview_created interviewId={} vacancyId={} actorId={}", saved.id(), vacancyId, actor.id());
        return saved;
    }

    @Transactional
    public Interview createAndStart(UUID vacancyId, String candidateName, String clientCompany) {
        UUID companyId = companyId();
        var vacancy = (companyContext == null ? vacancyRepository.findById(vacancyId)
                : vacancyRepository.findByIdAndCompanyId(vacancyId, companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Vaga não encontrada."));
        if (!vacancy.active()) throw new IllegalStateException("inactive job position cannot start an interview");
        var actor = currentActor.required();
        var now = Instant.now(clock);
        var interview = new Interview(companyId, vacancy.id(), vacancy.title(), vacancy.seniority(), vacancy.requirements(),
                candidateName, clientCompany, actor.id(), now);
        interview.start(actor.id(), now);
        var saved = repository.save(interview);
        log.info("event=interview_started interviewId={} vacancyId={} actorId={}", saved.id(), vacancyId, actor.id());
        return saved;
    }

    @Transactional(readOnly = true) public Interview get(UUID id) { return required(id); }

    @Transactional(readOnly = true)
    public Page<Interview> list(InterviewStatus status, Pageable pageable) {
        UUID companyId = companyId();
        return status == null ? repository.findByCompanyId(companyId, pageable)
                : repository.findByCompanyIdAndStatus(companyId, status, pageable);
    }

    @Transactional public Interview start(UUID id) { return mutate(id, Interview::start); }
    @Transactional public Interview complete(UUID id) {
        var interview = required(id);
        boolean alreadyCompleted = interview.status() == InterviewStatus.COMPLETED;
        var actor = currentActor.required();
        interview.complete(actor.id(), Instant.now(clock));
        var saved = repository.save(interview);
        if (!alreadyCompleted)
            log.info("event=interview_completed interviewId={} actorId={}", id, actor.id());
        return saved;
    }
    @Transactional
    public Interview finalizeAssessment(UUID id) {
        var interview = required(id);
        boolean alreadyFinalized = interview.assessmentFinalizedAt() != null;
        var actor = currentActor.required();
        interview.finalizeAssessment(actor.id(), Instant.now(clock));
        var saved = repository.save(interview);
        if (!alreadyFinalized)
            log.info("event=interview_assessment_finalized interviewId={} actorId={}", id, actor.id());
        return saved;
    }

    @Transactional
    public Interview registerPdfGeneration(UUID id) {
        var interview = required(id);
        var actor = currentActor.required();
        interview.registerPdfGeneration(actor.id(), Instant.now(clock));
        var saved = repository.save(interview);
        log.info("event=interview_pdf_generated interviewId={} actorId={}", id, actor.id());
        return saved;
    }

    @Transactional
    public Interview renameCandidate(UUID id, String name) {
        var interview = required(id);
        var actor = currentActor.required();
        interview.renameCandidate(name, actor.id(), Instant.now(clock));
        return repository.save(interview);
    }

    @Transactional
    public Interview saveAssessment(UUID id, long expectedVersion, String assessmentData) {
        var interview = required(id);
        if (interview.version() != expectedVersion) {
            log.warn("event=interview_autosave_conflict interviewId={} expectedVersion={} actualVersion={}",
                    id, expectedVersion, interview.version());
            throw new StaleInterviewException();
        }
        var actor = currentActor.required();
        interview.saveAssessment(assessmentData, actor.id(), Instant.now(clock));
        var saved = repository.saveAndFlush(interview);
        log.info("event=interview_autosave_succeeded interviewId={} actorId={} version={}",
                id, actor.id(), saved.version());
        return saved;
    }

    private Interview mutate(UUID id, AuditMutation operation) {
        var interview = required(id);
        var actor = currentActor.required();
        operation.apply(interview, actor.id(), Instant.now(clock));
        return repository.save(interview);
    }

    private Interview required(UUID id) {
        return (companyContext == null ? repository.findById(id) : repository.findByIdAndCompanyId(id, companyId()))
                .orElseThrow(() -> new ResourceNotFoundException("Entrevista não encontrada."));
    }

    private UUID companyId() {
        return companyContext == null ? com.kologeski.interviewassistant.vacancy.JobVacancy.LEGACY_COMPANY_ID
                : companyContext.required().companyId();
    }

    @FunctionalInterface
    private interface AuditMutation {
        void apply(Interview interview, String actorId, Instant occurredAt);
    }

    public static final class StaleInterviewException extends RuntimeException {
        public StaleInterviewException() {
            super("Este registro foi alterado por outro usuário. Recarregue os dados.");
        }
    }
}
