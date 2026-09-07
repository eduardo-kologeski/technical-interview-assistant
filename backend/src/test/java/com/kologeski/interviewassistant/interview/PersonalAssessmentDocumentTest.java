package com.kologeski.interviewassistant.interview;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonalAssessmentDocumentTest {
    private final PersonalAssessmentDocumentCodec codec =
            new PersonalAssessmentDocumentCodec(new ObjectMapper());

    @Test void roundTripsTheVersionedDocumentAndKeepsEvidenceReferences() {
        UUID criterionId = UUID.randomUUID();
        var document = new PersonalAssessmentDocument(1,
                List.of(new PersonalAssessmentDocument.Criterion(criterionId, "APIs REST", "Spring Boot",
                        null, null, null, 4)),
                List.of(new PersonalAssessmentDocument.Evidence(UUID.randomUUID(), criterionId,
                        "Explicou o fluxo de uma requisição.")),
                List.of(), List.of(), List.of());

        PersonalAssessmentDocument restored = codec.read(codec.write(document));

        assertThat(restored).isEqualTo(document);
        assertThat(restored.hasEvidenceForGeneration()).isTrue();
    }

    @Test void rejectsDuplicateIdsAndReferencesOutsideTheDocument() {
        UUID duplicate = UUID.randomUUID();
        var duplicateIds = new PersonalAssessmentDocument(1,
                List.of(new PersonalAssessmentDocument.Criterion(duplicate, "Java", null, null, null, null, null)),
                List.of(), List.of(new PersonalAssessmentDocument.Note(duplicate, "Nota")), List.of(), List.of());
        assertThatThrownBy(duplicateIds::validateReferences)
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("únicos");

        var invalidReference = new PersonalAssessmentDocument(1,
                List.of(new PersonalAssessmentDocument.Criterion(UUID.randomUUID(), "Java", null, null, null, null, null)),
                List.of(new PersonalAssessmentDocument.Evidence(UUID.randomUUID(), UUID.randomUUID(), "Evidência")),
                List.of(), List.of(), List.of());
        assertThatThrownBy(invalidReference::validateReferences)
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("critério inexistente");
    }

    @Test void beanValidationEnforcesSchemaScoreAndRequiredText() {
        UUID criterionId = UUID.randomUUID();
        var invalid = new PersonalAssessmentDocument(2,
                List.of(new PersonalAssessmentDocument.Criterion(criterionId, " ", null, null, null, null, 6)),
                List.of(new PersonalAssessmentDocument.Evidence(UUID.randomUUID(), criterionId, " ")),
                List.of(), List.of(), List.of());

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var paths = factory.getValidator().validate(invalid).stream()
                    .map(violation -> violation.getPropertyPath().toString()).toList();
            assertThat(paths).contains("schemaVersion", "criteria[0].name", "criteria[0].score", "evidence[0].text");
        }
    }

    @Test void emptyLegacyStorageBecomesAnIneligibleVersionOneDocument() {
        PersonalAssessmentDocument document = codec.read("{}");
        assertThat(document.schemaVersion()).isEqualTo(1);
        assertThat(document.criteria()).isEmpty();
        assertThat(document.hasEvidenceForGeneration()).isFalse();
    }
}
