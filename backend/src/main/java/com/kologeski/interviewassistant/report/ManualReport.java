package com.kologeski.interviewassistant.report;

/** Human-authored control artifact. It is never sent to the LLM. */
public record ManualReport(String context, String observedKnowledge, String strengths,
                           String concerns, String synthesis, String conclusion) {
    public void validate(boolean complete) {
        validateField(context, "contexto", complete);
        validateField(observedKnowledge, "conhecimentos observados", complete);
        validateField(strengths, "pontos positivos", complete);
        validateField(concerns, "pontos de atenção", complete);
        validateField(synthesis, "síntese", complete);
        validateField(conclusion, "conclusão humana", complete);
    }
    private static void validateField(String value, String label, boolean complete) {
        if (value != null && value.length() > 8_000) throw new IllegalArgumentException("O campo " + label + " excede o limite permitido.");
        if (complete && (value == null || value.isBlank())) throw new IllegalStateException("Preencha " + label + " antes de finalizar o parecer manual.");
    }
}
