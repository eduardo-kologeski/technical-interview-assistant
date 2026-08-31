package com.kologeski.interviewassistant.candidate;
import jakarta.persistence.*; import java.time.Instant; import java.util.*;
@Entity @Table(name="candidate_vacancies",uniqueConstraints=@UniqueConstraint(name="uk_candidate_vacancy",columnNames={"candidate_id","vacancy_id"}))
public class CandidateVacancyLink {
 @Id private UUID id; @Column(name="company_id",nullable=false) private UUID companyId; @Column(name="candidate_id",nullable=false) private UUID candidateId; @Column(name="vacancy_id",nullable=false) private UUID vacancyId; @Column(name="first_seen_at",nullable=false) private Instant firstSeenAt; @Column(name="last_seen_at",nullable=false) private Instant lastSeenAt;
 protected CandidateVacancyLink(){} CandidateVacancyLink(UUID company,UUID candidate,UUID vacancy,Instant now){id=UUID.randomUUID();companyId=company;candidateId=candidate;vacancyId=vacancy;firstSeenAt=now;lastSeenAt=now;} void touch(Instant now){lastSeenAt=now;}
}
