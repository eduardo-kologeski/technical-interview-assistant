import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_ORIGIN, apiOriginInterceptor } from './api-origin.interceptor';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { Router } from '@angular/router';
import { of } from 'rxjs';

describe('API origin routing', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  for (const origin of ['', 'https://api.example.test']) {
    it(`routes API requests with origin "${origin}" and preserves headers`, () => {
      TestBed.configureTestingModule({ providers: [
        provideHttpClient(withInterceptors([apiOriginInterceptor])), provideHttpClientTesting(),
        { provide: API_ORIGIN, useValue: origin },
      ] });
      TestBed.inject(HttpClient).get('/api/assessments?mine=true', {
        headers: { Authorization: 'Bearer test', 'X-Company-Id': 'test-company' },
      }).subscribe();
      const request = TestBed.inject(HttpTestingController).expectOne(`${origin}/api/assessments?mine=true`);
      expect(request.request.headers.get('Authorization')).toBe('Bearer test');
      expect(request.request.headers.get('X-Company-Id')).toBe('test-company');
      request.flush([]);
    });
  }

  it('does not rewrite unrelated requests', () => {
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(withInterceptors([apiOriginInterceptor])), provideHttpClientTesting(),
      { provide: API_ORIGIN, useValue: 'https://api.example.test' },
    ] });
    for (const url of ['/assets/example.json', 'https://other.example.test/api/test']) {
      TestBed.inject(HttpClient).get(url).subscribe();
      TestBed.inject(HttpTestingController).expectOne(url).flush({});
    }
  });

  it('retries on the public origin after refresh and keeps personal requests tenant-free', () => {
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(withInterceptors([authInterceptor, apiOriginInterceptor])), provideHttpClientTesting(),
      { provide: API_ORIGIN, useValue: 'https://api.example.test' },
      { provide: AuthService, useValue: {
        accessToken: () => 'expired', refreshToken: () => 'refresh', refresh: () => of('renewed'),
      } },
      { provide: Router, useValue: {} },
    ] });
    localStorage.setItem('tia.companyId', 'company');
    try {
      TestBed.inject(HttpClient).get('/api/assessments').subscribe();
      const http = TestBed.inject(HttpTestingController);
      const initial = http.expectOne('https://api.example.test/api/assessments');
      expect(initial.request.headers.has('X-Company-Id')).toBe(false);
      initial.flush({}, { status: 401, statusText: 'Unauthorized' });
      const retry = http.expectOne('https://api.example.test/api/assessments');
      expect(retry.request.headers.get('Authorization')).toBe('Bearer renewed');
      expect(retry.request.headers.has('X-Company-Id')).toBe(false);
      retry.flush([]);
      TestBed.inject(HttpClient).get('https://other.example.test/api/test').subscribe();
      const external = http.expectOne('https://other.example.test/api/test');
      expect(external.request.headers.has('Authorization')).toBe(false);
      expect(external.request.headers.has('X-Company-Id')).toBe(false);
      external.flush({});
    } finally { localStorage.removeItem('tia.companyId'); }
  });
});
