import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
export interface JobPosition { id: string; title: string; seniority: string; description: string; requirements: string[]; active: boolean; createdAt: string; createdBy: string; updatedAt?: string; updatedBy?: string; version: number }
export type JobPositionInput = Pick<JobPosition, 'title'|'seniority'|'description'|'requirements'>;
export interface PageResponse<T> { content:T[];page:number;size:number;totalElements:number;totalPages:number;first:boolean;last:boolean }
@Injectable({providedIn:'root'}) export class JobPositionService {
  private http=inject(HttpClient); private url='/api/job-positions';
  list(active?:boolean){return this.listPage(active,0,100).pipe(map(result=>result.content))}
  listPage(active?:boolean,page=0,size=20,query=''){return this.http.get<PageResponse<JobPosition>>(this.url,{params:{...(active===undefined?{}:{active}),page,size,query}})} get(id:string){return this.http.get<JobPosition>(`${this.url}/${id}`)} create(value:JobPositionInput){return this.http.post<JobPosition>(this.url,value)}
  update(id:string,value:JobPositionInput,version:number){return this.http.put<JobPosition>(`${this.url}/${id}`,{...value,version})}
  deactivate(id:string,version:number){return this.http.patch<JobPosition>(`${this.url}/${id}/deactivate`,{version})}
}
