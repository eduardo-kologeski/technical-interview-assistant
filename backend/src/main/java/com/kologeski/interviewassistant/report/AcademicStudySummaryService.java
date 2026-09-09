package com.kologeski.interviewassistant.report;

import com.kologeski.interviewassistant.audit.CurrentActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AcademicStudySummaryService {
    public static final String PROTOCOL_VERSION = "comparison-v1";
    private final AssessmentStudyRepository studies;
    private final AssessmentReportRepository reports;
    private final CurrentActor actor;
    private final ObjectMapper json;

    public AcademicStudySummaryService(AssessmentStudyRepository studies, AssessmentReportRepository reports,
                                       CurrentActor actor, ObjectMapper json) {
        this.studies = studies;
        this.reports = reports;
        this.actor = actor;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Summary summarize() {
        Long owner = Long.valueOf(actor.required().id());
        Map<UUID, AssessmentReport> reportsByAssessment = new LinkedHashMap<>();
        reports.findByOwnerUserId(owner).forEach(report -> reportsByAssessment.put(report.assessmentId(), report));
        Map<GroupKey, GroupAccumulator> groups = new LinkedHashMap<>();
        TotalsAccumulator totals = new TotalsAccumulator();
        TimingAccumulator timings = new TimingAccumulator();
        for (AssessmentStudy study : studies.findByOwnerUserIdAndEvaluationFinalizedAtIsNotNull(owner)) {
            AssessmentReport report = reportsByAssessment.get(study.assessmentId());
            if (report == null || report.sourceVersion() != study.sourceVersion()) continue;
            AcademicReportEvaluation evaluation = read(study.reportEvaluation());
            GroupAccumulator group = groups.computeIfAbsent(
                    new GroupKey(report.promptVersion(), report.modelName()), ignored -> new GroupAccumulator());
            group.add(evaluation, study, report);
            totals.add(evaluation);
            timings.add(study, report);
        }
        List<GroupSummary> summaries = groups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(GroupKey::promptVersion)
                        .thenComparing(GroupKey::model)))
                .map(entry -> entry.getValue().view(entry.getKey())).toList();
        return new Summary(PROTOCOL_VERSION, totals.cases, totals.view(), timings.view(), summaries);
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
        result.append("\nmetric,value\n")
                .append("totalCases,").append(summary.totalCases()).append('\n')
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

    private record GroupKey(String promptVersion, String model) { }
    public record Summary(String protocolVersion, int totalCases, CountTotals totals, TimingSummary timings, List<GroupSummary> groups) { }
    public record CountTotals(int expectedInformationMissing, int factualClaimsAnalyzed, int unsupportedClaims, int revisedSections) { }
    public record GroupSummary(String promptVersion, String model, int cases, List<DimensionSummary> dimensions, TimingSummary timings) { }
    public record TimingSummary(String timingVersion, int cases, BigDecimal averageManualActiveMillis,
                                BigDecimal averageGenerationLatencyMillis, BigDecimal averageReviewActiveMillis) { }
    public record DimensionSummary(AcademicReportEvaluation.Dimension dimension, int cases, BigDecimal averageScore,
                                   int score0, int score1, int score2) { }
}
