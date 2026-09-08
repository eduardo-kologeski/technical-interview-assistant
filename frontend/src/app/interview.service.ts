import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { PageResponse } from './job-position.service';
export interface InterviewParticipant { id:number; name:string; status:string }
export interface InterviewRecord { id:string; vacancyId:string; candidateId:string; candidateName:string; vacancyTitleSnapshot:string; senioritySnapshot:string; clientCompany:string; requirementSnapshot:string[]; kitSnapshot:string; interviewerId:string; status:string; startedAt:string; version:number; assessmentData:string; participants?:InterviewParticipant[] }
@Injectable({providedIn:'root'}) export class InterviewService {
  private http=inject(HttpClient); private url='/api/interviews';
  create(candidateName:string,jobPositionId:string,clientCompany:string,participantUserIds:number[]=[]){return this.http.post<InterviewRecord>(this.url,{candidateName,jobPositionId,clientCompany,participantUserIds})}
  list(page=0,size=50,status?:string){return this.http.get<PageResponse<InterviewRecord>>(this.url,{params:{page,size,...(status?{status}:{})}})}
  get(id:string){return this.http.get<InterviewRecord>(`${this.url}/${id}/workspace`)}
  saveAssessment(id:string,version:number,assessmentData:string){return this.http.patch<InterviewRecord>(`${this.url}/${id}/workspace`,{version,assessmentData})}
  complete(id:string){return this.http.patch<InterviewRecord>(`${this.url}/${id}/workspace/complete`,{})}
}
