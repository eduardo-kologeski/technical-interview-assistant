package com.kologeski.interviewassistant.report;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assessment-studies")
public class AcademicStudyController {
    private final AcademicStudySummaryService summaries;

    public AcademicStudyController(AcademicStudySummaryService summaries) { this.summaries = summaries; }

    @GetMapping("/summary")
    AcademicStudySummaryService.Summary summary() { return summaries.summarize(); }

    @GetMapping(value = "/summary.csv", produces = "text/csv")
    ResponseEntity<String> csv() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=academic-study-summary.csv")
                .body(summaries.csv());
    }
}
