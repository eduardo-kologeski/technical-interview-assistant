import { CommonModule } from '@angular/common';
import { Component, computed, HostListener, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

type Importance = 'Obrigatória' | 'Desejável' | 'Complementar';
type Skill = { name: string; category: string; weight: number; importance: Importance; expected: number; score: number | null; skipped: boolean; comment: string };

@Component({ selector: 'app-root', imports: [CommonModule, FormsModule], templateUrl: './app.html', styleUrl: './app.scss' })
export class App {
  readonly candidate = signal('Emiliano da Silveira');
  readonly role = signal('Desenvolvedor Fullstack Pleno');
  readonly activeIndex = signal(0);
  readonly seconds = signal(1938);
  readonly running = signal(false);
  readonly saveState = signal<'Salvo' | 'Salvando...'>('Salvo');
  readonly quickNotes = signal('Boa comunicação e experiência em contextos financeiros.');
  readonly skills = signal<Skill[]>([
    { name: 'C# / .NET', category: 'Backend', weight: 5, importance: 'Obrigatória', expected: 4, score: 3, skipped: false, comment: 'Experiência recente em APIs e soluções backend.' },
    { name: 'APIs REST', category: 'Backend', weight: 5, importance: 'Obrigatória', expected: 4, score: 4, skipped: false, comment: 'Boa compreensão de arquitetura e documentação.' },
    { name: 'SQL', category: 'Dados', weight: 4, importance: 'Obrigatória', expected: 3, score: 4, skipped: false, comment: 'Experiência com SQL Server e PostgreSQL.' },
    { name: 'Angular', category: 'Frontend', weight: 3, importance: 'Desejável', expected: 3, score: 4, skipped: false, comment: 'Boa base de frontend e TypeScript.' },
    { name: 'Testes automatizados', category: 'Qualidade', weight: 4, importance: 'Obrigatória', expected: 3, score: null, skipped: false, comment: '' },
    { name: 'Docker', category: 'DevOps', weight: 2, importance: 'Desejável', expected: 2, score: null, skipped: false, comment: '' },
  ]);
  readonly evaluated = computed(() => this.skills().filter(s => s.score !== null || s.skipped).length);
  readonly progress = computed(() => Math.round(this.evaluated() / this.skills().length * 100));
  readonly average = computed(() => { const scored = this.skills().filter(s => s.score !== null && !s.skipped); const weights = scored.reduce((sum, s) => sum + s.weight, 0); return weights ? (scored.reduce((sum, s) => sum + s.score! * s.weight, 0) / weights).toFixed(1) : '—'; });
  readonly formattedTime = computed(() => `${String(Math.floor(this.seconds() / 60)).padStart(2, '0')}:${String(this.seconds() % 60).padStart(2, '0')}`);
  private timer?: ReturnType<typeof setInterval>;
  private saveTimer?: ReturnType<typeof setTimeout>;

  setScore(index: number, score: number) { this.patchSkill(index, { score, skipped: false }); }
  setNotEvaluated(index: number) { this.patchSkill(index, { score: null, skipped: true }); }
  updateComment(index: number, comment: string) { this.patchSkill(index, { comment }); }
  updateNotes(notes: string) { this.quickNotes.set(notes); this.markSaving(); }
  select(index: number) { this.activeIndex.set(index); }
  toggleTimer() { this.running.update(v => !v); if (this.running()) this.timer = setInterval(() => this.seconds.update(v => v + 1), 1000); else clearInterval(this.timer); }

  @HostListener('window:keydown', ['$event'])
  handleShortcut(event: KeyboardEvent) {
    if ((event.target as HTMLElement).matches('input, textarea')) return;
    if (/^[0-5]$/.test(event.key)) this.setScore(this.activeIndex(), Number(event.key));
    if (event.key.toLowerCase() === 'n') this.setNotEvaluated(this.activeIndex());
    if (event.key === 'ArrowDown') this.activeIndex.update(i => Math.min(i + 1, this.skills().length - 1));
    if (event.key === 'ArrowUp') this.activeIndex.update(i => Math.max(i - 1, 0));
  }
  private patchSkill(index: number, patch: Partial<Skill>) { this.skills.update(items => items.map((item, i) => i === index ? { ...item, ...patch } : item)); this.markSaving(); }
  private markSaving() { this.saveState.set('Salvando...'); clearTimeout(this.saveTimer); this.saveTimer = setTimeout(() => this.saveState.set('Salvo'), 450); }
}
