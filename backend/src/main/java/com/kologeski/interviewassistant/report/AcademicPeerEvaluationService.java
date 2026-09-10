package com.kologeski.interviewassistant.report;

import com.kologeski.interviewassistant.api.ResourceNotFoundException;
import com.kologeski.interviewassistant.audit.CurrentActor;
import com.kologeski.interviewassistant.interview.InterviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AcademicPeerEvaluationService {
    private final AcademicPeerEvaluationRepository peers;
    private final AssessmentStudyRepository studies;
    private final AssessmentReportRepository reports;
    private final InterviewRepository assessments;
    private final ObjectMapper json;
    private final CurrentActor actor;
    private final Clock clock;

    public AcademicPeerEvaluationService(AcademicPeerEvaluationRepository peers, AssessmentStudyRepository studies,
                                         AssessmentReportRepository reports, InterviewRepository assessments,
                                         ObjectMapper json, CurrentActor actor, Clock clock) {
        this.peers = peers;
        this.studies = studies;
        this.reports = reports;
        this.assessments = assessments;
        this.json = json;
        this.actor = actor;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<View> list(UUID assessmentId) {
        Long owner = owner();
        AssessmentStudy study = requiredReadyStudy(assessmentId, owner);
        return peers.findByStudyIdAndOwnerUserIdOrderByCreatedAtAsc(study.id(), owner).stream()
                .map(this::view).toList();
    }

    @Transactional
    public View create(UUID assessmentId, String reviewerCode) {
        Long owner = owner();
        AssessmentStudy study = requiredReadyStudy(assessmentId, owner);
        requireReconciliationOpen(study);
        List<AcademicPeerEvaluation> existing = peers.findByStudyIdAndOwnerUserIdOrderByCreatedAtAsc(study.id(), owner);
        if (existing.size() >= 9) throw new IllegalStateException("O caso já atingiu o limite de avaliações independentes.");
        String code = reviewerCode == null ? "" : reviewerCode.trim();
        if (peers.existsByStudyIdAndOwnerUserIdAndReviewerCodeIgnoreCase(study.id(), owner, code))
            throw new IllegalStateException("Já existe uma avaliação com esse código de avaliador.");
        AcademicReportEvaluation empty = new AcademicReportEvaluation(List.of(), 0, 0, 0, 0).normalized();
        AcademicPeerEvaluation created = AcademicPeerEvaluation.create(study.id(), assessmentId, owner, code,
                write(empty), owner.toString(), Instant.now(clock));
        return view(peers.saveAndFlush(created));
    }

    @Transactional
    public View save(UUID assessmentId, UUID peerId, long expectedVersion, AcademicReportEvaluation evaluation) {
        Long owner = owner();
        AssessmentStudy study = requiredReadyStudy(assessmentId, owner);
        requireReconciliationOpen(study);
        AcademicPeerEvaluation peer = requiredPeer(peerId, assessmentId, owner);
        requireVersion(peer, expectedVersion);
        evaluation.validate(false);
        AcademicReportEvaluation normalized = evaluation.normalized();
        normalized.validate(false);
        peer.revise(write(normalized), owner.toString(), Instant.now(clock));
        return view(peers.saveAndFlush(peer));
    }

    @Transactional
    public View finalizeEvaluation(UUID assessmentId, UUID peerId, long expectedVersion) {
        Long owner = owner();
        AssessmentStudy study = requiredReadyStudy(assessmentId, owner);
        requireReconciliationOpen(study);
        AcademicPeerEvaluation peer = requiredPeer(peerId, assessmentId, owner);
        requireVersion(peer, expectedVersion);
        AcademicReportEvaluation evaluation = read(peer.evaluation());
        evaluation.validate(true);
        peer.finalizeEvaluation(write(evaluation), owner.toString(), Instant.now(clock));
        return view(peers.saveAndFlush(peer));
    }

    @Transactional
    public void delete(UUID assessmentId, UUID peerId) {
        Long owner = owner();
        AssessmentStudy study = requiredReadyStudy(assessmentId, owner);
        requireReconciliationOpen(study);
        AcademicPeerEvaluation peer = requiredPeer(peerId, assessmentId, owner);
        if (peer.finalized()) throw new IllegalStateException("A avaliação independente finalizada é imutável.");
        peers.delete(peer);
    }

    @Transactional(readOnly = true)
    public AgreementView agreement(UUID assessmentId) {
        Long owner = owner();
        AssessmentStudy study = requiredReadyStudy(assessmentId, owner);
        List<AcademicReportEvaluation> evaluations = new ArrayList<>();
        evaluations.add(read(study.reportEvaluation()));
        peers.findByStudyIdAndOwnerUserIdOrderByCreatedAtAsc(study.id(), owner).stream()
                .filter(AcademicPeerEvaluation::finalized)
                .map(value -> read(value.evaluation()))
                .forEach(evaluations::add);
        Map<AcademicReportEvaluation.Dimension, AgreementAccumulator> dimensions =
                new EnumMap<>(AcademicReportEvaluation.Dimension.class);
        Arrays.stream(AcademicReportEvaluation.Dimension.values())
                .forEach(value -> dimensions.put(value, new AgreementAccumulator()));
        for (int first = 0; first < evaluations.size(); first++) for (int second = first + 1; second < evaluations.size(); second++) {
            Map<AcademicReportEvaluation.Dimension, Integer> left = scores(evaluations.get(first));
            Map<AcademicReportEvaluation.Dimension, Integer> right = scores(evaluations.get(second));
            for (AcademicReportEvaluation.Dimension dimension : AcademicReportEvaluation.Dimension.values())
                dimensions.get(dimension).add(left.get(dimension), right.get(dimension));
        }
        List<DimensionAgreement> values = dimensions.entrySet().stream()
                .map(entry -> entry.getValue().view(entry.getKey())).toList();
        int comparisons = values.stream().mapToInt(DimensionAgreement::comparisons).sum();
        int matches = values.stream().mapToInt(DimensionAgreement::exactMatches).sum();
        return new AgreementView(evaluations.size(), comparisons, matches, percent(matches, comparisons), values);
    }

    private AssessmentStudy requiredReadyStudy(UUID assessmentId, Long owner) {
        assessments.findByIdAndOwnerUserId(assessmentId, owner)
                .orElseThrow(() -> new ResourceNotFoundException("Avaliação não encontrada."));
        AssessmentStudy study = studies.findByAssessmentIdAndOwnerUserId(assessmentId, owner)
                .orElseThrow(() -> new ResourceNotFoundException("Comparação acadêmica não encontrada."));
        AssessmentReport report = reports.findByAssessmentIdAndOwnerUserId(assessmentId, owner)
                .orElseThrow(() -> new IllegalStateException("Gere a minuta original antes da revisão entre avaliadores."));
        if (!study.evaluationFinalized() || study.sourceVersion() != report.sourceVersion())
            throw new IllegalStateException("Finalize a ficha acadêmica original antes da revisão entre avaliadores.");
        return study;
    }

    private AcademicPeerEvaluation requiredPeer(UUID peerId, UUID assessmentId, Long owner) {
        return peers.findByIdAndAssessmentIdAndOwnerUserId(peerId, assessmentId, owner)
                .orElseThrow(() -> new ResourceNotFoundException("Avaliação independente não encontrada."));
    }

    private static void requireReconciliationOpen(AssessmentStudy study) {
        if (study.evaluationReconciliation() != null)
            throw new IllegalStateException("A conciliação já foi iniciada; o conjunto de avaliações é imutável.");
    }

    private static void requireVersion(AcademicPeerEvaluation value, long expected) {
        if (value.version() != expected)
            throw new IllegalStateException("A avaliação independente mudou em outra sessão. Recarregue antes de continuar.");
    }

    private View view(AcademicPeerEvaluation value) {
        return new View(value.id(), value.reviewerCode(), value.version(), read(value.evaluation()), value.finalized(),
                value.createdAt(), value.updatedAt(), value.finalizedAt());
    }

    private AcademicReportEvaluation read(String value) {
        try { return json.readValue(value, AcademicReportEvaluation.class); }
        catch (JacksonException error) { throw new IllegalStateException("A avaliação acadêmica persistida é inválida.", error); }
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JacksonException error) { throw new IllegalStateException("Não foi possível serializar a avaliação acadêmica.", error); }
    }

    private Long owner() { return Long.valueOf(actor.required().id()); }

    private static Map<AcademicReportEvaluation.Dimension, Integer> scores(AcademicReportEvaluation value) {
        Map<AcademicReportEvaluation.Dimension, Integer> result = new EnumMap<>(AcademicReportEvaluation.Dimension.class);
        value.dimensions().forEach(rating -> result.put(rating.dimension(), rating.score()));
        return result;
    }

    private static BigDecimal percent(int value, int total) {
        return total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(value * 100L)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private static final class AgreementAccumulator {
        private int comparisons;
        private int matches;
        private void add(Integer left, Integer right) {
            if (left == null || right == null) return;
            comparisons++;
            if (left.equals(right)) matches++;
        }
        private DimensionAgreement view(AcademicReportEvaluation.Dimension dimension) {
            return new DimensionAgreement(dimension, comparisons, matches, percent(matches, comparisons));
        }
    }

    public record View(UUID id, String reviewerCode, long version, AcademicReportEvaluation evaluation,
                       boolean finalized, Instant createdAt, Instant updatedAt, Instant finalizedAt) { }
    public record AgreementView(int evaluators, int ratingComparisons, int exactMatches,
                                BigDecimal exactAgreementPercent, List<DimensionAgreement> dimensions) { }
    public record DimensionAgreement(AcademicReportEvaluation.Dimension dimension, int comparisons,
                                     int exactMatches, BigDecimal exactAgreementPercent) { }
}
