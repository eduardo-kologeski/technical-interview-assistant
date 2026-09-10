package com.kologeski.interviewassistant.report;

import com.kologeski.interviewassistant.audit.CurrentActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
public class AcademicStudySummaryService {
    public static final String PROTOCOL_VERSION = "comparison-v1";
    private final AssessmentStudyRepository studies;
    private final AssessmentReportRepository reports;
    private final AcademicPeerEvaluationRepository peerEvaluations;
    private final CurrentActor actor;
    private final ObjectMapper json;

    public AcademicStudySummaryService(AssessmentStudyRepository studies, AssessmentReportRepository reports,
                                       AcademicPeerEvaluationRepository peerEvaluations, CurrentActor actor, ObjectMapper json) {
        this.studies = studies;
        this.reports = reports;
        this.peerEvaluations = peerEvaluations;
        this.actor = actor;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Summary summarize() {
        Long owner = Long.valueOf(actor.required().id());
        Map<UUID, AssessmentReport> reportsByAssessment = new LinkedHashMap<>();
        reports.findByOwnerUserId(owner).forEach(report -> reportsByAssessment.put(report.assessmentId(), report));
        Map<UUID, List<AcademicPeerEvaluation>> peersByStudy = peerEvaluations
                .findByOwnerUserIdAndFinalizedAtIsNotNull(owner).stream()
                .collect(Collectors.groupingBy(AcademicPeerEvaluation::studyId));
        Map<GroupKey, GroupAccumulator> groups = new LinkedHashMap<>();
        TotalsAccumulator totals = new TotalsAccumulator();
        TimingAccumulator timings = new TimingAccumulator();
        LimitationAccumulator limitations = new LimitationAccumulator();
        AgreementAccumulator agreement = new AgreementAccumulator();
        for (AssessmentStudy study : studies.findByOwnerUserIdAndEvaluationFinalizedAtIsNotNull(owner)) {
            AssessmentReport report = reportsByAssessment.get(study.assessmentId());
            if (report == null || report.sourceVersion() != study.sourceVersion()) continue;
            AcademicReportEvaluation evaluation = read(study.reportEvaluation());
            GroupAccumulator group = groups.computeIfAbsent(
                    new GroupKey(report.promptVersion(), report.modelName()), ignored -> new GroupAccumulator());
            group.add(evaluation, study, report);
            totals.add(evaluation);
            timings.add(study, report);
            limitations.add(study);
            agreement.add(evaluation, peersByStudy.getOrDefault(study.id(), List.of()));
        }
        List<GroupSummary> summaries = groups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(GroupKey::promptVersion)
                        .thenComparing(GroupKey::model)))
                .map(entry -> entry.getValue().view(entry.getKey())).toList();
        return new Summary(PROTOCOL_VERSION, totals.cases, totals.view(), timings.view(), limitations.view(), agreement.view(), summaries);
    }

    public String csv() {
        Summary summary = summarize();
        StringBuilder result = new StringBuilder("protocolVersion,promptVersion,model,dimension,cases,averageScore,score0,score1,score2\n");
        for (GroupSummary group : summary.groups()) for (DimensionSummary dimension : group.dimensions()) {
            result.append(csv(PROTOCOL_VERSION)).append(',').append(csv(group.promptVersion())).append(',')
                    .append(csv(group.model())).append(',').append(dimension.dimension()).append(',')
                    .append(dimension.cases()).append(',').append(dimension.averageScore()).append(',')
                    .append(dimension.score0()).append(',').append(dimension.score1()).append(',')
                    .append(dimension.score2()).append('\n');
        }
        result.append("\ntimingVersion,promptVersion,model,cases,averageManualActiveMillis,averageGenerationLatencyMillis,averageReviewActiveMillis\n");
        for (GroupSummary group : summary.groups()) if (group.timings().cases() > 0) {
            result.append(csv(AssessmentStudy.TIMING_VERSION)).append(',').append(csv(group.promptVersion())).append(',')
                    .append(csv(group.model())).append(',').append(group.timings().cases()).append(',')
                    .append(group.timings().averageManualActiveMillis()).append(',')
                    .append(group.timings().averageGenerationLatencyMillis()).append(',')
                    .append(group.timings().averageReviewActiveMillis()).append('\n');
        }
        result.append("\nlimitationCategory,findings\n");
        summary.limitations().findingsByCategory().forEach((category, count) -> result
                .append(csv(category.name())).append(',').append(count).append('\n'));
        result.append("\nagreementDimension,comparisons,exactMatches,exactAgreementPercent\n");
        summary.agreement().dimensions().forEach(value -> result.append(csv(value.dimension().name())).append(',')
                .append(value.comparisons()).append(',').append(value.exactMatches()).append(',')
                .append(value.exactAgreementPercent()).append('\n'));
        result.append("\nmetric,value\n")
                .append("totalCases,").append(summary.totalCases()).append('\n')
                .append("finalizedLimitationAnalyses,").append(summary.limitations().finalizedAnalyses()).append('\n')
                .append("limitationFindings,").append(summary.limitations().findings()).append('\n')
                .append("agreementCases,").append(summary.agreement().cases()).append('\n')
                .append("peerEvaluations,").append(summary.agreement().peerEvaluations()).append('\n')
                .append("ratingComparisons,").append(summary.agreement().ratingComparisons()).append('\n')
                .append("exactMatches,").append(summary.agreement().exactMatches()).append('\n')
                .append("exactAgreementPercent,").append(summary.agreement().exactAgreementPercent()).append('\n')
                .append("expectedInformationMissing,").append(summary.totals().expectedInformationMissing()).append('\n')
                .append("factualClaimsAnalyzed,").append(summary.totals().factualClaimsAnalyzed()).append('\n')
                .append("unsupportedClaims,").append(summary.totals().unsupportedClaims()).append('\n')
                .append("revisedSections,").append(summary.totals().revisedSections()).append('\n');
        return result.toString();
    }

    private AcademicReportEvaluation read(String value) {
        try { return json.readValue(value, AcademicReportEvaluation.class); }
        catch (JacksonException error) { throw new IllegalStateException("A ficha acadêmica persistida é inválida.", error); }
    }

    private AcademicLimitationAnalysis readLimitations(String value) {
        try { return json.readValue(value, AcademicLimitationAnalysis.class); }
        catch (JacksonException error) { throw new IllegalStateException("A análise de limitações persistida é inválida.", error); }
    }

    private static String csv(String value) {
        String safe = value.matches("^[=+\\-@].*") ? "'" + value : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static final class GroupAccumulator {
        private int cases;
        private final Map<AcademicReportEvaluation.Dimension, Scores> scores = new LinkedHashMap<>();
        private final TimingAccumulator timings = new TimingAccumulator();
        private void add(AcademicReportEvaluation evaluation, AssessmentStudy study, AssessmentReport report) {
            cases++;
            for (AcademicReportEvaluation.DimensionRating rating : evaluation.dimensions())
                scores.computeIfAbsent(rating.dimension(), ignored -> new Scores()).add(rating.score());
            timings.add(study, report);
        }
        private GroupSummary view(GroupKey key) {
            List<DimensionSummary> dimensions = java.util.Arrays.stream(AcademicReportEvaluation.Dimension.values())
                    .map(dimension -> scores.getOrDefault(dimension, new Scores()).view(dimension)).toList();
            return new GroupSummary(key.promptVersion(), key.model(), cases, dimensions, timings.view());
        }
    }

    private static final class TimingAccumulator {
        private int cases; private long manual; private long generation; private long review;
        private void add(AssessmentStudy study, AssessmentReport report) {
            if (!AssessmentStudy.TIMING_VERSION.equals(study.timingVersion()) || report.generationLatencyMillis() == null) return;
            cases++; manual += study.manualActiveMillis(); generation += report.generationLatencyMillis(); review += report.reviewActiveMillis();
        }
        private TimingSummary view() { return new TimingSummary(AssessmentStudy.TIMING_VERSION, cases,
                average(manual, cases), average(generation, cases), average(review, cases)); }
        private static BigDecimal average(long sum, int count) { return count == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(sum)
                .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP); }
    }

    private static final class Scores {
        private int count; private int sum; private int score0; private int score1; private int score2;
        private void add(Integer value) { if (value == null) return; count++; sum += value; if (value == 0) score0++; else if (value == 1) score1++; else if (value == 2) score2++; }
        private DimensionSummary view(AcademicReportEvaluation.Dimension dimension) {
            BigDecimal average = count == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(sum)
                    .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
            return new DimensionSummary(dimension, count, average, score0, score1, score2);
        }
    }

    private static final class TotalsAccumulator {
        private int cases; private int missing; private int analyzed; private int unsupported; private int revised;
        private void add(AcademicReportEvaluation value) { cases++; missing += value.expectedInformationMissing(); analyzed += value.factualClaimsAnalyzed(); unsupported += value.unsupportedClaims(); revised += value.revisedSections(); }
        private CountTotals view() { return new CountTotals(missing, analyzed, unsupported, revised); }
    }

    private final class LimitationAccumulator {
        private int finalizedAnalyses;
        private int findings;
        private final Map<AcademicLimitationAnalysis.Category, Integer> findingsByCategory =
                new EnumMap<>(AcademicLimitationAnalysis.Category.class);
        private LimitationAccumulator() {
            for (AcademicLimitationAnalysis.Category category : AcademicLimitationAnalysis.Category.values())
                findingsByCategory.put(category, 0);
        }
        private void add(AssessmentStudy study) {
            if (!study.limitationAnalysisFinalized()) return;
            AcademicLimitationAnalysis analysis = readLimitations(study.limitationAnalysis());
            finalizedAnalyses++;
            for (AcademicLimitationAnalysis.Finding finding : analysis.findings()) {
                findings++;
                findingsByCategory.compute(finding.category(), (ignored, count) -> count + 1);
            }
        }
        private LimitationSummary view() { return new LimitationSummary(finalizedAnalyses, findings,
                Collections.unmodifiableMap(new LinkedHashMap<>(findingsByCategory))); }
    }

    private final class AgreementAccumulator {
        private int cases;
        private int peerCount;
        private final Map<AcademicReportEvaluation.Dimension, AgreementScores> dimensions =
                new EnumMap<>(AcademicReportEvaluation.Dimension.class);
        private AgreementAccumulator() {
            for (AcademicReportEvaluation.Dimension dimension : AcademicReportEvaluation.Dimension.values())
                dimensions.put(dimension, new AgreementScores());
        }
        private void add(AcademicReportEvaluation primary, List<AcademicPeerEvaluation> peers) {
            if (peers.isEmpty()) return;
            cases++;
            peerCount += peers.size();
            List<AcademicReportEvaluation> evaluations = new ArrayList<>();
            evaluations.add(primary);
            peers.stream().map(value -> read(value.evaluation())).forEach(evaluations::add);
            for (int first = 0; first < evaluations.size(); first++)
                for (int second = first + 1; second < evaluations.size(); second++) {
                    Map<AcademicReportEvaluation.Dimension, Integer> left = scores(evaluations.get(first));
                    Map<AcademicReportEvaluation.Dimension, Integer> right = scores(evaluations.get(second));
                    for (AcademicReportEvaluation.Dimension dimension : AcademicReportEvaluation.Dimension.values())
                        dimensions.get(dimension).add(left.get(dimension), right.get(dimension));
                }
        }
        private AgreementSummary view() {
            List<DimensionAgreementSummary> values = dimensions.entrySet().stream()
                    .map(entry -> entry.getValue().view(entry.getKey())).toList();
            int comparisons = values.stream().mapToInt(DimensionAgreementSummary::comparisons).sum();
            int matches = values.stream().mapToInt(DimensionAgreementSummary::exactMatches).sum();
            return new AgreementSummary(cases, peerCount, comparisons, matches, percent(matches, comparisons), values);
        }
    }

    private static final class AgreementScores {
        private int comparisons;
        private int matches;
        private void add(Integer left, Integer right) {
            if (left == null || right == null) return;
            comparisons++;
            if (left.equals(right)) matches++;
        }
        private DimensionAgreementSummary view(AcademicReportEvaluation.Dimension dimension) {
            return new DimensionAgreementSummary(dimension, comparisons, matches, percent(matches, comparisons));
        }
    }

    private static Map<AcademicReportEvaluation.Dimension, Integer> scores(AcademicReportEvaluation value) {
        Map<AcademicReportEvaluation.Dimension, Integer> result = new EnumMap<>(AcademicReportEvaluation.Dimension.class);
        value.dimensions().forEach(rating -> result.put(rating.dimension(), rating.score()));
        return result;
    }

    private static BigDecimal percent(int value, int total) {
        return total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(value * 100L)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private record GroupKey(String promptVersion, String model) { }
    public record Summary(String protocolVersion, int totalCases, CountTotals totals, TimingSummary timings,
                          LimitationSummary limitations, AgreementSummary agreement, List<GroupSummary> groups) { }
    public record CountTotals(int expectedInformationMissing, int factualClaimsAnalyzed, int unsupportedClaims, int revisedSections) { }
    public record GroupSummary(String promptVersion, String model, int cases, List<DimensionSummary> dimensions, TimingSummary timings) { }
    public record TimingSummary(String timingVersion, int cases, BigDecimal averageManualActiveMillis,
                                BigDecimal averageGenerationLatencyMillis, BigDecimal averageReviewActiveMillis) { }
    public record LimitationSummary(int finalizedAnalyses, int findings,
                                    Map<AcademicLimitationAnalysis.Category, Integer> findingsByCategory) { }
    public record AgreementSummary(int cases, int peerEvaluations, int ratingComparisons, int exactMatches,
                                   BigDecimal exactAgreementPercent, List<DimensionAgreementSummary> dimensions) { }
    public record DimensionAgreementSummary(AcademicReportEvaluation.Dimension dimension, int comparisons,
                                            int exactMatches, BigDecimal exactAgreementPercent) { }
    public record DimensionSummary(AcademicReportEvaluation.Dimension dimension, int cases, BigDecimal averageScore,
                                   int score0, int score1, int score2) { }
}
