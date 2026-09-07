import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { CompanyService } from './company.service';
import { authGuard } from './auth.guard';
import { authInterceptor } from './auth.interceptor';
import { PersonalDashboardComponent } from './personal-dashboard.component';
import { ShellComponent } from './shell.component';

describe('individual application entry', () => {
  afterEach(() => localStorage.clear());

  it('keeps an authenticated session valid when optional company loading fails', () => {
    TestBed.configureTestingModule({ providers: [
      provideRouter([]),
      { provide: AuthService, useValue: { ensureSession: () => of(true) } },
      { provide: CompanyService, useValue: { load: () => throwError(() => new Error('unavailable')) } },
    ] });
    let result: unknown;
    const outcome = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never)) as Observable<boolean>;
    outcome.subscribe(value => result = value);
    expect(result).toBe(true);
  });

  it('renders the personal shell without enterprise navigation when there are no memberships', async () => {
    const companies = signal<unknown[]>([]);
    const active = signal<unknown | null>(null);
    await TestBed.configureTestingModule({
      imports: [ShellComponent], providers: [provideRouter([]),
        { provide: AuthService, useValue: { user: signal({ id: 1, name: 'Pessoa', email: 'pessoa@test' }), logout: () => undefined } },
        { provide: CompanyService, useValue: { companies, activeCompany: active, activeCompanyId: signal(null), load: () => of([]), select: () => undefined } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ShellComponent); fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent || '';
    expect(text).toContain('Meu espaço');
    expect(text).toContain('Minhas avaliações');
    expect(text).not.toContain('Vagas');
    expect(text).not.toContain('Empresa');
  });

  it('loads an empty personal dashboard through the personal API', async () => {
    await TestBed.configureTestingModule({ imports: [PersonalDashboardComponent], providers: [
      provideHttpClient(), provideHttpClientTesting(),
      { provide: AuthService, useValue: { user: signal({ id: 1, name: 'Pessoa Teste', email: 'pessoa@test' }) } },
    ] }).compileComponents();
    const fixture = TestBed.createComponent(PersonalDashboardComponent);
    TestBed.inject(HttpTestingController).expectOne(request => request.url === '/api/assessments').flush({
      content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true,
    });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nenhuma avaliação criada ainda.');
  });

  it('never sends the selected company header to personal assessments', () => {
    localStorage.setItem('tia.companyId', 'company-1');
    TestBed.configureTestingModule({ providers: [
      provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting(),
      { provide: AuthService, useValue: { accessToken: () => 'access', refreshToken: () => null } },
    ] });
    TestBed.inject(HttpClient).get('/api/assessments').subscribe();
    const request = TestBed.inject(HttpTestingController).expectOne('/api/assessments');
    expect(request.request.headers.get('Authorization')).toBe('Bearer access');
    expect(request.request.headers.has('X-Company-Id')).toBe(false);
    request.flush({});
  });
});
