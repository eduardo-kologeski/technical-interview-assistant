import { CommonModule } from '@angular/common';
import { Component, computed, HostListener, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

type Importance = 'Obrigatória' | 'Desejável' | 'Complementar';
type Skill = { name: string; category: string; weight: number; importance: Importance; expected: number; score: number | null; skipped: boolean; comment: string };
type TechnologyType = 'LANGUAGE' | 'FRAMEWORK' | 'OTHER';
type Technology = { name: string; type: TechnologyType };

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
  readonly technologySearch = signal('');
  readonly selectedTechnologies = signal<Technology[]>([]);
  readonly technologyCatalog: Technology[] = [
    ...['Java', 'C#', 'JavaScript', 'TypeScript', 'Python', 'PHP', 'SQL', 'Go', 'Kotlin', 'C', 'C++', 'Ruby', 'Dart', 'Swift', 'Rust', 'Scala', 'PL/SQL', 'T-SQL'].map(name => ({ name, type: 'LANGUAGE' as const })),
    ...['Spring', 'Spring Boot', '.NET', 'ASP.NET Core', 'Node.js', 'NestJS', 'Express', 'Django', 'Flask', 'FastAPI', 'Laravel', 'Symfony', 'Ruby on Rails', 'Quarkus', 'Micronaut', 'Angular', 'React', 'Vue.js', 'Next.js', 'Nuxt', 'Svelte', 'Flutter', 'React Native', 'Ionic'].map(name => ({ name, type: 'FRAMEWORK' as const })),
  ];
  readonly quickTechnologySuggestions = ['Java', 'C#', 'JavaScript', 'TypeScript', 'Python', 'PHP', 'SQL', 'Spring Boot', '.NET', 'Angular', 'React', 'Node.js', 'Laravel'];
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
  readonly technologyResults = computed(() => {
    const query = this.normalizeTechnology(this.technologySearch());
    return query ? this.technologyCatalog.filter(item => this.normalizeTechnology(item.name).includes(query) && !this.hasTechnology(item.name)).slice(0, 8) : [];
  });
  readonly canAddCustomTechnology = computed(() => {
    const value = this.technologySearch().trim();
    return !!value && !this.technologyCatalog.some(item => this.normalizeTechnology(item.name) === this.normalizeTechnology(value)) && !this.hasTechnology(value);
  });
  readonly complementaryTechnologiesForReport = computed(() => {
    const evaluated = new Set(this.skills().map(skill => this.normalizeTechnology(skill.name)));
    return this.selectedTechnologies().filter(technology => !evaluated.has(this.normalizeTechnology(technology.name)));
  });
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
    const left = 16, width = 178, rightColumn = 116;
    let y = 16;
    const border = () => { pdf.setDrawColor(125); pdf.setLineWidth(0.25); };
    const ensureSpace = (needed: number) => { if (y + needed > 280) { pdf.addPage(); y = 16; } };
    const textLines = (text: string, maxWidth = width - 8) => pdf.splitTextToSize(text.trim() || 'Não informado.', maxWidth);
    const section = (title: string) => {
      ensureSpace(14); border(); pdf.setFillColor(190, 190, 190); pdf.rect(left, y, width, 8, 'FD');
      pdf.setTextColor(15); pdf.setFont('helvetica', 'normal'); pdf.setFontSize(11); pdf.text(title, left + 2, y + 5.5); y += 8;
    };
    const paragraphBox = (text: string, minimumHeight = 18) => {
      const lines = textLines(text); const height = Math.max(minimumHeight, lines.length * 4.3 + 8); ensureSpace(height);
      border(); pdf.setTextColor(20); pdf.setFont('helvetica', 'normal'); pdf.setFontSize(9); pdf.rect(left, y, width, height); pdf.text(lines, left + 4, y + 6); y += height;
    };
    const listText = (text: string) => text.split('\n').map(item => item.trim()).filter(Boolean).map(item => `- ${item.replace(/^[-+]\s*/, '')}`).join('\n');

    border(); pdf.setFillColor(180, 180, 180); pdf.rect(left, y, width, 11, 'FD');
    pdf.setTextColor(0); pdf.setFont('helvetica', 'bold'); pdf.setFontSize(15); pdf.text('Parecer Técnico Seletivo', 105, y + 7.5, { align: 'center' }); y += 11;

    const row = (leftLabel: string, leftValue: string, rightLabel: string, rightValue: string) => {
      border(); pdf.rect(left, y, rightColumn - left, 8); pdf.rect(rightColumn, y, left + width - rightColumn, 8);
      pdf.setFontSize(8.5); pdf.setFont('helvetica', 'bold'); pdf.text(leftLabel, left + 1.5, y + 5.3); pdf.text(rightLabel, rightColumn + 1.5, y + 5.3);
      pdf.setFont('helvetica', 'normal'); pdf.text(leftValue, left + 40, y + 5.3); pdf.text(rightValue, rightColumn + 26, y + 5.3); y += 8;
    };
    row('Pessoa Candidata:', this.candidate(), 'Data:', new Date().toLocaleDateString('pt-BR'));
    row('Pessoa Entrevistadora:', this.interviewer() || 'Não informado', 'Vaga:', this.role() || 'Não informada');
    row('Senioridade:', this.seniority(), 'Template:', this.template());

    section('Cultura Técnica');
    paragraphBox(this.finalAssessment(), 30);

    section('Tecnologias e Habilidades');
    const evaluated = this.skills().map(skill => {
      const score = skill.skipped ? 'N/A' : skill.score === null ? 'Não avaliado' : `${skill.score}/5`;
      return `- ${skill.name}: ${score}${skill.comment ? ` - ${skill.comment}` : ''}`;
    }).join('\n');
    paragraphBox(evaluated, 28);

    const complementary = this.complementaryTechnologiesForReport();
    if (complementary.length) {
      section('Experiências Complementares');
      paragraphBox(`A pessoa candidata relatou experiência anterior com ${complementary.map(item => item.name).join(', ')}.`, 18);
    }

    section('Prós');
    paragraphBox(listText(this.strengths()), 20);
    section('Contras');
    paragraphBox(listText(this.concerns()), 20);

    section('Feedback');
    paragraphBox(this.quickNotes(), 24);

    section('Conclusão: recomenda a contratação? Para qual perfil e com qual senioridade?');
    paragraphBox(`Recomendação: ${this.recommendation()}\nMédia ponderada: ${this.average()} / 5\nSenioridade avaliada: ${this.seniority()}`, 26);

    const pages = pdf.getNumberOfPages();
    for (let page = 1; page <= pages; page++) { pdf.setPage(page); pdf.setFontSize(7.5); pdf.setTextColor(110); pdf.text(`Página ${page} de ${pages}`, 105, 290, { align: 'center' }); }
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
  selectTechnology(technology: Technology) {
    if (this.hasTechnology(technology.name)) return;
    this.selectedTechnologies.update(items => [...items, technology]);
    this.technologySearch.set('');
    this.markSaving();
  }
  selectSuggestedTechnology(name: string) {
    const technology = this.technologyCatalog.find(item => item.name === name);
    if (technology) this.selectTechnology(technology);
  }
  addCustomTechnology() {
    const name = this.technologySearch().trim();
    if (name && !this.hasTechnology(name)) this.selectTechnology({ name, type: 'OTHER' });
  }
  removeTechnology(name: string) {
    this.selectedTechnologies.update(items => items.filter(item => this.normalizeTechnology(item.name) !== this.normalizeTechnology(name)));
    this.markSaving();
  }
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
  private hasTechnology(name: string) { return this.selectedTechnologies().some(item => this.normalizeTechnology(item.name) === this.normalizeTechnology(name)); }
  private normalizeTechnology(name: string) { return name.trim().toLocaleLowerCase('pt-BR'); }
}
