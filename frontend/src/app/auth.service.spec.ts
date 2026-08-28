import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';

describe('AuthService integrated session', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
    localStorage.clear();
  });

  it('uses only the application backend for login and current user', () => {
    auth.login('user@example.com', '123456', false).subscribe();
    http.expectOne('/api/auth/login').flush({ accessToken: 'access' });
    auth.loadUser().subscribe();
    http.expectOne('/api/users/me').flush({ id: 1, name: 'User', email: 'user@example.com' });
    expect(auth.user()?.email).toBe('user@example.com');
  });

  it('restores a persistent session by rotating the refresh token before loading the user', () => {
    localStorage.setItem('tia.refreshToken', 'refresh-1');
    let restored = false;
    auth.ensureSession().subscribe(value => restored = value);
    http.expectOne('/api/auth/refresh').flush({ accessToken: 'access-2', refreshToken: 'refresh-2' });
    http.expectOne('/api/users/me').flush({ id: 2, name: 'Persistent', email: 'persistent@example.com' });
    expect(restored).toBe(true);
    expect(sessionStorage.getItem('tia.accessToken')).toBe('access-2');
    expect(localStorage.getItem('tia.refreshToken')).toBe('refresh-2');
  });

  it('does not create persistence when remember me is disabled', () => {
    auth.login('short@example.com', '123456', false).subscribe();
    http.expectOne('/api/auth/login').flush({ accessToken: 'short-access', refreshToken: null });
    expect(sessionStorage.getItem('tia.accessToken')).toBe('short-access');
    expect(localStorage.getItem('tia.refreshToken')).toBeNull();
  });
});
