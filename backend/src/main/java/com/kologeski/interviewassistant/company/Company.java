package com.kologeski.interviewassistant.company;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "companies")
public class Company {
    @Id private UUID id;
    @Column(nullable = false, length = 160) private String name;
    @Column(nullable = false, unique = true, length = 120) private String slug;
    @Column(name = "created_by_user_id") private Long createdByUserId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Company() { }

    public Company(String name, String slug, Long creatorId, Instant now) {
        this.id = UUID.randomUUID();
        this.name = required(name, "name");
        this.slug = required(slug, "slug");
        this.createdByUserId = creatorId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public String slug() { return slug; }
    public Long createdByUserId() { return createdByUserId; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public void configure(String name, String slug, Long creatorId, Instant now) {
        this.name = required(name, "name");
        this.slug = required(slug, "slug");
        this.createdByUserId = creatorId;
        this.updatedAt = now;
    }
    public void rename(String value, Instant now) { this.name = required(value, "name"); this.updatedAt = now; }
}
