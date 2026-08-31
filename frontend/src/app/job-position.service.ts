import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
export interface JobPosition { id: string; title: string; seniority: string; description: string; requirements: string[]; active: boolean; createdAt: string; createdBy: string; updatedAt?: string; updatedBy?: string; version: number }
export type JobPositionInput = Pick<JobPosition, 'title'|'seniority'|'description'|'requirements'>;
export interface PageResponse<T> { content:T[];page:number;size:number;totalElements:number;totalPages:number;first:boolean;last:boolean }
export interface InterviewKitItem { position?:number;criterion:string;question:string;weight:number;rubric:string }
export interface InterviewKitVersion { id:string;version:number;status:string;publishedAt?:string;items:InterviewKitItem[] }
export interface InterviewKit { id:string;vacancyId:string;name:string;versions:InterviewKitVersion[] }
@Injectable({providedIn:'root'}) export class JobPositionService {
  private http=inject(HttpClient); private url='/api/job-positions';
  list(active?:boolean){return this.listPage(active,0,100).pipe(map(result=>result.content))}
  listPage(active?:boolean,page=0,size=20,query=''){return this.http.get<PageResponse<JobPosition>>(this.url,{params:{...(active===undefined?{}:{active}),page,size,query}})} get(id:string){return this.http.get<JobPosition>(`${this.url}/${id}`)} create(value:JobPositionInput){return this.http.post<JobPosition>(this.url,value)}
  update(id:string,value:JobPositionInput,version:number){return this.http.put<JobPosition>(`${this.url}/${id}`,{...value,version})}
  deactivate(id:string,version:number){return this.http.patch<JobPosition>(`${this.url}/${id}/deactivate`,{version})}
  getKit(id:string){return this.http.get<InterviewKit>(`${this.url}/${id}/interview-kit`)}
  saveKitDraft(id:string,name:string,items:InterviewKitItem[]){return this.http.put<InterviewKitVersion>(`${this.url}/${id}/interview-kit/draft`,{name,items})}
  publishKit(id:string,versionId:string){return this.http.post<InterviewKitVersion>(`${this.url}/${id}/interview-kit/versions/${versionId}/publish`,{})}
}
