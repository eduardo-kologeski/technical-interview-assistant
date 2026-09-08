package com.kologeski.interviewassistant.report;

import java.util.List;

/** The model output is always a reviewable draft and deliberately has no human conclusion field. */
public record ReportDraft(
        Section context,
        Section observedKnowledge,
        Section strengths,
        Section concerns,
        Section synthesis) {

    public record Section(String text, List<String> evidenceReferences, boolean insufficientData) {
        public Section {
            evidenceReferences = evidenceReferences == null ? List.of() : List.copyOf(evidenceReferences);
        }
    }
}
