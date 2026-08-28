package com.kologeski.interviewassistant.vacancy;

import com.kologeski.interviewassistant.audit.AuditableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "job_vacancies")
public class JobVacancy extends AuditableEntity {
    @Id private UUID id;
    @Column(nullable = false) private String title;
    @Column(nullable = false) private String seniority;
    @Column(columnDefinition = "text") private String description;
    @Column(nullable = false) private boolean active;
    @Version
    @Column(nullable = false)
    private long version;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "job_vacancy_requirements", joinColumns = @JoinColumn(name = "vacancy_id"))
    @Column(name = "requirement_name", nullable = false)
    private List<String> requirements = new ArrayList<>();

    protected JobVacancy() { }

    public JobVacancy(String title, List<String> requirements, String actorId, Instant now) {
        this(title, "Não informada", "", requirements, actorId, now);
    }

    public JobVacancy(String title, String seniority, String description, List<String> requirements, String actorId, Instant now) {
        this.id = UUID.randomUUID();
        this.title = requiredText(title, "title");
        this.seniority = requiredText(seniority, "seniority");
        this.description = description == null ? "" : description.trim();
        this.requirements = normalizedRequirements(requirements);
        this.active = true;
        registerCreation(actorId, now);
    }

    public void update(String title, String seniority, String description, List<String> requirements, String actorId, Instant now) {
        this.title = requiredText(title, "title");
        this.seniority = requiredText(seniority, "seniority");
        this.description = description == null ? "" : description.trim();
        this.requirements = normalizedRequirements(requirements);
        registerUpdate(actorId, now);
    }

    public void update(String title, List<String> requirements, String actorId, Instant now) {
        update(title, seniority, description, requirements, actorId, now);
    }

    public void deactivate(String actorId, Instant now) {
        active = false;
        registerUpdate(actorId, now);
    }

    private static List<String> normalizedRequirements(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(java.util.Objects::nonNull).map(String::trim)
                .filter(value -> !value.isBlank()).distinct().toList();
    }

    public UUID id() { return id; }
    public String title() { return title; }
    public String seniority() { return seniority; }
    public String description() { return description; }
    public boolean active() { return active; }
    public List<String> requirements() { return List.copyOf(requirements); }
    public long version() { return version; }
}
