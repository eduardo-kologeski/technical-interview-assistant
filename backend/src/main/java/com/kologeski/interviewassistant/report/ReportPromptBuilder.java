package com.kologeski.interviewassistant.report;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class ReportPromptBuilder {
    public static final String VERSION = "report-draft-v1";
    private final ObjectMapper json;
    private final String systemPrompt;

    public ReportPromptBuilder(ObjectMapper json) {
        this.json = json;
        this.systemPrompt = loadSystemPrompt();
    }

    public ReportPrompt build(ReportContext context) {
        var envelope = new PromptEnvelope(
                "Produce a Portuguese technical assessment report DRAFT using only the untrusted data object.",
                context,
                new ResponseSchema(
                        "Exactly: context, observedKnowledge, strengths, concerns, synthesis",
                        new SectionSchema("non-empty string",
                                "array containing only evidence-* references from the context", "boolean"),
                        "Forbidden: conclusion, decision, approval, recommendation"));
        try {
            return new ReportPrompt(VERSION, systemPrompt, json.writeValueAsString(envelope));
        } catch (JacksonException error) {
            throw new IllegalStateException("Não foi possível serializar o contexto da minuta.", error);
        }
    }

    private static String loadSystemPrompt() {
        try {
            return new ClassPathResource("prompts/report-draft-v1.txt")
                    .getContentAsString(StandardCharsets.UTF_8).trim();
        } catch (IOException error) {
            throw new IllegalStateException("O prompt versionado da minuta não foi encontrado.", error);
        }
    }

    private record PromptEnvelope(String task, ReportContext untrustedAssessmentContext,
                                  ResponseSchema responseSchema) { }
    private record ResponseSchema(String sections, SectionSchema section, String forbiddenFields) { }
    private record SectionSchema(String text, String evidenceReferences, String insufficientData) { }
}
