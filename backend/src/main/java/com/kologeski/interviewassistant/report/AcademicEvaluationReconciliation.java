package com.kologeski.interviewassistant.report;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Human reconciliation of disagreements between finalized academic evaluations. */
public record AcademicEvaluationReconciliation(List<DimensionResolution> dimensions, String overallConclusion) {
    public record DimensionResolution(AcademicReportEvaluation.Dimension dimension, Integer agreedScore,
                                      String rationale) { }

    public AcademicEvaluationReconciliation normalized() {
        Map<AcademicReportEvaluation.Dimension, DimensionResolution> byDimension =
                (dimensions == null ? List.<DimensionResolution>of() : dimensions).stream()
                        .filter(value -> value != null && value.dimension() != null)
                        .collect(Collectors.toMap(DimensionResolution::dimension, value -> value,
                                (first, ignored) -> first));
        List<DimensionResolution> ordered = Arrays.stream(AcademicReportEvaluation.Dimension.values())
                .map(dimension -> {
                    DimensionResolution value = byDimension.get(dimension);
                    return value == null ? new DimensionResolution(dimension, null, "")
                            : new DimensionResolution(dimension, value.agreedScore(), text(value.rationale()));
                }).toList();
        return new AcademicEvaluationReconciliation(ordered, text(overallConclusion));
    }

    public void validate(boolean complete) {
        List<DimensionResolution> values = dimensions == null ? List.of() : dimensions;
        Set<AcademicReportEvaluation.Dimension> seen = new HashSet<>();
        for (DimensionResolution value : values) {
            if (value == null || value.dimension() == null || !seen.add(value.dimension()))
                throw new IllegalArgumentException("As dimensões da conciliação devem ser únicas e válidas.");
            if (value.agreedScore() != null && (value.agreedScore() < 0 || value.agreedScore() > 2))
                throw new IllegalArgumentException("A nota conciliada deve estar entre 0 e 2.");
            requiredText(value.rationale(), "justificativa da conciliação", 3_000, complete);
            if (complete && value.agreedScore() == null)
                throw new IllegalStateException("Defina a nota conciliada de cada dimensão.");
        }
        if (complete && seen.size() != AcademicReportEvaluation.Dimension.values().length)
            throw new IllegalStateException("Concilie todas as dimensões antes de finalizar.");
        requiredText(overallConclusion, "conclusão da conciliação", 5_000, complete);
    }

    private static String text(String value) { return value == null ? "" : value.trim(); }
    private static void requiredText(String value, String label, int max, boolean required) {
        if (value != null && value.length() > max)
            throw new IllegalArgumentException("O campo " + label + " excede o limite permitido.");
        if (required && (value == null || value.isBlank()))
            throw new IllegalStateException("Preencha " + label + ".");
    }
}
