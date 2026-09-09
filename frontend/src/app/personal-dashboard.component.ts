import { Component, inject, signal } from '@angular/core';
import { AuthService } from './auth.service';
import { PersonalAssessmentService, PersonalAssessmentSummary } from './personal-assessment.service';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-personal-dashboard',
  imports: [RouterLink, FormsModule],
  template: `<section class="page">
    <p class="eyebrow">MEU ESPAÇO</p><h1>Olá, {{ firstName() }}</h1>
    <p class="intro">Organize suas avaliações técnicas e os pareceres produzidos a partir delas.</p>
    <section class="panel"><div class="panel-title"><h2>Minhas avaliações</h2><a routerLink="/assessments/new">+ Criar avaliação</a></div>
      @if (loading()) { <p>Carregando avaliações…</p> }
      @else if (error()) { <p class="error" role="alert">{{ error() }}</p> }
      @else if (!assessments().length) { <p>Nenhuma avaliação criada ainda.</p><small>Crie a primeira avaliação para definir critérios e registrar evidências.</small> }
      @else { @for (assessment of assessments(); track assessment.id) {
        <div class="assessment"><a [routerLink]="['/assessments',assessment.id]"><strong>{{ assessment.title }}</strong><span>{{ assessment.personName }}</span></a><small>{{ label(assessment.status) }}</small><div class="row-actions"><button type="button" (click)="reuse(assessment)">Reutilizar requisitos</button><button type="button" (click)="remove(assessment)">Excluir</button></div></div>
      }}
    </section>
    <section class="danger"><h2>Excluir minha conta</h2><p>Remove permanentemente suas avaliações pessoais, minutas, perfil e sessões. Sua autoria em registros empresariais será anonimizada.</p>
      @if(!showAccountDeletion()){<button type="button" (click)="showAccountDeletion.set(true)">Iniciar exclusão da conta</button>}
      @else{<label>Confirme com sua senha atual<input type="password" [(ngModel)]="deletionPassword" autocomplete="current-password"></label>@if(deletionError()){<p class="error" role="alert">{{deletionError()}}</p>}<div><button type="button" (click)="showAccountDeletion.set(false);deletionPassword=''">Cancelar</button><button type="button" class="delete-account" (click)="deleteAccount()" [disabled]="deletingAccount()||deletionPassword.length<8">{{deletingAccount()?'Excluindo…':'Excluir conta permanentemente'}}</button></div>}
    </section>
  </section>`,
  styles: [`.page{max-width:900px;margin:auto;padding:48px 28px}.eyebrow{color:#176b66;font-size:11px;font-weight:900;letter-spacing:.14em}h1{font-size:36px;margin:7px 0}.intro{color:#64747d}.panel{margin-top:28px;padding:24px;border:1px solid #dce5e8;border-radius:12px;background:#fff}.panel-title{display:flex;align-items:center;justify-content:space-between;gap:15px}.panel h2{margin-top:0}.panel-title a{padding:10px 13px;border-radius:8px;background:#176b66;color:#fff;text-decoration:none;font-size:12px;font-weight:800}.panel>p,.panel>small{color:#718089}.error{color:#a44035!important}.assessment{display:grid;grid-template-columns:1fr auto auto;align-items:center;gap:16px;padding:15px 0;border-top:1px solid #edf1f3}.assessment>a{display:grid;gap:4px;color:inherit;text-decoration:none}.assessment>a:hover strong{color:#176b66}.assessment span,.assessment small{color:#718089;font-size:12px}.row-actions{display:flex;gap:7px}.assessment button,.danger button{border:0;background:transparent;color:#a44035;font-weight:750}.assessment .row-actions button:first-child{color:#176b66}.danger{margin-top:28px;padding:22px;border:1px solid #edcbc7;border-radius:12px;background:#fff}.danger h2{margin-top:0;font-size:18px;color:#8e3d35}.danger p{color:#6e777c}.danger label{display:grid;gap:6px;max-width:380px;font-size:12px;font-weight:800}.danger input{padding:10px;border:1px solid #d6c2bf;border-radius:7px}.danger>div{display:flex;gap:10px;margin-top:14px}.danger .delete-account{padding:10px 13px;border-radius:7px;background:#a44035;color:#fff}.danger button:disabled{opacity:.45}@media(max-width:560px){.page{padding:28px 14px}.panel-title{align-items:flex-start;flex-direction:column}.assessment{grid-template-columns:1fr}.assessment small{display:none}.row-actions{justify-content:space-between}}`]
})
export class PersonalDashboardComponent {
  private readonly auth = inject(AuthService);
  private readonly service = inject(PersonalAssessmentService);
  private readonly router = inject(Router);
  readonly assessments = signal<PersonalAssessmentSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly showAccountDeletion = signal(false); readonly deletingAccount = signal(false); readonly deletionError = signal('');
  deletionPassword = '';
  constructor() { this.service.list().subscribe({
    next: page => { this.assessments.set(page.content); this.loading.set(false); },
    error: () => { this.error.set('Não foi possível carregar suas avaliações.'); this.loading.set(false); },
  }); }
  firstName() { return this.auth.user()?.name.split(' ')[0] || 'bem-vindo'; }
  label(status: string) { return status === 'CREATED' ? 'Rascunho' : status === 'IN_PROGRESS' ? 'Em andamento' : 'Concluída'; }
  remove(assessment: PersonalAssessmentSummary) {
    if (!confirm(`Excluir permanentemente a avaliação “${assessment.title}” e sua minuta?`)) return;
    this.service.delete(assessment.id).subscribe({
      next: () => this.assessments.update(items => items.filter(item => item.id !== assessment.id)),
      error: () => this.error.set('Não foi possível excluir a avaliação.'),
    });
  }
  reuse(assessment: PersonalAssessmentSummary) {
    const personName = prompt('Nome da pessoa para a nova avaliação:')?.trim();
    if (!personName) return;
    this.service.copyRequirements(assessment.id, { title: assessment.title, personName, context: assessment.context || '' }).subscribe({
      next: created => this.router.navigate(['/assessments', created.id]),
      error: () => this.error.set('Não foi possível reutilizar os requisitos desta avaliação.'),
    });
  }
  deleteAccount() {
    if (this.deletionPassword.length < 8 || this.deletingAccount()) return;
    if (!confirm('Esta ação exclui permanentemente seus dados pessoais. Deseja continuar?')) return;
    this.deletingAccount.set(true); this.deletionError.set('');
    this.auth.deleteAccount(this.deletionPassword).subscribe({
      next: () => this.router.navigateByUrl('/'),
      error: error => { this.deletingAccount.set(false); this.deletionError.set(error.status === 401 ? 'Senha atual inválida.' : 'Não foi possível excluir a conta.'); },
    });
  }
}
