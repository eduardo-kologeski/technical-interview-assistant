package com.kologeski.interviewassistant.report;

import com.kologeski.interviewassistant.audit.AuditableEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assessment_studies")
public class AssessmentStudy extends AuditableEntity {
    @Id private UUID id;
    @Column(name="assessment_id",nullable=false,updatable=false) private UUID assessmentId;
    @Column(name="owner_user_id",nullable=false,updatable=false) private Long ownerUserId;
    @Column(name="source_version",nullable=false,updatable=false) private long sourceVersion;
    @Column(name="manual_report",nullable=false,columnDefinition="text") private String manualReport;
    @Column(name="context_hash",length=64) private String contextHash;
    @Column(name="manual_finalized_at") private Instant manualFinalizedAt;
    @Column(name="manual_finalized_by",length=100) private String manualFinalizedBy;
    @Version @Column(nullable=false) private long version;
    protected AssessmentStudy() { }
    public static AssessmentStudy create(UUID assessmentId,Long ownerId,long sourceVersion,String manualReport,String actor,Instant now){var value=new AssessmentStudy();value.id=UUID.randomUUID();value.assessmentId=assessmentId;value.ownerUserId=ownerId;value.sourceVersion=sourceVersion;value.manualReport=requiredText(manualReport,"manualReport");value.registerCreation(actor,now);return value;}
    public void revise(String value,String actor,Instant now){requireOpen();manualReport=requiredText(value,"manualReport");registerUpdate(actor,now);}
    public void finalizeManual(String value,String hash,String actor,Instant now){requireOpen();manualReport=requiredText(value,"manualReport");contextHash=requiredText(hash,"contextHash");manualFinalizedAt=now;manualFinalizedBy=requiredText(actor,"actor");registerUpdate(actor,now);}
    private void requireOpen(){if(manualFinalizedAt!=null)throw new IllegalStateException("O parecer manual finalizado é imutável.");}
    public UUID id(){return id;} public UUID assessmentId(){return assessmentId;} public Long ownerUserId(){return ownerUserId;} public long sourceVersion(){return sourceVersion;} public String manualReport(){return manualReport;} public String contextHash(){return contextHash;} public Instant manualFinalizedAt(){return manualFinalizedAt;} public String manualFinalizedBy(){return manualFinalizedBy;} public long version(){return version;} public boolean finalized(){return manualFinalizedAt!=null;}
}
