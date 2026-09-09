package com.kologeski.interviewassistant.report;

import com.kologeski.interviewassistant.api.ResourceNotFoundException;
import com.kologeski.interviewassistant.audit.CurrentActor;
import com.kologeski.interviewassistant.interview.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AssessmentStudyService {
    private final AssessmentStudyRepository studies; private final AssessmentReportRepository reports;
    private final InterviewRepository assessments; private final PersonalAssessmentDocumentCodec documents;
    private final ReportContextBuilder contexts; private final ObjectMapper json; private final CurrentActor actor; private final Clock clock;
    public AssessmentStudyService(AssessmentStudyRepository studies,AssessmentReportRepository reports,InterviewRepository assessments,PersonalAssessmentDocumentCodec documents,ReportContextBuilder contexts,ObjectMapper json,CurrentActor actor,Clock clock){this.studies=studies;this.reports=reports;this.assessments=assessments;this.documents=documents;this.contexts=contexts;this.json=json;this.actor=actor;this.clock=clock;}
    @Transactional(readOnly=true) public View get(UUID assessmentId){Long owner=owner();requiredAssessment(assessmentId,owner);return view(requiredStudy(assessmentId,owner));}
    @Transactional public View save(UUID assessmentId,long assessmentVersion,long expectedVersion,ManualReport manual){
        Long owner=owner();Interview assessment=requiredAssessment(assessmentId,owner);requireSource(assessment,assessmentVersion);manual.validate(false);ensureBeforeGeneration(assessmentId,owner);
        AssessmentStudy study=studies.findByAssessmentIdAndOwnerUserId(assessmentId,owner).orElse(null);String encoded=write(manual);
        if(study==null){if(expectedVersion!=0)throw new StaleStudyException();study=AssessmentStudy.create(assessmentId,owner,assessment.version(),encoded,owner.toString(),Instant.now(clock));}
        else{requireVersion(study,expectedVersion);study.revise(encoded,owner.toString(),Instant.now(clock));}
        return view(studies.saveAndFlush(study));
    }
    @Transactional public View finalizeManual(UUID assessmentId,long expectedVersion){Long owner=owner();Interview assessment=requiredAssessment(assessmentId,owner);AssessmentStudy study=requiredStudy(assessmentId,owner);requireVersion(study,expectedVersion);if(assessment.version()!=study.sourceVersion())throw new StaleStudySourceException();ensureBeforeGeneration(assessmentId,owner);ManualReport manual=read(study.manualReport());manual.validate(true);ReportContext context=contexts.build(assessment,documents.read(assessment.assessmentData()));study.finalizeManual(write(manual),hash(write(context)),owner.toString(),Instant.now(clock));return view(studies.saveAndFlush(study));}
    private void ensureBeforeGeneration(UUID id,Long owner){if(reports.findByAssessmentIdAndOwnerUserId(id,owner).isPresent())throw new IllegalStateException("A minuta já foi revelada; não é possível criar ou alterar o parecer manual deste caso.");}
    private Interview requiredAssessment(UUID id,Long owner){return assessments.findByIdAndOwnerUserId(id,owner).orElseThrow(()->new ResourceNotFoundException("Avaliação não encontrada."));}
    private AssessmentStudy requiredStudy(UUID id,Long owner){return studies.findByAssessmentIdAndOwnerUserId(id,owner).orElseThrow(()->new ResourceNotFoundException("Comparação acadêmica não encontrada."));}
    private static void requireSource(Interview value,long version){if(value.status()!=InterviewStatus.COMPLETED)throw new IllegalStateException("Encerre a coleta antes de iniciar a comparação acadêmica.");if(value.version()!=version)throw new StaleStudySourceException();}
    private static void requireVersion(AssessmentStudy value,long version){if(value.version()!=version)throw new StaleStudyException();}
    private Long owner(){return Long.valueOf(actor.required().id());}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(JacksonException e){throw new IllegalStateException("Não foi possível serializar o artefato acadêmico.",e);}}
    private ManualReport read(String value){try{return json.readValue(value,ManualReport.class);}catch(JacksonException e){throw new IllegalStateException("O parecer manual persistido é inválido.",e);}}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private View view(AssessmentStudy value){return new View(value.id(),value.assessmentId(),value.sourceVersion(),value.version(),read(value.manualReport()),value.finalized(),value.contextHash(),value.createdAt(),value.updatedAt(),value.manualFinalizedAt(),value.manualFinalizedBy());}
    public record View(UUID id,UUID assessmentId,long sourceVersion,long version,ManualReport manualReport,boolean finalized,String contextHash,Instant createdAt,Instant updatedAt,Instant finalizedAt,String finalizedBy){}
    public static final class StaleStudyException extends IllegalStateException{public StaleStudyException(){super("O parecer manual mudou em outra sessão. Recarregue antes de continuar.");}}
    public static final class StaleStudySourceException extends IllegalStateException{public StaleStudySourceException(){super("A avaliação mudou; reinicie a comparação com o contexto atual.");}}
}
