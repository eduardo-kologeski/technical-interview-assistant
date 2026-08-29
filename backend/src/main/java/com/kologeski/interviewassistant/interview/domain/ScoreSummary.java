package com.kologeski.interviewassistant.interview.domain;

import java.math.BigDecimal;
import java.util.Map;

public record ScoreSummary(
        BigDecimal simpleAverage,
        BigDecimal weightedAverage,
        Map<String, BigDecimal> categoryAverages,
        int evaluatedSkills,
        int totalSkills) {
}
