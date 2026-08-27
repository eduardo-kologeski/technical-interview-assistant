package com.kologeski.interviewassistant.profile;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/interviewers")
public class InterviewerController {
    private final InterviewerSearchService service;

    public InterviewerController(InterviewerSearchService service) { this.service = service; }

    @GetMapping
    List<InterviewerSearchService.InterviewerResult> search(
            @RequestParam(required = false) UUID vacancyId,
            @RequestParam(required = false) String technology,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time) {
        return service.search(vacancyId, technology, date, time);
    }
}
