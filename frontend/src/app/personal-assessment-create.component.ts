import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { PersonalAssessmentService } from './personal-assessment.service';

@Component({
  selector: 'app-personal-assessment-create',
  imports: [FormsModule, RouterLink],
  template: `<section class="page">
    <a routerLink="/dashboard" class="back">← Minhas avaliações</a>
    <p class="eyebrow">NOVA AVALIAÇÃO</p><h1>Organize a avaliação técnica</h1>
    <p class="intro">Comece com o contexto essencial. Critérios e evidências serão registrados na próxima etapa.</p>
    <form (ngSubmit)="create()">
      <label>Título da avaliação *<input name="title" [(ngModel)]="title" maxlength="160" required placeholder="Ex.: Avaliação técnica de Java"></label>
      <label>Nome da pessoa avaliada *<input name="personName" [(ngModel)]="personName" maxlength="160" required autocomplete="off"></label>
      <label>Contexto ou objetivo<textarea name="context" [(ngModel)]="context" maxlength="5000" rows="5" placeholder="Ex.: avaliação de conhecimentos para desenvolvimento profissional"></textarea></label>
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
      <div class="actions"><a routerLink="/dashboard">Cancelar</a><button [disabled]="saving() || !title.trim() || !personName.trim()">{{ saving() ? 'Criando…' : 'Criar avaliação' }}</button></div>
    </form>
  </section>`,
  styles: [`:host{display:block}.page{max-width:760px;margin:auto;padding:42px 24px}.back{color:#176b66;text-decoration:none;font-size:12px;font-weight:800}.eyebrow{margin:32px 0 5px;color:#278d83;font-size:11px;font-weight:900;letter-spacing:.14em}h1{margin:0;font-size:34px}.intro{color:#65757d;line-height:1.6}form{display:grid;gap:19px;margin-top:28px;padding:26px;border:1px solid #dbe5e8;border-radius:12px;background:#fff}label{display:grid;gap:8px;color:#40525b;font-size:12px;font-weight:800}input,textarea{width:100%;padding:12px;border:1px solid #ccd9de;border-radius:8px;font:inherit;box-sizing:border-box}textarea{resize:vertical}.actions{display:flex;align-items:center;justify-content:flex-end;gap:14px}.actions a{color:#596a73;text-decoration:none}.actions button{padding:12px 17px;border:0;border-radius:8px;background:#176b66;color:#fff;font-weight:850}.actions button:disabled{opacity:.45}.error{padding:11px;border-radius:8px;background:#fff0ee;color:#a44035}@media(max-width:560px){.page{padding:25px 14px}form{padding:19px}h1{font-size:29px}}`],
})
export class PersonalAssessmentCreateComponent {
  private readonly service = inject(PersonalAssessmentService);
  private readonly router = inject(Router);
  title = '';
  personName = '';
  context = '';
  readonly saving = signal(false);
  readonly error = signal('');

  create() {
    if (this.saving() || !this.title.trim() || !this.personName.trim()) return;
    this.saving.set(true); this.error.set('');
    this.service.create({ title: this.title.trim(), personName: this.personName.trim(), context: this.context.trim() })
      .subscribe({
        next: assessment => this.router.navigate(['/assessments', assessment.id]),
        error: () => { this.saving.set(false); this.error.set('Não foi possível criar a avaliação. Tente novamente.'); },
      });
  }
}
