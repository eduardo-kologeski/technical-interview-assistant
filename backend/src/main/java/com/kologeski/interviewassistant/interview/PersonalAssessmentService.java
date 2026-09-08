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
    private final PersonalAssessmentDocumentCodec documents;

    public PersonalAssessmentService(InterviewRepository repository, CurrentActor actor, Clock clock,
                                     PersonalAssessmentDocumentCodec documents) {
        this.repository = repository;
        this.actor = actor;
        this.clock = clock;
        this.documents = documents;
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
        return required(id);
    }

    @Transactional(readOnly = true)
    public CollectionView collection(UUID id) {
        return view(required(id));
    }

    @Transactional
    public CollectionView startCollection(UUID id, long expectedVersion) {
        Interview assessment = required(id);
        requireVersion(assessment, expectedVersion);
        assessment.start(actor.required().id(), Instant.now(clock));
        return view(repository.saveAndFlush(assessment));
    }

    @Transactional
    public CollectionView saveCollection(UUID id, long expectedVersion, PersonalAssessmentDocument document) {
        Interview assessment = required(id);
        requireVersion(assessment, expectedVersion);
        if (assessment.status() != InterviewStatus.IN_PROGRESS) {
            throw new IllegalStateException("Somente uma coleta em andamento pode ser alterada.");
        }
        assessment.saveAssessment(documents.write(document), actor.required().id(), Instant.now(clock));
        return view(repository.saveAndFlush(assessment));
    }

    @Transactional
    public CollectionView completeCollection(UUID id, long expectedVersion) {
        Interview assessment = required(id);
        requireVersion(assessment, expectedVersion);
        assessment.complete(actor.required().id(), Instant.now(clock));
        return view(repository.saveAndFlush(assessment));
    }

    private CollectionView view(Interview assessment) {
        PersonalAssessmentDocument document = documents.read(assessment.assessmentData());
        boolean generationEligible = assessment.status() == InterviewStatus.COMPLETED
                && document.hasEvidenceForGeneration();
        return new CollectionView(assessment, document, generationEligible);
    }

    private Interview required(UUID id) {
        return repository.findByIdAndOwnerUserId(id, ownerId())
                .orElseThrow(() -> new ResourceNotFoundException("Avaliação não encontrada."));
    }

    private static void requireVersion(Interview assessment, long expectedVersion) {
        if (assessment.version() != expectedVersion) throw new StalePersonalAssessmentException();
    }

    private Long ownerId() { return Long.valueOf(actor.required().id()); }

    public record CollectionView(Interview assessment, PersonalAssessmentDocument document,
                                 boolean generationEligible) { }

    public static final class StalePersonalAssessmentException extends RuntimeException {
        public StalePersonalAssessmentException() {
            super("Esta avaliação foi alterada em outra sessão. Recarregue os dados.");
        }
    }
}
