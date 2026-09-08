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

@Injectable({ providedIn: 'root' })
export class PersonalAssessmentService {
  private readonly http = inject(HttpClient);
  list() { return this.http.get<PageResponse<PersonalAssessmentSummary>>('/api/assessments', { params: { page: 0, size: 20 } }); }
  create(input: CreatePersonalAssessment) { return this.http.post<PersonalAssessmentSummary>('/api/assessments', input); }
  get(id: string) { return this.http.get<PersonalAssessmentSummary>(`/api/assessments/${id}`); }
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
}
