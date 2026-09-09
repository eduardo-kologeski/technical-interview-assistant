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

    @Transactional
    public Interview copyRequirements(UUID sourceId, String title, String personName, String context) {
        PersonalAssessmentDocument source = documents.read(required(sourceId).assessmentData());
        if (source.criteria().isEmpty()) throw new IllegalStateException("A avaliação de origem não possui requisitos para copiar.");
        PersonalAssessmentDocument prepared = new PersonalAssessmentDocument(1,
                source.criteria().stream().map(item -> new PersonalAssessmentDocument.Criterion(
                        UUID.randomUUID(), item.name(), item.technology(), item.description(),
                        item.question(), item.rubric(), null)).toList(),
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of());
        Instant now = Instant.now(clock);
        Interview copy = Interview.personal(ownerId(), title, personName, context, now);
        copy.saveAssessment(documents.write(prepared), actor.required().id(), now);
        return repository.saveAndFlush(copy);
    }

    @Transactional(readOnly = true)
    public Page<Interview> list(Pageable pageable) {
        return repository.findByOwnerUserId(ownerId(), pageable);
    }

    @Transactional(readOnly = true)
    public Interview get(UUID id) {
        return required(id);
    }

    @Transactional
    public void delete(UUID id) {
        repository.delete(required(id));
        repository.flush();
    }

    @Transactional(readOnly = true)
    public CollectionView collection(UUID id) {
        return view(required(id));
    }

    @Transactional
    public CollectionView startCollection(UUID id, long expectedVersion) {
        Interview assessment = required(id);
        requireVersion(assessment, expectedVersion);
        if (documents.read(assessment.assessmentData()).criteria().isEmpty()) {
            throw new IllegalStateException("Defina pelo menos um requisito antes de iniciar a avaliação.");
        }
        assessment.start(actor.required().id(), Instant.now(clock));
        return view(repository.saveAndFlush(assessment));
    }

    @Transactional
    public CollectionView saveCollection(UUID id, long expectedVersion, PersonalAssessmentDocument document) {
        Interview assessment = required(id);
        requireVersion(assessment, expectedVersion);
        document.validateReferences();
        if (assessment.status() == InterviewStatus.CREATED) {
            document.requirePreparationOnly();
        } else if (assessment.status() == InterviewStatus.IN_PROGRESS) {
            PersonalAssessmentDocument prepared = documents.read(assessment.assessmentData());
            if (!document.hasSameRequirementDefinitions(prepared)) {
                throw new IllegalStateException("Os requisitos ficam bloqueados depois que a avaliação é iniciada.");
            }
        } else {
            throw new IllegalStateException("Uma avaliação encerrada não pode ser alterada.");
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
