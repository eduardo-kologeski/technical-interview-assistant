import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { DeploymentConfigService } from './deployment-config.service';

describe('DeploymentConfigService', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }));

  it('fails closed until capabilities are loaded', () => {
    const service = TestBed.inject(DeploymentConfigService);
    expect(service.publicSignupEnabled()).toBe(false);
    TestBed.inject(HttpTestingController).expectOne('/api/public/deployment').flush({
      mode: 'SAAS', publicSignupEnabled: true, multiTenantEnabled: true, billingEnabled: true,
    });
    TestBed.inject(HttpTestingController).expectOne('/api/public/setup').flush({ setupRequired: false });
    expect(service.publicSignupEnabled()).toBe(true);
  });

  it('keeps public signup disabled for on-premises', () => {
    const service = TestBed.inject(DeploymentConfigService);
    TestBed.inject(HttpTestingController).expectOne('/api/public/deployment').flush({
      mode: 'ON_PREMISE', publicSignupEnabled: false, multiTenantEnabled: false, billingEnabled: false,
    });
    TestBed.inject(HttpTestingController).expectOne('/api/public/setup').flush({ setupRequired: true });
    expect(service.publicSignupEnabled()).toBe(false);
    expect(service.setupRequired()).toBe(true);
  });
});
