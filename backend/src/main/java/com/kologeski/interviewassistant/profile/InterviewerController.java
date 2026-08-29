package com.kologeski.interviewassistant.profile;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;
import com.kologeski.interviewassistant.api.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/interviewers")
@Validated
public class InterviewerController {
    private final InterviewerSearchService service;

    public InterviewerController(InterviewerSearchService service) { this.service = service; }

    @GetMapping
    PageResponse<InterviewerSearchService.InterviewerResult> search(
            @RequestParam(required = false) UUID vacancyId,
            @RequestParam(required = false) String technology,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        return PageResponse.from(service.search(vacancyId, technology, date, time, pageable), value -> value);
    }
}
