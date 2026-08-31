import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';

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

  constructor() {
    this.http.get<DeploymentConfiguration>('/api/public/deployment').subscribe({
      next: value => { this.configuration.set(value); this.loaded.set(true); },
      error: () => this.loaded.set(true),
    });
  }

  publicSignupEnabled() { return this.configuration().publicSignupEnabled; }
}
