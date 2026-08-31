import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, shareReplay, switchMap, tap } from 'rxjs';

export interface AuthUser { id: number; name: string; email: string; role?: string }
export interface AuthTokens { accessToken: string; refreshToken?: string; expiresIn?: number }
export interface IdentityDelivery { status: string; developmentPath: string | null }

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api';
  private readonly userState = signal<AuthUser | null>(null);
  private refreshRequest?: Observable<string>;
  readonly user = this.userState.asReadonly();
  readonly authenticated = computed(() => !!this.accessToken());

  accessToken() { return sessionStorage.getItem('tia.accessToken'); }
  refreshToken() { return localStorage.getItem('tia.refreshToken'); }

  login(email: string, password: string, rememberMe: boolean) {
    this.clearCompanyContext();
    return this.http.post<AuthTokens>(`${this.apiUrl}/auth/login`, { email, password, rememberMe })
      .pipe(tap(tokens => this.store(tokens)), map(() => void 0));
  }

  register(name: string, email: string, password: string, rememberMe: boolean) {
    this.clearCompanyContext();
    return this.http.post<AuthTokens>(`${this.apiUrl}/auth/register`, { name, email, password, rememberMe })
      .pipe(tap(tokens => this.store(tokens)), switchMap(() => this.requestEmailVerification()));
  }

  requestEmailVerification() { return this.http.post<IdentityDelivery>(`${this.apiUrl}/auth/email-verification/request`, {}); }
  verifyEmail(token: string) { return this.http.post<void>(`${this.apiUrl}/auth/email-verification/confirm`, { token }); }
  forgotPassword(email: string) { return this.http.post<IdentityDelivery>(`${this.apiUrl}/auth/password/forgot`, { email }); }
  resetPassword(token: string, password: string) { return this.http.post<void>(`${this.apiUrl}/auth/password/reset`, { token, password }); }

  loadUser() {
    return this.http.get<AuthUser>(`${this.apiUrl}/users/me`).pipe(tap(user => this.userState.set(user)));
  }

  refresh(): Observable<string> {
    if (this.refreshRequest) return this.refreshRequest;
    const refreshToken = this.refreshToken();
    if (!refreshToken) throw new Error('Sessão expirada');
    this.refreshRequest = this.http.post<AuthTokens>(`${this.apiUrl}/auth/refresh`, { refreshToken }).pipe(
      tap(tokens => this.store(tokens)), map(tokens => tokens.accessToken),
      finalize(() => this.refreshRequest = undefined), shareReplay(1));
    return this.refreshRequest;
  }

  ensureSession(): Observable<boolean> {
    if (this.userState()) return of(true);
    if (this.accessToken()) {
      return this.loadUser().pipe(
        map(() => true),
        catchError(() => this.restoreWithRefresh())
      );
    }
    return this.restoreWithRefresh();
  }

  logout() {
    const refreshToken = this.refreshToken();
    const request = refreshToken ? this.http.post<void>(`${this.apiUrl}/auth/logout`, { refreshToken }) : null;
    request?.subscribe({ error: () => undefined });
    this.clear();
  }

  clear() {
    sessionStorage.removeItem('tia.accessToken');
    localStorage.removeItem('tia.refreshToken');
    this.clearCompanyContext();
    this.userState.set(null);
  }

  private clearCompanyContext() {
    localStorage.removeItem('tia.companyId');
  }

  private store(tokens: AuthTokens) {
    sessionStorage.setItem('tia.accessToken', tokens.accessToken);
    if (tokens.refreshToken) localStorage.setItem('tia.refreshToken', tokens.refreshToken);
    else localStorage.removeItem('tia.refreshToken');
  }

  private restoreWithRefresh(): Observable<boolean> {
    if (!this.refreshToken()) { this.clear(); return of(false); }
    return this.refresh().pipe(
      switchMap(() => this.loadUser()),
      map(() => true),
      catchError(() => { this.clear(); return of(false); })
    );
  }
}
