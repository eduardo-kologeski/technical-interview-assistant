import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { PageResponse } from './job-position.service';
export interface CandidateSummary { id:string;name:string;createdAt:string }
export interface CandidateTimeline { id:string;type:string;vacancyId?:string;interviewId?:string;occurredAt:string }
export interface CandidateDetail { candidate:CandidateSummary;timeline:CandidateTimeline[] }
@Injectable({providedIn:'root'}) export class CandidateService {private http=inject(HttpClient);list(q='',page=0,size=20){return this.http.get<PageResponse<CandidateSummary>>('/api/candidates',{params:{q,page,size}})}get(id:string){return this.http.get<CandidateDetail>(`/api/candidates/${id}`)}}
