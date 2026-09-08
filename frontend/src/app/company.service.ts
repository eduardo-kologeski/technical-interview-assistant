import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { tap } from 'rxjs';
import { PageResponse } from './job-position.service';

export interface CompanySummary { id: string; name: string; slug: string; role: 'COMPANY_ADMIN' | 'INTERVIEWER' }
export interface InvitationResponse { id: string; email: string; role: string; expiresAt: string; deliveryStatus: string; developmentPath: string | null }
export interface InvitationSummary { id: string; email: string; role: string; status: 'PENDING'|'ACCEPTED'|'EXPIRED'|'REVOKED'; expiresAt: string; createdAt: string }
export interface CompanyMember { id: number; name: string; email: string; role: 'COMPANY_ADMIN' | 'INTERVIEWER' }

@Injectable({ providedIn: 'root' })
export class CompanyService {
  private readonly http = inject(HttpClient);
  readonly companies = signal<CompanySummary[]>([]);
  readonly activeCompanyId = signal(localStorage.getItem('tia.companyId'));
  readonly activeCompany = computed(() => this.companies().find(item => item.id === this.activeCompanyId()) || null);
  load() { return this.http.get<CompanySummary[]>('/api/companies').pipe(tap(items => {
    this.companies.set(items);
    const current = this.activeCompanyId();
    if (!current || !items.some(item => item.id === current)) {
      const interviewerCompanies = items.filter(item => item.role === 'INTERVIEWER');
      const preferred = items.length === 1 ? items[0] : interviewerCompanies.length === 1 ? interviewerCompanies[0] : null;
      this.select(preferred?.id || null);
    }
  })); }
  create(name: string) { return this.http.post<CompanySummary>('/api/companies', { name }).pipe(tap(item => this.select(item.id))); }
  invite(email: string, role: 'COMPANY_ADMIN'|'INTERVIEWER' = 'INTERVIEWER') { return this.http.post<InvitationResponse>('/api/companies/invitations', { email, role }); }
  invitations() { return this.http.get<InvitationSummary[]>('/api/companies/invitations'); }
  revokeInvitation(id: string) { return this.http.delete<void>(`/api/companies/invitations/${id}`); }
  resendInvitation(id: string) { return this.http.post<InvitationResponse>(`/api/companies/invitations/${id}/resend`, {}); }
  members() { return this.http.get<CompanyMember[]>('/api/companies/members'); }
  membersPage(page=0,size=20) { return this.http.get<PageResponse<CompanyMember>>('/api/companies/members/page',{params:{page,size}}); }
  invitationsPage(page=0,size=20) { return this.http.get<PageResponse<InvitationSummary>>('/api/companies/invitations/page',{params:{page,size}}); }
  rename(name:string) { return this.http.patch<CompanySummary>('/api/companies/current',{name}).pipe(tap(updated=>this.companies.update(items=>items.map(item=>item.id===updated.id?updated:item)))); }
  changeRole(userId:number,role:'COMPANY_ADMIN'|'INTERVIEWER') { return this.http.patch<CompanyMember>(`/api/companies/members/${userId}/role`,{role}); }
  revokeMember(userId:number) { return this.http.delete<void>(`/api/companies/members/${userId}`); }
  select(id: string | null) { this.activeCompanyId.set(id); if (id) localStorage.setItem('tia.companyId', id); else localStorage.removeItem('tia.companyId'); }
}
