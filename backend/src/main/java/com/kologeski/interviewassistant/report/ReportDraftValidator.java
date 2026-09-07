package com.kologeski.interviewassistant.report;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class ReportDraftValidator {
    private static final int MAX_SECTION_LENGTH = 8_000;

    public ReportDraft validate(ReportDraft draft, ReportContext context) {
        if (draft == null) throw invalid("A resposta do gerador está vazia.");
        Map<String, ReportDraft.Section> sections = new LinkedHashMap<>();
        sections.put("context", draft.context());
        sections.put("observedKnowledge", draft.observedKnowledge());
        sections.put("strengths", draft.strengths());
        sections.put("concerns", draft.concerns());
        sections.put("synthesis", draft.synthesis());
        Set<String> allowedReferences = context.evidenceReferences();

        sections.forEach((name, section) -> validateSection(name, section, allowedReferences));
        requireEvidenceOrInsufficiency("observedKnowledge", draft.observedKnowledge());
        requireEvidenceOrInsufficiency("strengths", draft.strengths());
        requireEvidenceOrInsufficiency("concerns", draft.concerns());
        requireEvidenceOrInsufficiency("synthesis", draft.synthesis());
        return draft;
    }

    private static void validateSection(String name, ReportDraft.Section section, Set<String> allowedReferences) {
        if (section == null || section.text() == null || section.text().isBlank()) {
            throw invalid("A seção " + name + " deve conter texto.");
        }
        if (section.text().length() > MAX_SECTION_LENGTH) {
            throw invalid("A seção " + name + " excede o limite permitido.");
        }
        if (section.evidenceReferences().stream().distinct().count() != section.evidenceReferences().size()) {
            throw invalid("A seção " + name + " contém referências duplicadas.");
        }
        if (!allowedReferences.containsAll(section.evidenceReferences())) {
            throw invalid("A seção " + name + " referencia evidências fora do contexto.");
        }
    }

    private static void requireEvidenceOrInsufficiency(String name, ReportDraft.Section section) {
        if (!section.insufficientData() && section.evidenceReferences().isEmpty()) {
            throw invalid("A seção " + name + " deve referenciar evidências ou declarar insuficiência.");
        }
    }

    private static InvalidReportDraftException invalid(String message) {
        return new InvalidReportDraftException(message);
    }

    public static final class InvalidReportDraftException extends IllegalArgumentException {
        public InvalidReportDraftException(String message) { super(message); }
    }
}
