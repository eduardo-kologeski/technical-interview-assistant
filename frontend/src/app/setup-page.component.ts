import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { DeploymentConfigService } from './deployment-config.service';

@Component({
  selector: 'app-setup-page',
  imports: [FormsModule, RouterLink],
  template: `
    <main><form (ngSubmit)="submit()">
      <p class="eyebrow">CONFIGURAÇÃO INICIAL</p><h1>Prepare sua instalação</h1>
      <p>Crie a organização e o primeiro administrador. Este fluxo só pode ser executado uma vez.</p>
      <label>Nome da organização<input name="organization" [(ngModel)]="organizationName" required maxlength="160" autocomplete="organization"></label>
      <label>Nome do administrador<input name="adminName" [(ngModel)]="adminName" required maxlength="120" autocomplete="name"></label>
      <label>E-mail do administrador<input name="email" [(ngModel)]="adminEmail" required type="email" maxlength="180" autocomplete="email"></label>
      <label>Senha inicial<input name="password" [(ngModel)]="password" required minlength="8" maxlength="72" type="password" autocomplete="new-password"></label>
      <label>Confirmar senha<input name="confirm" [(ngModel)]="confirmation" required type="password" autocomplete="new-password"></label>
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
      <button [disabled]="saving()">{{ saving() ? 'Configurando...' : 'Concluir configuração' }}</button>
      <a routerLink="/auth/login">Voltar ao login</a>
    </form></main>`,
  styles: [`:host{display:grid;min-height:100vh;place-items:center;background:#eef4f4;color:#1d3038;font-family:Inter,system-ui,sans-serif}main{width:min(100% - 32px,520px)}form{display:grid;gap:16px;padding:36px;border:1px solid #d7e2e4;border-radius:16px;background:#fff;box-shadow:0 20px 60px #183e4615}h1{margin:0;font-size:32px}p{margin:0;color:#66777f;line-height:1.5}.eyebrow{color:#278d83;font-size:11px;font-weight:900;letter-spacing:.14em}label{display:grid;gap:7px;font-size:12px;font-weight:750}input{box-sizing:border-box;width:100%;padding:12px;border:1px solid #ccd9dd;border-radius:8px;font:inherit}button{padding:13px;border:0;border-radius:8px;background:#176b66;color:#fff;font-weight:800}button:disabled{opacity:.55}.error{padding:10px;border-radius:7px;background:#fff0ee;color:#a44035}a{text-align:center;color:#176b66;font-size:13px;font-weight:800}`],
})
export class SetupPageComponent {
  private readonly deployment = inject(DeploymentConfigService);
  private readonly router = inject(Router);
  readonly saving = signal(false); readonly error = signal('');
  organizationName = ''; adminName = ''; adminEmail = ''; password = ''; confirmation = '';

  submit() {
    this.error.set('');
    if (this.password !== this.confirmation) { this.error.set('As senhas não coincidem.'); return; }
    this.saving.set(true);
    this.deployment.configureInstallation({ organizationName: this.organizationName.trim(),
      adminName: this.adminName.trim(), adminEmail: this.adminEmail.trim(), password: this.password }).subscribe({
      next: () => this.router.navigateByUrl('/auth/login'),
      error: error => { this.error.set(error.error?.detail || 'Não foi possível concluir a configuração.'); this.saving.set(false); },
    });
  }
}
