import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { tap } from 'rxjs';

export interface CompanySummary { id: string; name: string; slug: string; role: 'COMPANY_ADMIN' | 'INTERVIEWER' }
export interface InvitationResponse { id: string; email: string; role: string; expiresAt: string; path: string }
export interface CompanyMember { id: number; name: string; email: string; role: 'COMPANY_ADMIN' | 'INTERVIEWER' }

@Injectable({ providedIn: 'root' })
export class CompanyService {
  private readonly http = inject(HttpClient);
  readonly companies = signal<CompanySummary[]>([]);
  readonly activeCompanyId = signal(localStorage.getItem('tia.companyId'));
  readonly activeCompany = computed(() => this.companies().find(item => item.id === this.activeCompanyId()) || null);
  load() { return this.http.get<CompanySummary[]>('/api/companies').pipe(tap(items => { this.companies.set(items); const current = this.activeCompanyId(); if (!current || !items.some(item => item.id === current)) this.select(items.length === 1 ? items[0].id : null); })); }
  create(name: string) { return this.http.post<CompanySummary>('/api/companies', { name }).pipe(tap(item => this.select(item.id))); }
  invite(email: string) { return this.http.post<InvitationResponse>('/api/companies/invitations', { email, role: 'INTERVIEWER' }); }
  members() { return this.http.get<CompanyMember[]>('/api/companies/members'); }
  select(id: string | null) { this.activeCompanyId.set(id); if (id) localStorage.setItem('tia.companyId', id); else localStorage.removeItem('tia.companyId'); }
}
