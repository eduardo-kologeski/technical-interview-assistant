import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { PageResponse } from './job-position.service';

export type InterviewStatus = 'CREATED'|'IN_PROGRESS'|'COMPLETED';
export interface OperationalDashboard {
  from:string; to:string;
  summary:{total:number;inProgress:number;completed:number;averageMinutes:number;completionRate:number};
  byStatus:{status:InterviewStatus;total:number}[];
  byVacancy:PageResponse<{vacancyId:string;vacancyTitle:string;total:number;completed:number}>;
  byInterviewer:PageResponse<{interviewerCode:string;label:string;total:number;completed:number}>;
}

@Injectable({providedIn:'root'})
export class AnalyticsService {
  private readonly http=inject(HttpClient);
  operational(filters:{from?:string;to?:string;status?:string;vacancyId?:string;vacancyPage?:number;interviewerPage?:number;size?:number}) {
    let params=new HttpParams();
    Object.entries(filters).forEach(([key,value])=>{if(value!==undefined&&value!=='')params=params.set(key,String(value))});
    return this.http.get<OperationalDashboard>('/api/analytics/operational',{params});
  }
}
