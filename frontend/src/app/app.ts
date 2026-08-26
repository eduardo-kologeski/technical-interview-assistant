import { CommonModule } from '@angular/common';
import { Component, computed, HostListener, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

type Importance = 'Obrigatória' | 'Desejável' | 'Complementar';
type Skill = { name: string; category: string; weight: number; importance: Importance; expected: number; score: number | null; skipped: boolean; comment: string };

@Component({ selector: 'app-root', imports: [CommonModule, FormsModule], templateUrl: './app.html', styleUrl: './app.scss' })
export class App {
  readonly started = signal(false);
  readonly reviewing = signal(false);
  readonly candidate = signal('');
  readonly role = signal('');
  readonly company = signal('');
  readonly interviewer = signal('');
  readonly seniority = signal('Pleno');
  readonly template = signal('Backend .NET');
  readonly recommendation = signal('Sem recomendação');
  readonly strengths = signal('');
  readonly concerns = signal('');
  readonly finalAssessment = signal('');
  readonly activeIndex = signal(0);
  readonly seconds = signal(1938);
  readonly running = signal(false);
  readonly saveState = signal<'Salvo' | 'Salvando...'>('Salvo');
  readonly quickNotes = signal('');
  readonly skills = signal<Skill[]>([
    { name: 'C# / .NET', category: 'Backend', weight: 5, importance: 'Obrigatória', expected: 4, score: null, skipped: false, comment: '' },
    { name: 'APIs REST', category: 'Backend', weight: 5, importance: 'Obrigatória', expected: 4, score: null, skipped: false, comment: '' },
    { name: 'SQL', category: 'Dados', weight: 4, importance: 'Obrigatória', expected: 3, score: null, skipped: false, comment: '' },
    { name: 'Angular', category: 'Frontend', weight: 3, importance: 'Desejável', expected: 3, score: null, skipped: false, comment: '' },
    { name: 'Testes automatizados', category: 'Qualidade', weight: 4, importance: 'Obrigatória', expected: 3, score: null, skipped: false, comment: '' },
    { name: 'Docker', category: 'DevOps', weight: 2, importance: 'Desejável', expected: 2, score: null, skipped: false, comment: '' },
  ]);
  readonly evaluated = computed(() => this.skills().filter(s => s.score !== null || s.skipped).length);
  readonly progress = computed(() => Math.round(this.evaluated() / this.skills().length * 100));
  readonly average = computed(() => { const scored = this.skills().filter(s => s.score !== null && !s.skipped); const weights = scored.reduce((sum, s) => sum + s.weight, 0); return weights ? (scored.reduce((sum, s) => sum + s.score! * s.weight, 0) / weights).toFixed(1) : '—'; });
  readonly formattedTime = computed(() => `${String(Math.floor(this.seconds() / 60)).padStart(2, '0')}:${String(this.seconds() % 60).padStart(2, '0')}`);
  private timer?: ReturnType<typeof setInterval>;
  private saveTimer?: ReturnType<typeof setTimeout>;

  startInterview() {
    if (!this.candidate().trim()) return;
    this.candidate.set(this.candidate().trim());
    this.started.set(true);
    this.seconds.set(0);
    this.running.set(true);
    this.timer = setInterval(() => this.seconds.update(v => v + 1), 1000);
  }

  finishInterview() {
    clearInterval(this.timer);
    this.running.set(false);
    this.finalAssessment.set(this.buildAssessmentDraft());
    this.reviewing.set(true);
  }

  backToInterview() { this.reviewing.set(false); }

  async exportPdf() {
    const { jsPDF } = await import('jspdf');
    const pdf = new jsPDF({ unit: 'mm', format: 'a4' });
    const left = 18;
    const width = 174;
    let y = 18;
    const ensureSpace = (needed: number) => { if (y + needed > 280) { pdf.addPage(); y = 18; } };
    const heading = (title: string) => { ensureSpace(15); pdf.setFillColor(22, 93, 122); pdf.rect(left, y, width, 8, 'F'); pdf.setTextColor(255); pdf.setFont('helvetica', 'bold'); pdf.setFontSize(10); pdf.text(title.toUpperCase(), left + 3, y + 5.5); pdf.setTextColor(35, 48, 58); y += 13; };
    const paragraph = (text: string) => { const lines = pdf.splitTextToSize(text || 'Não informado.', width); ensureSpace(lines.length * 5 + 4); pdf.setFont('helvetica', 'normal'); pdf.setFontSize(9); pdf.text(lines, left, y); y += lines.length * 5 + 4; };

    pdf.setTextColor(22, 93, 122); pdf.setFont('helvetica', 'bold'); pdf.setFontSize(19); pdf.text('PARECER TÉCNICO', left, y); y += 8;
    pdf.setDrawColor(39, 141, 131); pdf.setLineWidth(0.7); pdf.line(left, y, left + width, y); y += 9;
    pdf.setTextColor(35, 48, 58); pdf.setFontSize(10);
    const details = [`Candidato: ${this.candidate()}`, `Vaga: ${this.role() || 'Não informada'}`, `Senioridade: ${this.seniority()}`, `Empresa: ${this.company() || 'Não informada'}`, `Entrevistador: ${this.interviewer() || 'Não informado'}`, `Data: ${new Date().toLocaleDateString('pt-BR')}`];
    details.forEach(line => { pdf.setFont('helvetica', line.startsWith('Candidato') ? 'bold' : 'normal'); pdf.text(line, left, y); y += 5.5; }); y += 3;
    heading('Resumo técnico'); paragraph(this.finalAssessment());
    heading('Competências avaliadas');
    this.skills().forEach(skill => { ensureSpace(12); pdf.setFont('helvetica', 'bold'); pdf.text(`${skill.name}  ${skill.skipped ? 'N/A' : skill.score === null ? 'Não avaliado' : `${skill.score}/5`}`, left, y); pdf.setFont('helvetica', 'normal'); const comment = pdf.splitTextToSize(skill.comment || 'Sem comentário.', width - 4); pdf.text(comment, left + 4, y + 4.5); y += comment.length * 4.5 + 7; });
    heading('Resultado'); paragraph(`Média ponderada: ${this.average()} / 5\nRecomendação: ${this.recommendation()}`);
    heading('Pontos fortes'); paragraph(this.strengths());
    heading('Pontos de atenção'); paragraph(this.concerns());
    const pages = pdf.getNumberOfPages(); for (let page = 1; page <= pages; page++) { pdf.setPage(page); pdf.setFontSize(8); pdf.setTextColor(120); pdf.text(`Technical Interview Assistant · Página ${page} de ${pages}`, 105, 291, { align: 'center' }); }
    pdf.save(`parecer-${this.candidate().toLowerCase().replace(/[^a-z0-9]+/g, '-') || 'entrevista'}.pdf`);
  }

  private buildAssessmentDraft() {
    const scored = this.skills().filter(s => s.score !== null);
    if (!scored.length) return 'Nenhuma competência recebeu nota. Revise as anotações antes de concluir o parecer.';
    const strong = scored.filter(s => s.score! >= 4).map(s => s.name);
    const attention = scored.filter(s => s.score! <= 2).map(s => s.name);
    const parts = [`Foram avaliadas ${scored.length} competências, com média ponderada de ${this.average()} em 5.`];
    if (strong.length) parts.push(`Os melhores resultados registrados foram em ${strong.join(', ')}.`);
    if (attention.length) parts.push(`Os principais pontos de desenvolvimento registrados foram ${attention.join(', ')}.`);
    if (this.quickNotes().trim()) parts.push(`Anotações da entrevista: ${this.quickNotes().trim()}`);
    return parts.join(' ');
  }

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
