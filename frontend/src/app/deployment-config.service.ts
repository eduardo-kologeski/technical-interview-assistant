import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';

export type DeploymentMode = 'SAAS' | 'ON_PREMISE';
export interface DeploymentConfiguration {
  mode: DeploymentMode;
  publicSignupEnabled: boolean;
  multiTenantEnabled: boolean;
  billingEnabled: boolean;
}

const SAFE_DEFAULTS: DeploymentConfiguration = {
  mode: 'ON_PREMISE', publicSignupEnabled: false, multiTenantEnabled: false, billingEnabled: false,
};

@Injectable({ providedIn: 'root' })
export class DeploymentConfigService {
  private readonly http = inject(HttpClient);
  readonly configuration = signal<DeploymentConfiguration>(SAFE_DEFAULTS);
  readonly loaded = signal(false);
  readonly setupRequired = signal(false);

  constructor() {
    forkJoin({
      configuration: this.http.get<DeploymentConfiguration>('/api/public/deployment'),
      setup: this.http.get<{ setupRequired: boolean }>('/api/public/setup'),
    }).subscribe({
      next: value => {
        this.configuration.set(value.configuration);
        this.setupRequired.set(value.setup.setupRequired);
        this.loaded.set(true);
      },
      error: () => this.loaded.set(true),
    });
  }

  publicSignupEnabled() { return this.configuration().publicSignupEnabled; }

  configureInstallation(request: { organizationName: string; adminName: string; adminEmail: string; password: string }) {
    return this.http.post<void>('/api/public/setup', request);
  }
}
