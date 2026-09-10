package com.kologeski.interviewassistant.report;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

/** Human evaluation of the original draft. It is never sent to the LLM. */
public record AcademicReportEvaluation(
        List<DimensionRating> dimensions,
        int expectedInformationMissing,
        int factualClaimsAnalyzed,
        int unsupportedClaims,
        int revisedSections) {

    public enum Dimension {
        PRESENCE, CORRESPONDENCE, SUPPORT, INSUFFICIENCY_HANDLING, DECISION_SEPARATION
    }

    public record DimensionRating(Dimension dimension, Integer score, String justification,
                                  List<String> sourceReferences, List<String> draftReferences) { }

    public AcademicReportEvaluation normalized() {
        Map<Dimension, DimensionRating> byDimension = (dimensions == null ? List.<DimensionRating>of() : dimensions).stream()
                .filter(value -> value != null && value.dimension() != null)
                .collect(Collectors.toMap(DimensionRating::dimension, value -> value, (first, ignored) -> first));
        List<DimensionRating> ordered = java.util.Arrays.stream(Dimension.values()).map(dimension -> {
            DimensionRating value = byDimension.get(dimension);
            return value == null
                    ? new DimensionRating(dimension, null, "", List.of(), List.of())
                    : new DimensionRating(dimension, value.score(), value.justification() == null ? "" : value.justification(),
                    value.sourceReferences() == null ? List.of() : List.copyOf(value.sourceReferences()),
                    value.draftReferences() == null ? List.of() : List.copyOf(value.draftReferences()));
        }).toList();
        return new AcademicReportEvaluation(ordered, expectedInformationMissing, factualClaimsAnalyzed,
                unsupportedClaims, revisedSections);
    }

    public void validate(boolean complete) {
        requireCount(expectedInformationMissing, "informações esperadas ausentes");
        requireCount(factualClaimsAnalyzed, "afirmações factuais analisadas");
        requireCount(unsupportedClaims, "afirmações sem sustentação");
        requireCount(revisedSections, "seções alteradas na revisão");
        if (unsupportedClaims > factualClaimsAnalyzed) {
            throw new IllegalArgumentException("Afirmações sem sustentação não podem exceder as afirmações analisadas.");
        }
        List<DimensionRating> values = dimensions == null ? List.of() : dimensions;
        Set<Dimension> seen = new HashSet<>();
        for (DimensionRating rating : values) {
            if (rating == null || rating.dimension() == null || !seen.add(rating.dimension())) {
                throw new IllegalArgumentException("As dimensões da rubrica devem ser únicas e válidas.");
            }
            if (rating.score() != null && (rating.score() < 0 || rating.score() > 2)) {
                throw new IllegalArgumentException("A nota da rubrica deve estar entre 0 e 2.");
            }
            validateText(rating.justification(), "justificativa", 3_000, complete);
            validateReferences(rating.sourceReferences(), "entrada", complete);
            validateReferences(rating.draftReferences(), "minuta", complete);
            if (complete && rating.score() == null) throw new IllegalStateException("Atribua uma nota a cada dimensão.");
        }
        if (complete && seen.size() != Dimension.values().length) {
            throw new IllegalStateException("Avalie todas as dimensões antes de finalizar a ficha.");
        }
    }

    private static void requireCount(int value, String label) {
        if (value < 0 || value > 10_000) throw new IllegalArgumentException("A contagem de " + label + " é inválida.");
    }

    private static void validateText(String value, String label, int max, boolean required) {
        if (value != null && value.length() > max) throw new IllegalArgumentException("O campo " + label + " excede o limite permitido.");
        if (required && (value == null || value.isBlank())) throw new IllegalStateException("Preencha a " + label + " de cada dimensão.");
    }

    private static void validateReferences(List<String> values, String label, boolean required) {
        List<String> references = values == null ? List.of() : values;
        if (references.size() > 20) throw new IllegalArgumentException("Há referências demais para a " + label + ".");
        if (references.stream().anyMatch(value -> value == null || value.isBlank() || value.length() > 160)) {
            throw new IllegalArgumentException("As referências à " + label + " devem ter entre 1 e 160 caracteres.");
        }
        if (required && references.isEmpty()) throw new IllegalStateException("Informe ao menos uma referência à " + label + " por dimensão.");
    }
}
