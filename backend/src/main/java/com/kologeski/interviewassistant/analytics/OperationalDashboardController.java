package com.kologeski.interviewassistant.analytics;

import com.kologeski.interviewassistant.interview.InterviewStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/analytics/operational")
public class OperationalDashboardController {
    private final OperationalDashboardService dashboard;

    public OperationalDashboardController(OperationalDashboardService dashboard) { this.dashboard = dashboard; }

    @GetMapping
    OperationalDashboardService.DashboardResponse dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) InterviewStatus status,
            @RequestParam(required = false) UUID vacancyId,
            @RequestParam(defaultValue = "0") @Min(0) int vacancyPage,
            @RequestParam(defaultValue = "0") @Min(0) int interviewerPage,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return dashboard.dashboard(from, to, status, vacancyId,
                PageRequest.of(vacancyPage, size), PageRequest.of(interviewerPage, size));
    }
}
