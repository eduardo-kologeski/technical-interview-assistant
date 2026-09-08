package com.kologeski.interviewassistant.candidate;
import jakarta.persistence.*; import java.time.Instant; import java.util.*;
@Entity @Table(name="candidate_timeline_events")
public class CandidateTimelineEvent {
 @Id private UUID id; @Column(name="company_id",nullable=false) private UUID companyId; @Column(name="candidate_id",nullable=false) private UUID candidateId; @Column(name="event_type",nullable=false) private String eventType; @Column(name="vacancy_id") private UUID vacancyId; @Column(name="interview_id") private UUID interviewId; @Column(name="occurred_at",nullable=false) private Instant occurredAt; @Column(name="actor_id",nullable=false) private String actorId;
 protected CandidateTimelineEvent(){} CandidateTimelineEvent(UUID company,UUID candidate,String type,UUID vacancy,UUID interview,Instant now,String actor){id=UUID.randomUUID();companyId=company;candidateId=candidate;eventType=type;vacancyId=vacancy;interviewId=interview;occurredAt=now;actorId=actor;}
 public UUID id(){return id;} public String eventType(){return eventType;} public UUID vacancyId(){return vacancyId;} public UUID interviewId(){return interviewId;} public Instant occurredAt(){return occurredAt;}
}
