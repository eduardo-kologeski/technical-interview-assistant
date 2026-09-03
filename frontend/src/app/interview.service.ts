import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { API_BASE_URL } from './api-config';
export interface InterviewRecord { id:string; vacancyId:string; candidateName:string; vacancyTitleSnapshot:string; senioritySnapshot:string; clientCompany:string; requirementSnapshot:string[]; interviewerId:string; status:string; startedAt:string; version:number; assessmentData:string }
@Injectable({providedIn:'root'}) export class InterviewService {
  private http=inject(HttpClient); private url=`${API_BASE_URL}/interviews`;
  create(candidateName:string,jobPositionId:string,clientCompany:string){return this.http.post<InterviewRecord>(this.url,{candidateName,jobPositionId,clientCompany})}
  get(id:string){return this.http.get<InterviewRecord>(`${this.url}/${id}`)}
  saveAssessment(id:string,version:number,assessmentData:string){return this.http.patch<InterviewRecord>(`${this.url}/${id}/assessment`,{version,assessmentData})}
  complete(id:string){return this.http.patch<InterviewRecord>(`${this.url}/${id}/complete`,{})}
}
