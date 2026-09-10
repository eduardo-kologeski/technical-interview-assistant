package com.kologeski.interviewassistant.report;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Human analysis of limitations and proposed adjustments. It is never sent to the LLM. */
public record AcademicLimitationAnalysis(List<Finding> findings, String overallConclusion) {
    public enum Category { INPUT, CONTEXT, PROMPT, MODEL, INTERFACE, HUMAN_REVIEW }

    public record Finding(UUID id, Category category, String description,
                          List<String> sourceReferences, List<String> draftReferences,
                          String proposedAdjustment, String expectedImpact,
                          String validationPlan, String residualRisk) { }

    public AcademicLimitationAnalysis normalized() {
        List<Finding> values = findings == null ? List.of() : findings.stream().map(value -> new Finding(
                value.id(), value.category(), text(value.description()), references(value.sourceReferences()),
                references(value.draftReferences()), text(value.proposedAdjustment()), text(value.expectedImpact()),
                text(value.validationPlan()), text(value.residualRisk()))).toList();
        return new AcademicLimitationAnalysis(values, text(overallConclusion));
    }

    public void validate(boolean complete) {
        List<Finding> values = findings == null ? List.of() : findings;
        if (values.size() > 50) throw new IllegalArgumentException("Há limitações demais na análise.");
        if (complete && values.isEmpty()) throw new IllegalStateException("Registre ao menos uma limitação antes de finalizar.");
        Set<UUID> ids = new HashSet<>();
        for (Finding finding : values) {
            if (finding == null || finding.id() == null || !ids.add(finding.id()) || finding.category() == null) {
                throw new IllegalArgumentException("As limitações devem ter identificadores únicos e categorias válidas.");
            }
            requiredText(finding.description(), "descrição", 3_000, complete);
            requiredReferences(finding.sourceReferences(), "entrada", complete);
            requiredReferences(finding.draftReferences(), "minuta", complete);
            requiredText(finding.proposedAdjustment(), "ajuste proposto", 3_000, complete);
            requiredText(finding.expectedImpact(), "impacto esperado", 3_000, complete);
            requiredText(finding.validationPlan(), "plano de validação", 3_000, complete);
            requiredText(finding.residualRisk(), "risco residual", 3_000, complete);
        }
        requiredText(overallConclusion, "conclusão da análise", 5_000, complete);
    }

    public boolean contains(UUID findingId) {
        return findingId != null && (findings == null ? List.<Finding>of() : findings).stream()
                .anyMatch(value -> findingId.equals(value.id()));
    }

    private static String text(String value) { return value == null ? "" : value.trim(); }
    private static List<String> references(List<String> values) {
        return values == null ? List.of() : values.stream().map(AcademicLimitationAnalysis::text).filter(value -> !value.isEmpty()).toList();
    }
    private static void requiredText(String value, String label, int max, boolean required) {
        if (value != null && value.length() > max) throw new IllegalArgumentException("O campo " + label + " excede o limite permitido.");
        if (required && (value == null || value.isBlank())) throw new IllegalStateException("Preencha " + label + " de cada limitação.");
    }
    private static void requiredReferences(List<String> values, String label, boolean required) {
        List<String> refs = values == null ? List.of() : values;
        if (refs.size() > 20 || refs.stream().anyMatch(value -> value == null || value.isBlank() || value.length() > 160)) {
            throw new IllegalArgumentException("As referências à " + label + " são inválidas.");
        }
        if (required && refs.isEmpty()) throw new IllegalStateException("Informe ao menos uma referência à " + label + " por limitação.");
    }
}
