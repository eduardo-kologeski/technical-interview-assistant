import { Component, inject, signal } from '@angular/core';
import { AuthService } from './auth.service';
import { PersonalAssessmentService, PersonalAssessmentSummary } from './personal-assessment.service';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-personal-dashboard',
  imports: [RouterLink],
  template: `<section class="page">
    <p class="eyebrow">MEU ESPAÇO</p><h1>Olá, {{ firstName() }}</h1>
    <p class="intro">Organize suas avaliações técnicas e os pareceres produzidos a partir delas.</p>
    <section class="panel"><div class="panel-title"><h2>Minhas avaliações</h2><a routerLink="/assessments/new">+ Criar avaliação</a></div>
      @if (loading()) { <p>Carregando avaliações…</p> }
      @else if (error()) { <p class="error" role="alert">{{ error() }}</p> }
      @else if (!assessments().length) { <p>Nenhuma avaliação criada ainda.</p><small>Crie a primeira avaliação para definir critérios e registrar evidências.</small> }
      @else { @for (assessment of assessments(); track assessment.id) {
        <a class="assessment" [routerLink]="['/assessments',assessment.id]"><div><strong>{{ assessment.title }}</strong><span>{{ assessment.personName }}</span></div><small>{{ label(assessment.status) }}</small></a>
      }}
    </section>
  </section>`,
  styles: [`.page{max-width:900px;margin:auto;padding:48px 28px}.eyebrow{color:#176b66;font-size:11px;font-weight:900;letter-spacing:.14em}h1{font-size:36px;margin:7px 0}.intro{color:#64747d}.panel{margin-top:28px;padding:24px;border:1px solid #dce5e8;border-radius:12px;background:#fff}.panel-title{display:flex;align-items:center;justify-content:space-between;gap:15px}.panel h2{margin-top:0}.panel-title a{padding:10px 13px;border-radius:8px;background:#176b66;color:#fff;text-decoration:none;font-size:12px;font-weight:800}.panel>p,.panel>small{color:#718089}.error{color:#a44035!important}.assessment{display:flex;justify-content:space-between;gap:16px;padding:15px 0;border-top:1px solid #edf1f3;color:inherit;text-decoration:none}.assessment:hover strong{color:#176b66}.assessment div{display:grid;gap:4px}.assessment span,.assessment small{color:#718089;font-size:12px}@media(max-width:560px){.page{padding:28px 14px}.panel-title{align-items:flex-start;flex-direction:column}}`]
})
export class PersonalDashboardComponent {
  private readonly auth = inject(AuthService);
  private readonly service = inject(PersonalAssessmentService);
  readonly assessments = signal<PersonalAssessmentSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  constructor() { this.service.list().subscribe({
    next: page => { this.assessments.set(page.content); this.loading.set(false); },
    error: () => { this.error.set('Não foi possível carregar suas avaliações.'); this.loading.set(false); },
  }); }
  firstName() { return this.auth.user()?.name.split(' ')[0] || 'bem-vindo'; }
  label(status: string) { return status === 'CREATED' ? 'Rascunho' : status === 'IN_PROGRESS' ? 'Em andamento' : 'Concluída'; }
}
