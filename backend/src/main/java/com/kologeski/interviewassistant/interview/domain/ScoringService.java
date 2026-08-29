package com.kologeski.interviewassistant.interview.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ScoringService {
    private static final int SCALE = 2;

    public ScoreSummary summarize(List<SkillEvaluation> evaluations) {
        var requirements = evaluations.stream()
                .filter(e -> e.source() == EvaluationSource.JOB_REQUIREMENT)
                .toList();
        var scored = requirements.stream().filter(e -> e.status() == EvaluationStatus.SCORED).toList();
        var simple = average(scored);
        var weighted = weightedAverage(scored);
        Map<String, BigDecimal> categories = new LinkedHashMap<>();
        requirements.stream().map(SkillEvaluation::category).distinct().forEach(category -> {
            var categoryScores = scored.stream().filter(e -> e.category().equals(category)).toList();
            if (!categoryScores.isEmpty()) categories.put(category, weightedAverage(categoryScores));
        });
        return new ScoreSummary(simple, weighted, Map.copyOf(categories), scored.size(), requirements.size());
    }

    private BigDecimal average(List<SkillEvaluation> values) {
        if (values.isEmpty()) return null;
        int total = values.stream().mapToInt(SkillEvaluation::score).sum();
        return divide(total, values.size());
    }

    private BigDecimal weightedAverage(List<SkillEvaluation> values) {
        if (values.isEmpty()) return null;
        int weightedTotal = values.stream().mapToInt(e -> e.score() * e.weight()).sum();
        int totalWeight = values.stream().mapToInt(SkillEvaluation::weight).sum();
        return divide(weightedTotal, totalWeight);
    }

    private BigDecimal divide(int dividend, int divisor) {
        return BigDecimal.valueOf(dividend).divide(BigDecimal.valueOf(divisor), SCALE, RoundingMode.HALF_UP);
    }
}
