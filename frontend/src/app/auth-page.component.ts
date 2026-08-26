import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize, switchMap } from 'rxjs';
import { AuthService } from './auth.service';

@Component({
  selector: 'app-auth-page', imports: [FormsModule, RouterLink],
  template: `
    <main class="auth-page"><section class="auth-brand"><span class="logo">T</span><h1>Technical Interview Assistant</h1><p>Entrevistas técnicas mais consistentes, do briefing ao parecer.</p></section>
    <form (ngSubmit)="submit()" class="auth-card">
      <p class="eyebrow">{{ registerMode ? 'COMECE AGORA' : 'BEM-VINDO DE VOLTA' }}</p>
      <h2>{{ registerMode ? 'Criar conta' : 'Entrar' }}</h2>
      @if (registerMode) { <label>Nome<input name="name" [(ngModel)]="name" required autocomplete="name"></label> }
      <label>E-mail<input name="email" [(ngModel)]="email" required type="email" autocomplete="email" placeholder="seu@email.com"></label>
      <label>Senha<input name="password" [(ngModel)]="password" required minlength="6" type="password" autocomplete="current-password"></label>
      @if (registerMode) { <label>Confirmar senha<input name="confirm" [(ngModel)]="confirmPassword" required type="password" autocomplete="new-password"></label> }
      <label class="remember"><input name="remember" [(ngModel)]="rememberMe" type="checkbox"> Permanecer logado</label>
      @if (error()) { <p class="error">{{ error() }}</p> }
      <button [disabled]="loading()">{{ loading() ? 'Aguarde...' : (registerMode ? 'Criar conta' : 'Entrar') }}</button>
      <p class="switch">{{ registerMode ? 'Já possui uma conta?' : 'Não possui uma conta?' }}
        <a [routerLink]="registerMode ? '/auth/login' : '/auth/register'">{{ registerMode ? 'Entrar' : 'Criar conta' }}</a></p>
    </form></main>`,
  styles: [`:host{display:block;min-height:100vh;background:linear-gradient(135deg,#0e4057,#19756f)}.auth-page{min-height:100vh;display:grid;grid-template-columns:1.1fr minmax(340px,470px);align-items:center;gap:8vw;max-width:1100px;margin:auto;padding:32px}.auth-brand{color:#fff}.logo{display:grid;place-items:center;width:50px;height:50px;border-radius:14px;background:#fff;color:#165d7a;font-size:25px;font-weight:900}.auth-brand h1{font-size:43px;line-height:1.05;max-width:570px;margin:25px 0 15px}.auth-brand p{font-size:18px;color:#cfe4e4}.auth-card{display:grid;gap:16px;padding:36px;background:#fff;border-radius:18px;box-shadow:0 25px 70px #06293466}.auth-card h2{font-size:28px;margin:-8px 0 7px}.eyebrow{margin:0;color:#278d83;font-size:11px;font-weight:900;letter-spacing:.14em}.auth-card label{display:grid;gap:7px;color:#46555f;font-size:12px;font-weight:750}.auth-card input{box-sizing:border-box;width:100%;padding:12px;border:1px solid #d3dde3;border-radius:8px;font:inherit}.auth-card input:focus{outline:0;border-color:#278d83;box-shadow:0 0 0 3px #278d8320}.auth-card .remember{display:flex;align-items:center;gap:8px}.remember input{width:auto}.auth-card button{padding:13px;border:0;border-radius:8px;background:#165d7a;color:#fff;font-weight:800}.auth-card button:disabled{opacity:.55}.error{margin:0;padding:10px;border-radius:7px;background:#fff0ee;color:#a44035;font-size:12px}.switch{text-align:center;color:#66747d;font-size:13px}.switch a{color:#19756f;font-weight:800}@media(max-width:760px){.auth-page{grid-template-columns:1fr}.auth-brand{display:none}.auth-card{padding:25px}}`]
})
export class AuthPageComponent {
  private readonly auth = inject(AuthService); private readonly router = inject(Router);
  readonly registerMode = this.router.url.includes('register'); readonly loading = signal(false); readonly error = signal('');
  name = ''; email = ''; password = ''; confirmPassword = ''; rememberMe = false;
  submit() {
    this.error.set('');
    if (this.registerMode && this.password !== this.confirmPassword) { this.error.set('As senhas não coincidem.'); return; }
    if (this.registerMode && !this.name.trim()) { this.error.set('Informe seu nome.'); return; }
    this.loading.set(true);
    const request = this.registerMode ? this.auth.register(this.name.trim(), this.email.trim(), this.password, this.rememberMe) : this.auth.login(this.email.trim(), this.password, this.rememberMe);
    request.pipe(switchMap(() => this.auth.loadUser()), finalize(() => this.loading.set(false))).subscribe({
      next: () => this.router.navigateByUrl('/dashboard'),
      error: error => this.error.set(this.registerMode && error.status === 400 ? 'Este e-mail já está cadastrado ou os dados são inválidos.' : 'E-mail ou senha inválidos.')
    });
  }
}
