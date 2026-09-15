import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { DeploymentConfigService } from './deployment-config.service';
import { LandingPageComponent } from './landing-page.component';

describe('LandingPageComponent', () => {
  it('presents the individual flow while preserving company registration', async () => {
    const deployment = {
      setupRequired: signal(false),
      publicSignupEnabled: () => true,
    };
    await TestBed.configureTestingModule({
      imports: [LandingPageComponent],
      providers: [provideRouter([]), { provide: DeploymentConfigService, useValue: deployment }],
    }).compileComponents();

    const fixture = TestBed.createComponent(LandingPageComponent);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const links = Array.from(element.querySelectorAll<HTMLAnchorElement>('a'));

    expect(element.textContent).toContain('Defina os requisitos');
    expect(element.textContent).toContain('Registre evidências com atalhos');
    expect(element.textContent).toContain(
      'A revisão, a conclusão e a aprovação continuam sob sua responsabilidade.',
    );
    expect(
      links.find((link) => link.textContent?.trim() === 'Criar minha conta')?.getAttribute('href'),
    ).toBe('/auth/register');
    expect(
      links.find((link) => link.textContent?.trim() === 'Cadastrar empresa')?.getAttribute('href'),
    ).toBe('/auth/register?company=true');
  });
});
