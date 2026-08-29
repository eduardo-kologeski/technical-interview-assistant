package com.kologeski.interviewassistant.interview.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringServiceTest {
    private final ScoringService service = new ScoringService();

    @Test
    void calculatesSimpleWeightedAndCategoryAverages() {
        var java = evaluation("Linguagens", "Java", 5, 5);
        var sql = evaluation("Dados", "SQL", 3, 3);

        var summary = service.summarize(List.of(java, sql));

        assertThat(summary.simpleAverage()).isEqualByComparingTo(new BigDecimal("4.00"));
        assertThat(summary.weightedAverage()).isEqualByComparingTo(new BigDecimal("4.25"));
        assertThat(summary.categoryAverages()).containsEntry("Linguagens", new BigDecimal("5.00"));
    }

    @Test
    void ignoresPendingAndNotEvaluatedSkills() {
        var scored = evaluation("Backend", "REST", 4, 4);
        var notEvaluated = new SkillEvaluation("Infra", "Kubernetes", 5, SkillImportance.DESIRED, 3);
        notEvaluated.markNotEvaluated("Fora do escopo da conversa");
        var pending = new SkillEvaluation("Infra", "Docker", 2, SkillImportance.DESIRED, 3);

        var summary = service.summarize(List.of(scored, notEvaluated, pending));

        assertThat(summary.weightedAverage()).isEqualByComparingTo(new BigDecimal("4.00"));
        assertThat(summary.evaluatedSkills()).isOne();
        assertThat(summary.totalSkills()).isEqualTo(3);
        assertThat(summary.categoryAverages()).doesNotContainKey("Infra");
    }

    @Test
    void returnsUnavailableAveragesWhenNothingWasScored() {
        var evaluation = new SkillEvaluation("Backend", "Java", 5, SkillImportance.REQUIRED, 4);

        var summary = service.summarize(List.of(evaluation));

        assertThat(summary.simpleAverage()).isNull();
        assertThat(summary.weightedAverage()).isNull();
        assertThat(summary.categoryAverages()).isEmpty();
    }

    @Test
    void excludesComplementaryExperienceFromJobScore() {
        var requirement = evaluation("Backend", "Java", 5, 3);
        var complementary = new SkillEvaluation("Complementar", "PHP", 5, SkillImportance.OPTIONAL, null,
                EvaluationSource.COMPLEMENTARY_EXPERIENCE);
        complementary.score(5, "Quatro anos de experiência");

        var summary = service.summarize(List.of(requirement, complementary));

        assertThat(summary.simpleAverage()).isEqualByComparingTo("3.00");
        assertThat(summary.weightedAverage()).isEqualByComparingTo("3.00");
        assertThat(summary.evaluatedSkills()).isOne();
        assertThat(summary.totalSkills()).isOne();
        assertThat(complementary.source()).isEqualTo(EvaluationSource.COMPLEMENTARY_EXPERIENCE);
    }

    private SkillEvaluation evaluation(String category, String skill, int weight, int score) {
        var evaluation = new SkillEvaluation(category, skill, weight, SkillImportance.REQUIRED, 4);
        evaluation.score(score, "evidence");
        return evaluation;
    }
}
