package com.kologeski.interviewassistant.report;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcademicReportEvaluationTest {
    @Test
    void normalizesAPartialFormIntoTheCanonicalDimensionOrder() {
        var partial = new AcademicReportEvaluation(List.of(
                new AcademicReportEvaluation.DimensionRating(
                        AcademicReportEvaluation.Dimension.SUPPORT, 1, null, null, null)),
                0, 0, 0, 0);

        var normalized = partial.normalized();

        assertThat(normalized.dimensions()).extracting(AcademicReportEvaluation.DimensionRating::dimension)
                .containsExactly(AcademicReportEvaluation.Dimension.values());
        assertThat(normalized.dimensions().get(2).justification()).isEmpty();
        assertThat(normalized.dimensions().get(2).sourceReferences()).isEmpty();
    }

    @Test
    void requiresCompleteReferencedRatingsAndCoherentCountsForFinalization() {
        var ratings = java.util.Arrays.stream(AcademicReportEvaluation.Dimension.values())
                .map(dimension -> new AcademicReportEvaluation.DimensionRating(
                        dimension, 2, "Verificação humana", List.of("evidence-1"), List.of("synthesis: frase 1")))
                .toList();
        var complete = new AcademicReportEvaluation(ratings, 1, 4, 1, 2);

        complete.validate(true);
        assertThatThrownBy(() -> new AcademicReportEvaluation(ratings, 0, 1, 2, 0).validate(true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não podem exceder");
        var missingReference = new AcademicReportEvaluation(List.of(
                new AcademicReportEvaluation.DimensionRating(
                        AcademicReportEvaluation.Dimension.PRESENCE, 2, "Justificativa", List.of(), List.of("contexto"))),
                0, 0, 0, 0);
        assertThatThrownBy(() -> missingReference.validate(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("referência à entrada");
    }
}
