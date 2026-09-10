package com.kologeski.interviewassistant.interview;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Versioned, provider-independent input collected during a personal technical assessment. */
public record PersonalAssessmentDocument(
        @NotNull @Min(1) @Max(1) Integer schemaVersion,
        @Size(max = 100) List<@Valid Criterion> criteria,
        @Size(max = 500) List<@Valid Evidence> evidence,
        @Size(max = 200) List<@Valid Note> notes,
        @Size(max = 100) List<@Valid Finding> strengths,
        @Size(max = 100) List<@Valid Finding> concerns) {

    public PersonalAssessmentDocument {
        criteria = immutable(criteria);
        evidence = immutable(evidence);
        notes = immutable(notes);
        strengths = immutable(strengths);
        concerns = immutable(concerns);
    }

    public static PersonalAssessmentDocument empty() {
        return new PersonalAssessmentDocument(1, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    public void validateReferences() {
        Set<UUID> allIds = new HashSet<>();
        Set<UUID> criterionIds = new HashSet<>();
        criteria.forEach(item -> {
            unique(item.id(), allIds);
            criterionIds.add(item.id());
        });
        evidence.forEach(item -> {
            unique(item.id(), allIds);
            referenced(item.criterionId(), criterionIds, "A evidência referencia um critério inexistente.");
        });
        notes.forEach(item -> unique(item.id(), allIds));
        strengths.forEach(item -> {
            unique(item.id(), allIds);
            optionalReference(item.criterionId(), criterionIds);
        });
        concerns.forEach(item -> {
            unique(item.id(), allIds);
            optionalReference(item.criterionId(), criterionIds);
        });
    }

    public boolean hasEvidenceForGeneration() {
        return !criteria.isEmpty() && evidence.stream().anyMatch(item -> !item.text().isBlank());
    }

    public void requirePreparationOnly() {
        if (criteria.stream().anyMatch(item -> item.score() != null)
                || !evidence.isEmpty() || !notes.isEmpty() || !strengths.isEmpty() || !concerns.isEmpty()) {
            throw new IllegalStateException("Antes de iniciar, configure somente os requisitos da avaliação.");
        }
    }

    public boolean hasSameRequirementDefinitions(PersonalAssessmentDocument prepared) {
        if (prepared == null || criteria.size() != prepared.criteria.size()) return false;
        for (int index = 0; index < criteria.size(); index++) {
            if (!criteria.get(index).sameDefinition(prepared.criteria.get(index))) return false;
        }
        return true;
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static void unique(UUID id, Set<UUID> ids) {
        if (!ids.add(id)) throw new IllegalArgumentException("Os IDs do documento devem ser únicos.");
    }

    private static void optionalReference(UUID id, Set<UUID> criterionIds) {
        if (id != null) referenced(id, criterionIds, "O apontamento referencia um critério inexistente.");
    }

    private static void referenced(UUID id, Set<UUID> criterionIds, String message) {
        if (!criterionIds.contains(id)) throw new IllegalArgumentException(message);
    }

    public record Criterion(
            @NotNull UUID id,
            @NotBlank @Size(max = 300) String name,
            @Size(max = 120) String technology,
            @Size(max = 2_000) String description,
            @Size(max = 2_000) String question,
            @Size(max = 3_000) String rubric,
            @Min(0) @Max(5) Integer score) {
        boolean sameDefinition(Criterion other) {
            return other != null && java.util.Objects.equals(id, other.id)
                    && java.util.Objects.equals(name, other.name)
                    && java.util.Objects.equals(technology, other.technology)
                    && java.util.Objects.equals(description, other.description)
                    && java.util.Objects.equals(question, other.question)
                    && java.util.Objects.equals(rubric, other.rubric);
        }
    }

    public record Evidence(
            @NotNull UUID id,
            @NotNull UUID criterionId,
            @NotBlank @Size(max = 5_000) String text) { }

    public record Note(
            @NotNull UUID id,
            @NotBlank @Size(max = 5_000) String text) { }

    public record Finding(
            @NotNull UUID id,
            UUID criterionId,
            @NotBlank @Size(max = 2_000) String text) { }
}
