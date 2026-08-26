import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, finalize, map, shareReplay, tap } from 'rxjs';

export interface AuthUser { id: number; name: string; email: string; role?: string }
export interface AuthTokens { accessToken: string; refreshToken?: string; expiresIn?: number }

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly authUrl = 'http://localhost:8080/api';
  private readonly userState = signal<AuthUser | null>(null);
  private refreshRequest?: Observable<string>;
  readonly user = this.userState.asReadonly();
  readonly authenticated = computed(() => !!this.accessToken());

  accessToken() { return sessionStorage.getItem('tia.accessToken'); }
  refreshToken() { return localStorage.getItem('tia.refreshToken'); }

  login(email: string, password: string, rememberMe: boolean) {
    return this.http.post<AuthTokens>(`${this.authUrl}/auth/login`, { email, password, rememberMe })
      .pipe(tap(tokens => this.store(tokens)), map(() => void 0));
  }

  register(name: string, email: string, password: string, rememberMe: boolean) {
    return this.http.post<AuthTokens>(`${this.authUrl}/auth/register`, { name, email, password, rememberMe })
      .pipe(tap(tokens => this.store(tokens)), map(() => void 0));
  }

  loadUser() {
    return this.http.get<AuthUser>(`${this.authUrl}/users/me`).pipe(tap(user => this.userState.set(user)));
  }

  refresh(): Observable<string> {
    if (this.refreshRequest) return this.refreshRequest;
    const refreshToken = this.refreshToken();
    if (!refreshToken) throw new Error('Sessão expirada');
    this.refreshRequest = this.http.post<AuthTokens>(`${this.authUrl}/auth/refresh`, { refreshToken }).pipe(
      tap(tokens => this.store(tokens)), map(tokens => tokens.accessToken),
      finalize(() => this.refreshRequest = undefined), shareReplay(1));
    return this.refreshRequest;
  }

  logout() {
    const refreshToken = this.refreshToken();
    const request = refreshToken ? this.http.post<void>(`${this.authUrl}/auth/logout`, { refreshToken }) : null;
    request?.subscribe({ error: () => undefined });
    this.clear();
  }

  clear() {
    sessionStorage.removeItem('tia.accessToken');
    localStorage.removeItem('tia.refreshToken');
    this.userState.set(null);
  }

  private store(tokens: AuthTokens) {
    sessionStorage.setItem('tia.accessToken', tokens.accessToken);
    if (tokens.refreshToken) localStorage.setItem('tia.refreshToken', tokens.refreshToken);
    else localStorage.removeItem('tia.refreshToken');
  }
}
