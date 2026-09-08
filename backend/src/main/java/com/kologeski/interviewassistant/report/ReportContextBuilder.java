package com.kologeski.interviewassistant.report;

import com.kologeski.interviewassistant.interview.Interview;
import com.kologeski.interviewassistant.interview.InterviewStatus;
import com.kologeski.interviewassistant.interview.PersonalAssessmentDocument;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ReportContextBuilder {
    public ReportContext build(Interview assessment, PersonalAssessmentDocument document) {
        if (assessment.status() != InterviewStatus.COMPLETED) {
            throw new ReportContextUnavailableException("Encerre a coleta antes de gerar uma minuta.");
        }
        if (assessment.title() == null || assessment.title().isBlank()) {
            throw new ReportContextUnavailableException("Informe o título da avaliação.");
        }
        if (document.criteria().isEmpty()) {
            throw new ReportContextUnavailableException("Inclua pelo menos um critério na avaliação.");
        }
        if (!document.hasEvidenceForGeneration()) {
            throw new ReportContextUnavailableException("Registre pelo menos uma evidência vinculada a um critério.");
        }
        document.validateReferences();

        Map<UUID, String> criteriaReferences = references(
                document.criteria().stream().map(PersonalAssessmentDocument.Criterion::id).toList(), "criterion-");
        Map<UUID, String> evidenceReferences = references(
                document.evidence().stream().map(PersonalAssessmentDocument.Evidence::id).toList(), "evidence-");
        Map<UUID, String> noteReferences = references(
                document.notes().stream().map(PersonalAssessmentDocument.Note::id).toList(), "note-");
        Map<UUID, String> strengthReferences = references(
                document.strengths().stream().map(PersonalAssessmentDocument.Finding::id).toList(), "strength-");
        Map<UUID, String> concernReferences = references(
                document.concerns().stream().map(PersonalAssessmentDocument.Finding::id).toList(), "concern-");

        return new ReportContext(1,
                new ReportContext.Assessment(assessment.title(), blankToNull(assessment.context())),
                document.criteria().stream().map(item -> new ReportContext.Criterion(
                        criteriaReferences.get(item.id()), item.name(), blankToNull(item.technology()),
                        blankToNull(item.description()))).toList(),
                document.evidence().stream().map(item -> new ReportContext.Evidence(
                        evidenceReferences.get(item.id()), criteriaReferences.get(item.criterionId()), item.text())).toList(),
                document.notes().stream().map(item -> new ReportContext.Note(
                        noteReferences.get(item.id()), item.text())).toList(),
                document.strengths().stream().map(item -> new ReportContext.Finding(
                        strengthReferences.get(item.id()), optionalReference(item.criterionId(), criteriaReferences),
                        item.text())).toList(),
                document.concerns().stream().map(item -> new ReportContext.Finding(
                        concernReferences.get(item.id()), optionalReference(item.criterionId(), criteriaReferences),
                        item.text())).toList());
    }

    private static Map<UUID, String> references(Iterable<UUID> ids, String prefix) {
        Map<UUID, String> result = new LinkedHashMap<>();
        AtomicInteger sequence = new AtomicInteger();
        ids.forEach(id -> result.put(id, prefix + sequence.incrementAndGet()));
        return result;
    }

    private static String optionalReference(UUID id, Map<UUID, String> references) {
        return id == null ? null : references.get(id);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static final class ReportContextUnavailableException extends IllegalStateException {
        public ReportContextUnavailableException(String message) { super(message); }
    }
}
