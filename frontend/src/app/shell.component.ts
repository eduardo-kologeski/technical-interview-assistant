import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './auth.service';
import { CompanyService } from './company.service';
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `<a class="skip-link" href="#main-content">Pular para o conteúdo principal</a><header>
      <a class="brand" routerLink="/dashboard"><b>T</b><span>Technical Interview</span></a>
      <nav>
        <a routerLink="/dashboard" routerLinkActive="active" ariaCurrentWhenActive="page">Início</a
        >@if (companies.activeCompany()) {
          <a routerLink="/interviews" routerLinkActive="active" ariaCurrentWhenActive="page">Entrevistas</a>
          <a routerLink="/job-positions" routerLinkActive="active" ariaCurrentWhenActive="page">Vagas</a>
          @if (companies.activeCompany()?.role !== 'INTERVIEWER') {<a routerLink="/company" routerLinkActive="active" ariaCurrentWhenActive="page">Empresa</a>}
        } @else { <a routerLink="/dashboard" routerLinkActive="active">Minhas avaliações</a> }
      </nav>
      <div class="user">
        @if (companies.companies().length > 1) {
          <select aria-label="Espaço ativo" [value]="companies.activeCompanyId() || ''" (change)="selectCompany($event)">
            <option value="">Meu espaço</option>
            @for (company of companies.companies(); track company.id) { <option [value]="company.id">{{ company.name }}</option> }
          </select>
        } @else if (companies.activeCompany()) { <strong>{{ companies.activeCompany()?.name }}</strong> }
        @else { <strong>Meu espaço</strong> }
        <a class="profile-link" [routerLink]="companies.activeCompany() ? '/interviews' : '/dashboard'">{{ auth.user()?.name || 'Usuário' }}</a
        ><button (click)="logout()">Sair</button>
      </div>
    </header>
    <main id="main-content" tabindex="-1"><router-outlet /></main>`,
  styles: [
    `
      :host {
        display: block;
        min-height: 100vh;
        background: #f5f7fa;
        font-family: Inter, system-ui;
        color: #17202a;
      }
      header {
        min-height: 64px;
        display: flex;
        align-items: center;
        gap: 35px;
        padding: 0 5vw;
        background: #fff;
        border-bottom: 1px solid #e1e7ec;
      }
      .skip-link {
        position: fixed;
        z-index: 1000;
        top: 8px;
        left: 8px;
        padding: 10px 14px;
        border-radius: 6px;
        background: #fff;
        color: #0b516d;
        font-weight: 800;
        transform: translateY(-150%);
      }
      .skip-link:focus {
        transform: translateY(0);
      }
      .brand {
        display: flex;
        align-items: center;
        gap: 10px;
        color: #20313b;
        text-decoration: none;
        font-weight: 800;
      }
      .brand b {
        display: grid;
        place-items: center;
        width: 32px;
        height: 32px;
        border-radius: 8px;
        background: #165d7a;
        color: #fff;
      }
      nav {
        display: flex;
        gap: 20px;
        flex: 1;
      }
      nav a {
        color: #64727c;
        text-decoration: none;
        font-size: 13px;
        font-weight: 700;
      }
      nav a.active {
        color: #19756f;
      }
      .user {
        display: flex;
        align-items: center;
        gap: 12px;
        font-size: 12px;
        font-weight: 750;
      }
      .user button {
        border: 0;
        background: transparent;
        color: #a34a3f;
      }
      .profile-link{color:#30444d;text-decoration:none}.profile-link:hover{text-decoration:underline}
      .user select { max-width: 190px; padding: 7px 9px; border: 1px solid #d4dfe3; border-radius: 7px; background: #fff; color: #30444d; }
      .user strong { color: #176b66; font-size: 11px; white-space: nowrap; }
      @media (max-width: 900px) {
        header {
          gap: 15px;
          padding: 10px 14px;
          flex-wrap: wrap;
        }
        .brand span,
        .user span {
          display: none;
        }
        nav {
          order: 3;
          width: 100%;
          overflow-x: auto;
          gap: 16px;
        }
      }
    `,
  ],
})
export class ShellComponent {
  auth = inject(AuthService);
  companies = inject(CompanyService);
  private router = inject(Router);
  constructor() {
    const loadCompanies = () => { if (!this.companies.companies().length) this.companies.load().subscribe({ error: () => undefined }); };
    if (!this.auth.user()) this.auth.loadUser().subscribe({ next: loadCompanies, error: () => this.logout() });
    else loadCompanies();
  }
  selectCompany(event: Event) { this.companies.select((event.target as HTMLSelectElement).value); location.reload(); }
  logout() {
    this.auth.logout();
    this.router.navigateByUrl('/auth/login');
  }
}
