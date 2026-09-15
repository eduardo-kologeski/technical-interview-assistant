import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { AuthPageComponent } from './auth-page.component';
import { AuthService } from './auth.service';
import { CompanyService } from './company.service';
import { DeploymentConfigService } from './deployment-config.service';

describe('AuthPageComponent', () => {
  it('waits for the deployment configuration before enabling registration', async () => {
    const loaded = signal(false);
    await TestBed.configureTestingModule({
      imports: [AuthPageComponent],
      providers: [
        provideRouter([{ path: 'auth/register', component: AuthPageComponent }]),
        { provide: AuthService, useValue: {} },
        { provide: CompanyService, useValue: {} },
        {
          provide: DeploymentConfigService,
          useValue: { loaded, publicSignupEnabled: () => true },
        },
      ],
    }).compileComponents();
    await TestBed.inject(Router).navigateByUrl('/auth/register');

    const fixture = TestBed.createComponent(AuthPageComponent);
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('form > button') as HTMLButtonElement;

    expect(button.disabled).toBe(true);
    expect(button.textContent).toContain('Aguarde');

    loaded.set(true);
    fixture.detectChanges();

    expect(button.disabled).toBe(false);
    expect(button.textContent).toContain('Criar conta');
  });
});
