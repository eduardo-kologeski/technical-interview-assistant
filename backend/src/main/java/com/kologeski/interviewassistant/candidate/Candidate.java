package com.kologeski.interviewassistant.candidate;

import com.kologeski.interviewassistant.audit.AuditableEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name="candidates", uniqueConstraints=@UniqueConstraint(name="uk_candidate_company_name", columnNames={"company_id","normalized_name"}))
public class Candidate extends AuditableEntity {
    @Id private UUID id;
    @Column(name="company_id", nullable=false, updatable=false) private UUID companyId;
    @Column(name="display_name", nullable=false, length=160) private String displayName;
    @Column(name="normalized_name", nullable=false, length=160) private String normalizedName;
    protected Candidate() { }
    public Candidate(UUID companyId, String name, String actorId, Instant now) {
        this.id=UUID.randomUUID(); this.companyId=Objects.requireNonNull(companyId); this.displayName=clean(name);
        this.normalizedName=normalize(name); registerCreation(actorId, now);
    }
    public static String normalize(String value) { return clean(value).toLowerCase(Locale.ROOT); }
    private static String clean(String value) { return requiredText(value,"candidateName").replaceAll("\\s+"," "); }
    public UUID id(){return id;} public UUID companyId(){return companyId;} public String displayName(){return displayName;} public String normalizedName(){return normalizedName;}
}
