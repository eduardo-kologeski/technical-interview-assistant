import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
export interface JobPosition { id: string; title: string; seniority: string; description: string; requirements: string[]; active: boolean; createdAt: string; createdBy: string; updatedAt?: string; updatedBy?: string }
export type JobPositionInput = Pick<JobPosition, 'title'|'seniority'|'description'|'requirements'>;
@Injectable({providedIn:'root'}) export class JobPositionService {
  private http=inject(HttpClient); private url='/api/job-positions';
  list(active?:boolean){return this.http.get<JobPosition[]>(this.url,{params:active===undefined?{}:{active}})} get(id:string){return this.http.get<JobPosition>(`${this.url}/${id}`)} create(value:JobPositionInput){return this.http.post<JobPosition>(this.url,value)}
  update(id:string,value:JobPositionInput){return this.http.put<JobPosition>(`${this.url}/${id}`,value)}
  deactivate(id:string){return this.http.patch<JobPosition>(`${this.url}/${id}/deactivate`,{})}
}
