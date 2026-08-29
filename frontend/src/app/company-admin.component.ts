import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CompanyMember, CompanyService } from './company.service';

@Component({
  selector: 'app-company-admin',
  imports: [FormsModule],
  template: `<section class="page">
    <p class="eyebrow">ADMINISTRAÇÃO</p><h1>Empresa e entrevistadores</h1>
    <p class="intro">Cada vaga, entrevista e parecer pertence somente à empresa selecionada.</p>
    @if (isAdmin()) {
      <article><h2>Convidar entrevistador</h2>
        <p class="hint">Informe o e-mail e compartilhe o link. Ele direciona o profissional ao cadastro ou login.</p>
        <label>E-mail corporativo<input type="email" [(ngModel)]="email" placeholder="entrevistador@empresa.com.br"></label>
        <button (click)="invite()" [disabled]="sending() || !email.trim()">{{ sending() ? 'Gerando...' : 'Gerar link de convite' }}</button>
        @if (error()) { <p class="error">{{ error() }}</p> }
        @if (link()) { <div class="link"><span>Link válido por 7 dias e vinculado ao e-mail informado</span><input readonly [value]="link()"><button (click)="copy()">{{ copied() ? 'Copiado' : 'Copiar link' }}</button></div> }
      </article>
    }
    <article><h2>Entrevistadores da empresa</h2>
      @if (loading()) { <p class="hint">Carregando equipe...</p> }
      @else if (!members().length) { <p class="hint">Nenhum entrevistador cadastrado.</p> }
      @else { <div class="members">@for (member of members(); track member.id) { <div class="member"><span class="avatar">{{ initials(member) }}</span><span><strong>{{ member.name }}</strong><small>{{ member.email }}</small></span><em>{{ member.role === 'COMPANY_ADMIN' ? 'Gestor' : 'Entrevistador' }}</em></div> }</div> }
    </article>
  </section>`,
  styles: [`.page{max-width:820px;margin:auto;padding:42px 28px}.eyebrow{color:#278d83;font-size:11px;font-weight:900;letter-spacing:.14em}h1{margin:7px 0;font-size:34px}.intro,.hint{color:#64747d;line-height:1.5}article{display:grid;gap:14px;margin-top:24px;padding:26px;border:1px solid #dce5e8;border-radius:12px;background:#fff}h2{margin:0}label{display:grid;gap:7px;color:#52616b;font-size:12px;font-weight:800}input{box-sizing:border-box;width:100%;padding:12px;border:1px solid #d2dce1;border-radius:7px;font:inherit}button{padding:12px 15px;border:0;border-radius:8px;background:#176b66;color:#fff;font-weight:800}.link{display:grid;grid-template-columns:1fr auto;gap:8px;padding:15px;border-radius:9px;background:#eef7f5}.link span{grid-column:1/-1;color:#46706d;font-size:11px;font-weight:800}.link input{background:#fff}.error{color:#a44035}.members{display:grid}.member{display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:12px;padding:13px 0;border-top:1px solid #edf1f3}.avatar{display:grid;place-items:center;width:38px;height:38px;border-radius:50%;background:#e7f2f0;color:#176b66;font-weight:900}.member strong,.member small{display:block}.member small{margin-top:3px;color:#718089}.member em{padding:5px 8px;border-radius:999px;background:#f0f3f5;color:#53636b;font-size:11px;font-style:normal;font-weight:800}@media(max-width:560px){.page{padding:28px 14px}.link{grid-template-columns:1fr}.link button{width:100%}.member{grid-template-columns:auto 1fr}.member em{grid-column:2}}`],
})
export class CompanyAdminComponent {
  private companies = inject(CompanyService);
  email = '';
  readonly sending = signal(false);
  readonly link = signal('');
  readonly error = signal('');
  readonly copied = signal(false);
  readonly loading = signal(true);
  readonly members = signal<CompanyMember[]>([]);
  readonly isAdmin = () => this.companies.activeCompany()?.role === 'COMPANY_ADMIN';

  constructor() { this.companies.members().subscribe({ next: value => { this.members.set(value); this.loading.set(false); }, error: () => this.loading.set(false) }); }
  invite() { this.sending.set(true); this.error.set(''); this.companies.invite(this.email.trim()).subscribe({ next: value => { this.link.set(`${location.origin}${value.path}`); this.sending.set(false); }, error: error => { this.error.set(error.error?.detail || 'Não foi possível gerar o convite.'); this.sending.set(false); } }); }
  initials(member: CompanyMember) { return member.name.split(/\s+/).slice(0, 2).map(part => part[0]).join('').toUpperCase(); }
  async copy() { await navigator.clipboard.writeText(this.link()); this.copied.set(true); }
}
