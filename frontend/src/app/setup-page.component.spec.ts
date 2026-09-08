import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';
import { SetupPageComponent } from './setup-page.component';

describe('SetupPageComponent', () => {
  beforeEach(() => TestBed.configureTestingModule({
    imports: [SetupPageComponent],
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
  }));

  it('validates password confirmation before submitting', () => {
    const component = TestBed.createComponent(SetupPageComponent).componentInstance;
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/public/deployment').flush({ mode: 'ON_PREMISE', publicSignupEnabled: false,
      multiTenantEnabled: false, billingEnabled: false });
    http.expectOne('/api/public/setup').flush({ setupRequired: true });
    component.password = 'strong-password'; component.confirmation = 'different-password';
    component.submit();
    expect(component.error()).toContain('não coincidem');
    http.expectNone(request => request.method === 'POST');
  });

  it('submits the initial organization and administrator', () => {
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const component = TestBed.createComponent(SetupPageComponent).componentInstance;
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/public/deployment').flush({ mode: 'ON_PREMISE', publicSignupEnabled: false,
      multiTenantEnabled: false, billingEnabled: false });
    http.expectOne('/api/public/setup').flush({ setupRequired: true });
    component.organizationName = ' Acme '; component.adminName = ' Admin ';
    component.adminEmail = ' admin@acme.test '; component.password = component.confirmation = 'strong-password';
    component.submit();
    const request = http.expectOne('/api/public/setup');
    expect(request.request.body).toEqual({ organizationName: 'Acme', adminName: 'Admin',
      adminEmail: 'admin@acme.test', password: 'strong-password' });
    request.flush(null);
  });
});
