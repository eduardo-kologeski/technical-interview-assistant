package com.kologeski.interviewassistant.profile;

import com.kologeski.interviewassistant.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_technical_skills")
public class UserTechnicalSkill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(name = "normalized_name", nullable = false, length = 120)
    private String normalizedName;
    @Enumerated(EnumType.STRING)
    @Column(name = "proficiency_level", nullable = false, length = 20)
    private ProficiencyLevel proficiencyLevel;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserTechnicalSkill() { }

    UserTechnicalSkill(User user, String name, String normalizedName, ProficiencyLevel level, Instant now) {
        this.user = user;
        this.name = name;
        this.normalizedName = normalizedName;
        this.proficiencyLevel = level;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void update(String name, String normalizedName, ProficiencyLevel level, Instant now) {
        this.name = name;
        this.normalizedName = normalizedName;
        this.proficiencyLevel = level;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getName() { return name; }
    public String getNormalizedName() { return normalizedName; }
    public ProficiencyLevel getProficiencyLevel() { return proficiencyLevel; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
