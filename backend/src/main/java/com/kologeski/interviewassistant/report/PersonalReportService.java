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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
    private final AssessmentStudyRepository studies;
    private final ReportGenerationClaimRepository claims;
    private final PersonalAssessmentDocumentCodec documents;
    private final ReportGenerationService generation;
    private final ReportDraftValidator validator;
    private final CurrentActor actor;
    private final Clock clock;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final java.time.Duration claimTtl;
    private final ConcurrentHashMap<GenerationKey, CompletableFuture<ReportView>> inFlight = new ConcurrentHashMap<>();

    public PersonalReportService(InterviewRepository assessments, AssessmentReportRepository reports, AssessmentStudyRepository studies,
                                 ReportGenerationClaimRepository claims,
                                 PersonalAssessmentDocumentCodec documents, ReportGenerationService generation,
                                 ReportDraftValidator validator, CurrentActor actor, Clock clock, ObjectMapper json,
                                 TransactionTemplate transactions, OllamaProperties ollama) {
        this.assessments = assessments;
        this.reports = reports;
        this.studies = studies;
        this.claims = claims;
        this.documents = documents;
        this.generation = generation;
        this.validator = validator;
        this.actor = actor;
        this.clock = clock;
        this.json = json;
        this.transactions = transactions;
        this.claimTtl = ollama.getConnectTimeout().plus(ollama.getResponseTimeout()).plusSeconds(60);
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

    public ReportView get(UUID assessmentId) {
        Long ownerId = Long.valueOf(actor.required().id());
        requiredAssessment(ownerId, assessmentId);
        return view(requiredReport(ownerId, assessmentId), false);
    }

    public ReportView saveReview(UUID assessmentId, long expectedVersion, ReportDraft draft,
                                 String humanConclusion, boolean reviewConfirmed) {
        Long ownerId = Long.valueOf(actor.required().id());
        ReportView saved = transactions.execute(status -> {
            requiredAssessment(ownerId, assessmentId);
            AssessmentReport report = requiredReport(ownerId, assessmentId);
            requireReportVersion(report, expectedVersion);
            if (humanConclusion != null && humanConclusion.trim().length() > 8_000) {
                throw new IllegalArgumentException("A conclusão humana excede o limite permitido.");
            }
            ReportContext context = read(report.contextSnapshot(), ReportContext.class,
                    "O contexto persistido da minuta possui formato inválido.");
            ReportDraft validated = validator.validate(draft, context);
            report.revise(write(validated), humanConclusion, reviewConfirmed,
                    ownerId.toString(), Instant.now(clock));
            return view(reports.saveAndFlush(report), false);
        });
        if (saved == null) throw new IllegalStateException("Não foi possível salvar a revisão.");
        return saved;
    }

    public ReportView approve(UUID assessmentId, long expectedVersion, UUID idempotencyKey) {
        Long ownerId = Long.valueOf(actor.required().id());
        ReportView replay = approvalReplay(ownerId, assessmentId, idempotencyKey);
        if (replay != null) return replay;
        try {
            ReportView approved = transactions.execute(status -> approveOnce(
                    ownerId, assessmentId, expectedVersion, idempotencyKey));
            if (approved == null) throw new IllegalStateException("Não foi possível aprovar o parecer.");
            log.info("event=personal_report_approved assessmentId={} reportId={}", assessmentId, approved.id());
            return approved;
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException error) {
            ReportView concurrentReplay = approvalReplay(ownerId, assessmentId, idempotencyKey);
            if (concurrentReplay != null) return concurrentReplay;
            throw new StaleReportException();
        }
    }

    private ReportView approveOnce(Long ownerId, UUID assessmentId, long expectedVersion, UUID idempotencyKey) {
        Interview assessment = requiredAssessment(ownerId, assessmentId);
        AssessmentReport report = requiredReport(ownerId, assessmentId);
        if (report.status() == ReportStatus.APPROVED) {
            if (idempotencyKey.equals(report.approvalIdempotencyKey())) return view(report, true);
            throw new AssessmentReport.ReportAlreadyApprovedException();
        }
        requireReportVersion(report, expectedVersion);
        if (assessment.version() != report.sourceVersion()) throw new StaleReportSourceException();
        report.approve(idempotencyKey, ownerId.toString(), Instant.now(clock));
        return view(reports.saveAndFlush(report), false);
    }

    private ReportView approvalReplay(Long ownerId, UUID assessmentId, UUID idempotencyKey) {
        return reports.findByApprovalIdempotencyKeyAndOwnerUserId(idempotencyKey, ownerId)
                .map(report -> view(matchingEntity(assessmentId, report), true)).orElse(null);
    }

    private ReportView generateOnce(Long ownerId, UUID assessmentId, long expectedVersion, UUID idempotencyKey) {
        Capture capture = transactions.execute(status -> capture(ownerId, assessmentId, expectedVersion, idempotencyKey));
        if (capture == null) throw new IllegalStateException("Não foi possível capturar a avaliação.");
        if (capture.existing() != null) return view(capture.existing(), true);

        try {
            ReportGenerationService.GenerationResult result = generation.generate(capture.assessment(), capture.document());
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
        } finally {
            transactions.executeWithoutResult(status -> claims.deleteByAssessmentIdAndOwnerUserIdAndIdempotencyKey(
                    assessmentId, ownerId, idempotencyKey));
        }
    }

    private Capture capture(Long ownerId, UUID assessmentId, long expectedVersion, UUID idempotencyKey) {
        AssessmentReport sameRequest = reports.findByIdempotencyKeyAndOwnerUserId(idempotencyKey, ownerId).orElse(null);
        if (sameRequest != null) return new Capture(null, null, sameRequest.sourceVersion(),
                matchingEntity(assessmentId, sameRequest));
        Interview assessment = assessments.findPersonalForUpdate(assessmentId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Avaliação não encontrada."));
        sameRequest = reports.findByIdempotencyKeyAndOwnerUserId(idempotencyKey, ownerId).orElse(null);
        if (sameRequest != null) return new Capture(null, null, sameRequest.sourceVersion(),
                matchingEntity(assessmentId, sameRequest));
        AssessmentReport current = reports.findByAssessmentIdAndOwnerUserId(assessmentId, ownerId).orElse(null);
        if (current != null) throw new DraftAlreadyExistsException();
        requireVersion(assessment, expectedVersion);
        Instant now = Instant.now(clock);
        ReportGenerationClaim activeClaim = claims.findByAssessmentIdAndOwnerUserId(assessmentId, ownerId).orElse(null);
        if (activeClaim != null && !activeClaim.expiredAt(now)) throw new GenerationInProgressException();
        if (activeClaim != null) { claims.delete(activeClaim); claims.flush(); }
        AssessmentStudy study = studies.findByAssessmentIdAndOwnerUserId(assessmentId, ownerId).orElse(null);
        if (study != null && !study.finalized()) {
            throw new IllegalStateException("Finalize o parecer manual antes de revelar a minuta deste caso acadêmico.");
        }
        if (study != null && study.sourceVersion() != assessment.version()) {
            throw new AssessmentStudyService.StaleStudySourceException();
        }
        claims.saveAndFlush(new ReportGenerationClaim(assessmentId, ownerId, idempotencyKey,
                assessment.version(), now, now.plus(claimTtl)));
        return new Capture(assessment, documents.read(assessment.assessmentData()), assessment.version(), null);
    }

    private AssessmentReport persist(Long ownerId, UUID assessmentId, long sourceVersion, UUID idempotencyKey,
                                     ReportGenerationService.GenerationResult result) {
        AssessmentReport sameRequest = reports.findByIdempotencyKeyAndOwnerUserId(idempotencyKey, ownerId).orElse(null);
        if (sameRequest != null) return matchingEntity(assessmentId, sameRequest);
        if (reports.findByAssessmentIdAndOwnerUserId(assessmentId, ownerId).isPresent()) {
            throw new DraftAlreadyExistsException();
        }
        Interview current = assessments.findPersonalForUpdate(assessmentId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Avaliação não encontrada."));
        requireVersion(current, sourceVersion);
        ReportGenerationClaim claim = claims.findByAssessmentIdAndOwnerUserId(assessmentId, ownerId)
                .orElseThrow(GenerationReservationLostException::new);
        if (!claim.idempotencyKey().equals(idempotencyKey) || claim.sourceVersion() != sourceVersion) {
            throw new GenerationReservationLostException();
        }
        AssessmentReport saved = reports.saveAndFlush(AssessmentReport.draft(assessmentId, ownerId, sourceVersion,
                idempotencyKey, result.promptVersion(), result.model(), write(result.context()),
                write(result.draft()), Instant.now(clock)));
        claims.delete(claim);
        return saved;
    }

    private ReportView replay(Long ownerId, UUID assessmentId, UUID idempotencyKey) {
        return reports.findByIdempotencyKeyAndOwnerUserId(idempotencyKey, ownerId)
                .map(report -> view(matchingEntity(assessmentId, report), true)).orElse(null);
    }

    private Interview requiredAssessment(Long ownerId, UUID assessmentId) {
        return assessments.findByIdAndOwnerUserId(assessmentId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Avaliação não encontrada."));
    }

    private AssessmentReport requiredReport(Long ownerId, UUID assessmentId) {
        return reports.findByAssessmentIdAndOwnerUserId(assessmentId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Minuta não encontrada."));
    }

    private static void requireVersion(Interview assessment, long expectedVersion) {
        if (assessment.version() != expectedVersion) {
            throw new PersonalAssessmentService.StalePersonalAssessmentException();
        }
    }

    private static void requireReportVersion(AssessmentReport report, long expectedVersion) {
        if (report.version() != expectedVersion) throw new StaleReportException();
    }

    private ReportView view(AssessmentReport report, boolean replayed) {
        ReportDraft original = read(report.originalDraft(), ReportDraft.class,
                "A minuta original persistida possui formato inválido.");
        ReportDraft reviewed = report.reviewedDraft() == null ? original : read(report.reviewedDraft(),
                ReportDraft.class, "A minuta revisada persistida possui formato inválido.");
        return new ReportView(report.id(), report.assessmentId(), report.status(), report.sourceVersion(),
                report.version(), report.promptVersion(), report.modelName(), original, reviewed,
                report.humanConclusion(), report.reviewConfirmed(), report.createdAt(), report.updatedAt(),
                report.approvedAt(), replayed);
    }

    private <T> T read(String value, Class<T> type, String message) {
        try { return json.readValue(value, type); }
        catch (JacksonException error) { throw new IllegalStateException(message, error); }
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
                report.version(), report.promptVersion(), report.model(), report.originalDraft(), report.draft(),
                report.humanConclusion(), report.reviewConfirmed(), report.createdAt(), report.updatedAt(),
                report.approvedAt(), true);
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
    public record ReportView(UUID id, UUID assessmentId, ReportStatus status, long sourceVersion, long version,
                             String promptVersion, String model, ReportDraft originalDraft, ReportDraft draft,
                             String humanConclusion, boolean reviewConfirmed, Instant createdAt, Instant updatedAt,
                             Instant approvedAt, boolean replayed) { }

    public static final class DraftAlreadyExistsException extends IllegalStateException {
        public DraftAlreadyExistsException() { super("Esta avaliação já possui uma minuta."); }
    }
    public static final class IdempotencyKeyConflictException extends IllegalStateException {
        public IdempotencyKeyConflictException() {
            super("A chave de idempotência já foi usada em outra avaliação.");
        }
    }
    public static final class StaleReportException extends IllegalStateException {
        public StaleReportException() { super("A minuta mudou em outra sessão. Recarregue antes de continuar."); }
    }
    public static final class StaleReportSourceException extends IllegalStateException {
        public StaleReportSourceException() {
            super("A avaliação mudou depois da geração. Gere e revise uma nova minuta antes de aprovar.");
        }
    }
    public static final class GenerationInProgressException extends IllegalStateException {
        public GenerationInProgressException() { super("A geração da minuta já está em andamento."); }
    }
    public static final class GenerationReservationLostException extends IllegalStateException {
        public GenerationReservationLostException() { super("A reserva da geração expirou. Tente gerar a minuta novamente."); }
    }
}
