package com.kologeski.interviewassistant.report;

/** Infrastructure port. The MVP implementation will be OllamaLlmClient in IND-06. */
public interface LlmClient {
    LlmDraftResponse generateDraft(ReportPrompt prompt);

    record LlmDraftResponse(ReportDraft draft, String model) { }
}
