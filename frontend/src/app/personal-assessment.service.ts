import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { PageResponse } from './job-position.service';

export interface PersonalAssessmentSummary {
  id: string;
  title: string;
  personName: string;
  context: string | null;
  status: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface PersonalAssessmentCriterion {
  id: string;
  name: string;
  technology: string | null;
  description: string | null;
  question: string | null;
  rubric: string | null;
  score: number | null;
}

export interface PersonalAssessmentEvidence { id: string; criterionId: string; text: string; }
export interface PersonalAssessmentNote { id: string; text: string; }
export interface PersonalAssessmentFinding { id: string; criterionId: string | null; text: string; }

export interface PersonalAssessmentDocument {
  schemaVersion: 1;
  criteria: PersonalAssessmentCriterion[];
  evidence: PersonalAssessmentEvidence[];
  notes: PersonalAssessmentNote[];
  strengths: PersonalAssessmentFinding[];
  concerns: PersonalAssessmentFinding[];
}

export interface PersonalAssessmentCollection {
  id: string;
  status: 'CREATED' | 'IN_PROGRESS' | 'COMPLETED';
  version: number;
  document: PersonalAssessmentDocument;
  generationEligible: boolean;
  updatedAt: string;
}

export interface CreatePersonalAssessment {
  title: string;
  personName: string;
  context: string;
}

export interface ReportSection { text: string; evidenceReferences: string[]; insufficientData: boolean; }
export interface ReportDraft {
  context: ReportSection;
  observedKnowledge: ReportSection;
  strengths: ReportSection;
  concerns: ReportSection;
  synthesis: ReportSection;
}
export interface PersonalAssessmentReport {
  id: string; assessmentId: string; status: 'DRAFT' | 'APPROVED'; sourceVersion: number; version: number;
  promptVersion: string; model: string; originalDraft: ReportDraft; draft: ReportDraft;
  humanConclusion: string | null; reviewConfirmed: boolean; createdAt: string; updatedAt: string;
  approvedAt: string | null; replayed: boolean;
}
export interface ManualReport { context: string; observedKnowledge: string; strengths: string; concerns: string; synthesis: string; conclusion: string; }
export type AcademicDimension = 'PRESENCE' | 'CORRESPONDENCE' | 'SUPPORT' | 'INSUFFICIENCY_HANDLING' | 'DECISION_SEPARATION';
export interface AcademicDimensionRating { dimension: AcademicDimension; score: number | null; justification: string; sourceReferences: string[]; draftReferences: string[]; }
export interface AcademicReportEvaluation { dimensions: AcademicDimensionRating[]; expectedInformationMissing: number; factualClaimsAnalyzed: number; unsupportedClaims: number; revisedSections: number; }
export interface AssessmentStudy { id: string; assessmentId: string; sourceVersion: number; version: number; manualReport: ManualReport; finalized: boolean; contextHash: string | null; createdAt: string; updatedAt: string; finalizedAt: string | null; finalizedBy: string | null; evaluation: AcademicReportEvaluation | null; evaluationFinalized: boolean; evaluationFinalizedAt: string | null; evaluationFinalizedBy: string | null; }

@Injectable({ providedIn: 'root' })
export class PersonalAssessmentService {
  private readonly http = inject(HttpClient);
  list() { return this.http.get<PageResponse<PersonalAssessmentSummary>>('/api/assessments', { params: { page: 0, size: 20 } }); }
  create(input: CreatePersonalAssessment) { return this.http.post<PersonalAssessmentSummary>('/api/assessments', input); }
  copyRequirements(id: string, input: CreatePersonalAssessment) { return this.http.post<PersonalAssessmentSummary>(`/api/assessments/${id}/copy-requirements`, input); }
  get(id: string) { return this.http.get<PersonalAssessmentSummary>(`/api/assessments/${id}`); }
  delete(id: string) { return this.http.delete<void>(`/api/assessments/${id}`); }
  collection(id: string) { return this.http.get<PersonalAssessmentCollection>(`/api/assessments/${id}/collection`); }
  start(id: string, version: number) {
    return this.http.patch<PersonalAssessmentCollection>(`/api/assessments/${id}/collection/start`, { version });
  }
  save(id: string, version: number, document: PersonalAssessmentDocument) {
    return this.http.put<PersonalAssessmentCollection>(`/api/assessments/${id}/collection`, { version, document });
  }
  complete(id: string, version: number) {
    return this.http.patch<PersonalAssessmentCollection>(`/api/assessments/${id}/collection/complete`, { version });
  }
  report(id: string) { return this.http.get<PersonalAssessmentReport>(`/api/assessments/${id}/report`); }
  generateReport(id: string, version: number, idempotencyKey: string = crypto.randomUUID()) {
    return this.http.post<PersonalAssessmentReport>(`/api/assessments/${id}/report/generate`, { version }, { headers: { 'Idempotency-Key': idempotencyKey } });
  }
  saveReport(id: string, version: number, draft: ReportDraft, humanConclusion: string, reviewConfirmed: boolean) {
    return this.http.put<PersonalAssessmentReport>(`/api/assessments/${id}/report`, { version, draft, humanConclusion, reviewConfirmed });
  }
  approveReport(id: string, version: number, idempotencyKey: string = crypto.randomUUID()) {
    return this.http.post<PersonalAssessmentReport>(`/api/assessments/${id}/report/approve`, { version }, { headers: { 'Idempotency-Key': idempotencyKey } });
  }
  study(id: string) { return this.http.get<AssessmentStudy>(`/api/assessments/${id}/study`); }
  saveManualReport(id: string, assessmentVersion: number, version: number, manualReport: ManualReport) {
    return this.http.put<AssessmentStudy>(`/api/assessments/${id}/study/manual`, { assessmentVersion, version, manualReport });
  }
  finalizeManualReport(id: string, version: number) {
    return this.http.post<AssessmentStudy>(`/api/assessments/${id}/study/manual/finalize`, { version });
  }
  saveStudyEvaluation(id: string, version: number, evaluation: AcademicReportEvaluation) {
    return this.http.put<AssessmentStudy>(`/api/assessments/${id}/study/evaluation`, { version, evaluation });
  }
  finalizeStudyEvaluation(id: string, version: number) {
    return this.http.post<AssessmentStudy>(`/api/assessments/${id}/study/evaluation/finalize`, { version });
  }
}
