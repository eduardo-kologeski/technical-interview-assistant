package com.kologeski.interviewassistant.report;

import com.kologeski.interviewassistant.interview.Interview;
import com.kologeski.interviewassistant.interview.PersonalAssessmentDocument;

/** Pure orchestration core. It becomes an application bean when Ollama is wired in IND-06. */
public class ReportGenerationService {
    private final ReportContextBuilder contexts;
    private final ReportPromptBuilder prompts;
    private final LlmClient llm;
    private final ReportDraftValidator validator;

    public ReportGenerationService(ReportContextBuilder contexts, ReportPromptBuilder prompts,
                                   LlmClient llm, ReportDraftValidator validator) {
        this.contexts = contexts;
        this.prompts = prompts;
        this.llm = llm;
        this.validator = validator;
    }

    public GenerationResult generate(Interview assessment, PersonalAssessmentDocument document) {
        ReportContext context = contexts.build(assessment, document);
        ReportPrompt prompt = prompts.build(context);
        ReportDraft draft = validator.validate(llm.generateDraft(prompt), context);
        return new GenerationResult(context, prompt.version(), draft);
    }

    public record GenerationResult(ReportContext context, String promptVersion, ReportDraft draft) { }
}
