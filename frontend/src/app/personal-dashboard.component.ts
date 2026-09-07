import { Component, inject, signal } from '@angular/core';
import { AuthService } from './auth.service';
import { PersonalAssessmentService, PersonalAssessmentSummary } from './personal-assessment.service';

@Component({
  selector: 'app-personal-dashboard',
  template: `<section class="page">
    <p class="eyebrow">MEU ESPAÇO</p><h1>Olá, {{ firstName() }}</h1>
    <p class="intro">Organize suas avaliações técnicas e os pareceres produzidos a partir delas.</p>
    <section class="panel"><h2>Minhas avaliações</h2>
      @if (loading()) { <p>Carregando avaliações…</p> }
      @else if (error()) { <p class="error" role="alert">{{ error() }}</p> }
      @else if (!assessments().length) { <p>Nenhuma avaliação criada ainda.</p><small>A criação guiada será disponibilizada na próxima etapa do produto.</small> }
      @else { @for (assessment of assessments(); track assessment.id) {
        <article><div><strong>{{ assessment.title }}</strong><span>{{ assessment.personName }}</span></div><small>{{ label(assessment.status) }}</small></article>
      }}
    </section>
  </section>`,
  styles: [`.page{max-width:900px;margin:auto;padding:48px 28px}.eyebrow{color:#176b66;font-size:11px;font-weight:900;letter-spacing:.14em}h1{font-size:36px;margin:7px 0}.intro{color:#64747d}.panel{margin-top:28px;padding:24px;border:1px solid #dce5e8;border-radius:12px;background:#fff}.panel h2{margin-top:0}.panel>p,.panel>small{color:#718089}.error{color:#a44035!important}article{display:flex;justify-content:space-between;gap:16px;padding:15px 0;border-top:1px solid #edf1f3}article div{display:grid;gap:4px}article span,article small{color:#718089;font-size:12px}@media(max-width:560px){.page{padding:28px 14px}}`]
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
