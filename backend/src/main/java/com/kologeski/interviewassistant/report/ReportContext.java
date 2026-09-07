package com.kologeski.interviewassistant.report;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Provider-independent allowlist of assessment facts authorized for draft generation. */
public record ReportContext(
        int schemaVersion,
        Assessment assessment,
        List<Criterion> criteria,
        List<Evidence> evidence,
        List<Note> notes,
        List<Finding> strengths,
        List<Finding> concerns) {

    public ReportContext {
        criteria = List.copyOf(criteria);
        evidence = List.copyOf(evidence);
        notes = List.copyOf(notes);
        strengths = List.copyOf(strengths);
        concerns = List.copyOf(concerns);
    }

    public Set<String> evidenceReferences() {
        return evidence.stream().map(Evidence::reference).collect(Collectors.toUnmodifiableSet());
    }

    public record Assessment(String title, String context) { }
    public record Criterion(String reference, String name, String technology, String description) { }
    public record Evidence(String reference, String criterionReference, String text) { }
    public record Note(String reference, String text) { }
    public record Finding(String reference, String criterionReference, String text) { }
}
