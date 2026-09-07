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

@Injectable({ providedIn: 'root' })
export class PersonalAssessmentService {
  private readonly http = inject(HttpClient);
  list() { return this.http.get<PageResponse<PersonalAssessmentSummary>>('/api/assessments', { params: { page: 0, size: 20 } }); }
}
