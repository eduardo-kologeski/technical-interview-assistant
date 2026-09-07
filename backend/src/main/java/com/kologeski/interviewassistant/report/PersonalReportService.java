package com.kologeski.interviewassistant.report;

import com.kologeski.interviewassistant.api.ResourceNotFoundException;
import com.kologeski.interviewassistant.audit.CurrentActor;
import com.kologeski.interviewassistant.interview.Interview;
import com.kologeski.interviewassistant.interview.InterviewRepository;
import com.kologeski.interviewassistant.interview.PersonalAssessmentDocument;
import com.kologeski.interviewassistant.interview.PersonalAssessmentDocumentCodec;
import com.kologeski.interviewassistant.interview.PersonalAssessmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PersonalReportService {
    private static final Logger log = LoggerFactory.getLogger(PersonalReportService.class);
    private final InterviewRepository assessments;
    private final AssessmentReportRepository reports;
    private final PersonalAssessmentDocumentCodec documents;
    private final ReportGenerationService generation;
    private final CurrentActor actor;
    private final Clock clock;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final ConcurrentHashMap<GenerationKey, CompletableFuture<ReportView>> inFlight = new ConcurrentHashMap<>();

    public PersonalReportService(InterviewRepository assessments, AssessmentReportRepository reports,
                                 PersonalAssessmentDocumentCodec documents, ReportGenerationService generation,
                                 CurrentActor actor, Clock clock, ObjectMapper json,
                                 TransactionTemplate transactions) {
        this.assessments = assessments;
        this.reports = reports;
        this.documents = documents;
        this.generation = generation;
        this.actor = actor;
        this.clock = clock;
        this.json = json;
        this.transactions = transactions;
    }

    public ReportView generate(UUID assessmentId, long expectedVersion, UUID idempotencyKey) {
        Long ownerId = Long.valueOf(actor.required().id());
        ReportView replay = replay(ownerId, assessmentId, idempotencyKey);
        if (replay != null) return replay;

        GenerationKey key = new GenerationKey(ownerId, idempotencyKey);
        CompletableFuture<ReportView> mine = new CompletableFuture<>();
        CompletableFuture<ReportView> running = inFlight.putIfAbsent(key, mine);
        if (running != null) return asReplay(matching(assessmentId, await(running)));

        long started = System.nanoTime();
        try {
            ReportView generated = generateOnce(ownerId, assessmentId, expectedVersion, idempotencyKey);
            mine.complete(generated);
            log.info("event=personal_report_generated assessmentId={} reportId={} promptVersion={} durationMs={}",
                    assessmentId, generated.id(), generated.promptVersion(), elapsedMillis(started));
            return generated;
        } catch (RuntimeException error) {
            mine.completeExceptionally(error);
            log.warn("event=personal_report_generation_failed assessmentId={} type={} durationMs={}",
                    assessmentId, error.getClass().getSimpleName(), elapsedMillis(started));
            throw error;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    private ReportView generateOnce(Long ownerId, UUID assessmentId, long expectedVersion, UUID idempotencyKey) {
        Capture capture = transactions.execute(status -> capture(ownerId, assessmentId, expectedVersion, idempotencyKey));
        if (capture == null) throw new IllegalStateException("Não foi possível capturar a avaliação.");
        if (capture.existing() != null) return view(capture.existing(), true);

        ReportGenerationService.GenerationResult result = generation.generate(capture.assessment(), capture.document());
        try {
            AssessmentReport persisted = transactions.execute(status -> persist(
                    ownerId, assessmentId, capture.sourceVersion(), idempotencyKey, result));
            if (persisted == null) throw new IllegalStateException("Não foi possível persistir a minuta.");
            return view(persisted, false);
        } catch (DataIntegrityViolationException error) {
            ReportView replay = replay(ownerId, assessmentId, idempotencyKey);
            if (replay != null) return replay;
            if (reports.findByAssessmentIdAndOwnerUserId(assessmentId, ownerId).isPresent()) {
                throw new DraftAlreadyExistsException();
            }
            throw error;
        }
    }

    private Capture capture(Long ownerId, UUID assessmentId, long expectedVersion, UUID idempotencyKey) {
        AssessmentReport sameRequest = reports.findByIdempotencyKeyAndOwnerUserId(idempotencyKey, ownerId).orElse(null);
        if (sameRequest != null) return new Capture(null, null, sameRequest.sourceVersion(),
                matchingEntity(assessmentId, sameRequest));
        AssessmentReport current = reports.findByAssessmentIdAndOwnerUserId(assessmentId, ownerId).orElse(null);
        if (current != null) throw new DraftAlreadyExistsException();
        Interview assessment = requiredAssessment(ownerId, assessmentId);
        requireVersion(assessment, expectedVersion);
        return new Capture(assessment, documents.read(assessment.assessmentData()), assessment.version(), null);
    }

    private AssessmentReport persist(Long ownerId, UUID assessmentId, long sourceVersion, UUID idempotencyKey,
                                     ReportGenerationService.GenerationResult result) {
        AssessmentReport sameRequest = reports.findByIdempotencyKeyAndOwnerUserId(idempotencyKey, ownerId).orElse(null);
        if (sameRequest != null) return matchingEntity(assessmentId, sameRequest);
        if (reports.findByAssessmentIdAndOwnerUserId(assessmentId, ownerId).isPresent()) {
            throw new DraftAlreadyExistsException();
        }
        Interview current = requiredAssessment(ownerId, assessmentId);
        requireVersion(current, sourceVersion);
        return reports.saveAndFlush(AssessmentReport.draft(assessmentId, ownerId, sourceVersion,
                idempotencyKey, result.promptVersion(), result.model(), write(result.context()),
                write(result.draft()), Instant.now(clock)));
    }

    private ReportView replay(Long ownerId, UUID assessmentId, UUID idempotencyKey) {
        return reports.findByIdempotencyKeyAndOwnerUserId(idempotencyKey, ownerId)
                .map(report -> view(matchingEntity(assessmentId, report), true)).orElse(null);
    }

    private Interview requiredAssessment(Long ownerId, UUID assessmentId) {
        return assessments.findByIdAndOwnerUserId(assessmentId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Avaliação não encontrada."));
    }

    private static void requireVersion(Interview assessment, long expectedVersion) {
        if (assessment.version() != expectedVersion) {
            throw new PersonalAssessmentService.StalePersonalAssessmentException();
        }
    }

    private ReportView view(AssessmentReport report, boolean replayed) {
        try {
            ReportDraft draft = json.readValue(report.originalDraft(), ReportDraft.class);
            return new ReportView(report.id(), report.assessmentId(), report.status(), report.sourceVersion(),
                    report.promptVersion(), report.modelName(), draft, report.createdAt(), replayed);
        } catch (JacksonException error) {
            throw new IllegalStateException("A minuta persistida possui formato inválido.", error);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JacksonException error) {
            throw new IllegalStateException("Não foi possível serializar a minuta.", error);
        }
    }

    private static AssessmentReport matchingEntity(UUID assessmentId, AssessmentReport report) {
        if (!report.assessmentId().equals(assessmentId)) throw new IdempotencyKeyConflictException();
        return report;
    }

    private static ReportView matching(UUID assessmentId, ReportView report) {
        if (!report.assessmentId().equals(assessmentId)) throw new IdempotencyKeyConflictException();
        return report;
    }

    private static ReportView asReplay(ReportView report) {
        return new ReportView(report.id(), report.assessmentId(), report.status(), report.sourceVersion(),
                report.promptVersion(), report.model(), report.draft(), report.createdAt(), true);
    }

    private static ReportView await(CompletableFuture<ReportView> future) {
        try {
            return future.join();
        } catch (CompletionException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            throw error;
        }
    }

    private static long elapsedMillis(long started) { return (System.nanoTime() - started) / 1_000_000; }

    private record GenerationKey(Long ownerId, UUID idempotencyKey) { }
    private record Capture(Interview assessment, PersonalAssessmentDocument document, long sourceVersion,
                           AssessmentReport existing) { }
    public record ReportView(UUID id, UUID assessmentId, ReportStatus status, long sourceVersion,
                             String promptVersion, String model, ReportDraft draft, Instant createdAt,
                             boolean replayed) { }

    public static final class DraftAlreadyExistsException extends IllegalStateException {
        public DraftAlreadyExistsException() { super("Esta avaliação já possui uma minuta."); }
    }
    public static final class IdempotencyKeyConflictException extends IllegalStateException {
        public IdempotencyKeyConflictException() {
            super("A chave de idempotência já foi usada em outra avaliação.");
        }
    }
}
