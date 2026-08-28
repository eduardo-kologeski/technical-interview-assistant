package com.kologeski.interviewassistant.interview.domain;

import java.util.Objects;
import java.util.UUID;

public final class SkillEvaluation {
    private final UUID id;
    private final String category;
    private final String skill;
    private final int weight;
    private final SkillImportance importance;
    private final Integer expectedScore;
    private final EvaluationSource source;
    private Integer score;
    private EvaluationStatus status;
    private String comment;

    public SkillEvaluation(String category, String skill, int weight,
                           SkillImportance importance, Integer expectedScore) {
        this(category, skill, weight, importance, expectedScore, EvaluationSource.JOB_REQUIREMENT);
    }

    public SkillEvaluation(String category, String skill, int weight,
                           SkillImportance importance, Integer expectedScore, EvaluationSource source) {
        this.id = UUID.randomUUID();
        this.category = requireText(category, "category");
        this.skill = requireText(skill, "skill");
        if (weight < 1 || weight > 5) throw new IllegalArgumentException("weight must be between 1 and 5");
        validateScore(expectedScore, "expectedScore");
        this.weight = weight;
        this.importance = Objects.requireNonNull(importance, "importance is required");
        this.expectedScore = expectedScore;
        this.source = Objects.requireNonNull(source, "source is required");
        this.status = EvaluationStatus.PENDING;
        this.comment = "";
    }

    public void score(int value, String comment) {
        validateScore(value, "score");
        this.score = value;
        this.status = EvaluationStatus.SCORED;
        this.comment = normalize(comment);
    }

    public void markNotEvaluated(String comment) {
        this.score = null;
        this.status = EvaluationStatus.NOT_EVALUATED;
        this.comment = normalize(comment);
    }

    private static void validateScore(Integer value, String field) {
        if (value != null && (value < 0 || value > 5)) {
            throw new IllegalArgumentException(field + " must be between 0 and 5");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private static String normalize(String value) { return value == null ? "" : value.trim(); }

    public UUID id() { return id; }
    public String category() { return category; }
    public String skill() { return skill; }
    public int weight() { return weight; }
    public SkillImportance importance() { return importance; }
    public Integer expectedScore() { return expectedScore; }
    public Integer score() { return score; }
    public EvaluationStatus status() { return status; }
    public String comment() { return comment; }
    public EvaluationSource source() { return source; }
}
