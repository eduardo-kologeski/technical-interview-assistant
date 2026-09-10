package com.kologeski.interviewassistant.report;

import com.kologeski.interviewassistant.interview.Interview;
import com.kologeski.interviewassistant.interview.PersonalAssessmentDocument;
import org.springframework.stereotype.Service;

/** Builds the minimized input, invokes the infrastructure port and validates the draft. */
@Service
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
        return generate(context);
    }

    public GenerationResult generate(ReportContext context) {
        ReportPrompt prompt = prompts.build(context);
        LlmClient.LlmDraftResponse response = llm.generateDraft(prompt);
        if (response == null || response.model() == null || response.model().isBlank()) {
            throw new LlmGenerationException(LlmGenerationException.Kind.INVALID_RESPONSE,
                    "O gerador não identificou o modelo utilizado.");
        }
        ReportDraft draft;
        try {
            draft = validator.validate(response.draft(), context);
        } catch (ReportDraftValidator.InvalidReportDraftException error) {
            throw new LlmGenerationException(LlmGenerationException.Kind.INVALID_RESPONSE,
                    "O Ollama retornou uma minuta que não atende ao contrato.", error);
        }
        return new GenerationResult(context, prompt.version(), response.model(), draft);
    }

    public record GenerationResult(ReportContext context, String promptVersion, String model,
                                   ReportDraft draft) { }
}
