package com.kologeski.interviewassistant.interview;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class PersonalAssessmentDocumentCodec {
    private final ObjectMapper json;

    public PersonalAssessmentDocumentCodec(ObjectMapper json) {
        this.json = json;
    }

    public String write(PersonalAssessmentDocument document) {
        document.validateReferences();
        try {
            return json.writeValueAsString(document);
        } catch (JacksonException error) {
            throw new IllegalArgumentException("O documento da avaliação possui formato inválido.", error);
        }
    }

    public PersonalAssessmentDocument read(String stored) {
        if (stored == null || stored.isBlank() || stored.trim().equals("{}")) {
            return PersonalAssessmentDocument.empty();
        }
        try {
            PersonalAssessmentDocument document = json.readValue(stored, PersonalAssessmentDocument.class);
            document.validateReferences();
            return document;
        } catch (JacksonException | IllegalArgumentException error) {
            throw new IllegalStateException("O documento persistido da avaliação é inválido.", error);
        }
    }
}
