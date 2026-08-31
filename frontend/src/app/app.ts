import { CommonModule } from '@angular/common';
import { Component, computed, HostListener, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { EvaluationSource, SkillEvaluationCardComponent } from './skill-evaluation-card.component';
import { JobPosition, JobPositionService } from './job-position.service';
import { InterviewService } from './interview.service';
import { AuthService } from './auth.service';
import { ActivatedRoute, Router } from '@angular/router';
import { EMPTY, Subject, catchError, concatMap, debounceTime, tap } from 'rxjs';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

type Importance = 'Obrigatória' | 'Desejável' | 'Complementar';
type Skill = {
  name: string;
  category: string;
  weight: number;
  importance: Importance;
  expected: number;
  score: number | null;
  skipped: boolean;
  comment: string;
  source: EvaluationSource;
  question?: string;
  rubric?: string;
};
type KitSnapshot = {
  items?: Array<{ criterion: string; question: string; weight: number; rubric: string }>;
};
type TechnologyType = 'LANGUAGE' | 'FRAMEWORK' | 'OTHER';
type Technology = {
  name: string;
  type: TechnologyType;
  description?: string;
  reviewScore?: number | null;
  notEvaluated?: boolean;
  source?: EvaluationSource;
};
type InterviewAssessment = {
  requiredSkills: Skill[];
  complementaryTechnologies: Technology[];
  behavioralSkills?: string[];
  interviewNotes: string;
  feedbackNotes: string;
  strengths: string;
  concerns: string;
  recommendation: string;
  recommendedLevel: string;
  technicalSkillsNotes: string;
  recommendationRationale: string;
  finalAssessment: string;
};
type AutosaveRequest = { sequence: number; assessment: InterviewAssessment };

@Component({
  selector: 'app-interview-page',
  imports: [CommonModule, FormsModule, SkillEvaluationCardComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class InterviewPage {
  private readonly jobPositions = inject(JobPositionService);
  private readonly interviews = inject(InterviewService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly sanitizer = inject(DomSanitizer);
  readonly vacancies = signal<JobPosition[]>([]);
  readonly loadingVacancies = signal(true);
  readonly vacanciesError = signal(false);
  readonly formError = signal('');
  readonly finishError = signal('');
  readonly finishingInterview = signal(false);
  readonly creatingInterview = signal(false);
  readonly selectedVacancyId = signal('');
  readonly started = signal(false);
  readonly reviewing = signal(false);
  readonly candidate = signal('');
  readonly role = signal('');
  readonly company = signal('');
  readonly authenticatedUserName = computed(() => this.auth.user()?.name || '');
  readonly loadingUser = computed(() => !this.auth.user());
  readonly seniority = signal('');
  readonly template = signal('');
  readonly recommendation = signal('Requer avaliação adicional');
  readonly recommendedLevel = signal('');
  readonly feedbackNotes = signal('');
  readonly technicalSkillsNotes = signal('');
  readonly recommendationRationale = signal('');
  readonly strengths = signal('');
  readonly concerns = signal('');
  readonly finalAssessment = signal('');
  readonly technologySearch = signal('');
  readonly selectedTechnologies = signal<Technology[]>([]);
  readonly lastSupportMessage = signal('');
  readonly technologySuggestionsExpanded = signal(false);
  readonly pdfPreviewUrl = signal<SafeResourceUrl | null>(null);
  private pdfPreviewObjectUrl?: string;
  readonly levelOptions = [
    { value: 'Júnior inicial', className: 'level-junior-initial' },
    { value: 'Júnior intermediário', className: 'level-junior-intermediate' },
    { value: 'Júnior avançado', className: 'level-junior-advanced' },
    { value: 'Pleno inicial', className: 'level-mid-initial' },
    { value: 'Pleno intermediário', className: 'level-mid-intermediate' },
    { value: 'Pleno avançado', className: 'level-mid-advanced' },
    { value: 'Sênior inicial', className: 'level-senior-initial' },
    { value: 'Sênior intermediário', className: 'level-senior-intermediate' },
    { value: 'Sênior avançado', className: 'level-senior-advanced' },
    { value: 'Especialista', className: 'level-specialist' },
  ];
  readonly technologyCatalog: Technology[] = [
    ...[
      'Java',
      'C#',
      'JavaScript',
      'TypeScript',
      'Python',
      'PHP',
      'SQL',
      'Go',
      'Kotlin',
      'C',
      'C++',
      'Ruby',
      'Dart',
      'Swift',
      'Rust',
      'Scala',
      'PL/SQL',
      'T-SQL',
    ].map((name) => ({ name, type: 'LANGUAGE' as const })),
    ...[
      'Spring',
      'Spring Boot',
      '.NET',
      'ASP.NET Core',
      'Node.js',
      'NestJS',
      'Express',
      'Django',
      'Flask',
      'FastAPI',
      'Laravel',
      'Symfony',
      'Ruby on Rails',
      'Quarkus',
      'Micronaut',
      'Angular',
      'React',
      'Vue.js',
      'Next.js',
      'Nuxt',
      'Svelte',
      'Flutter',
      'React Native',
      'Ionic',
    ].map((name) => ({ name, type: 'FRAMEWORK' as const })),
  ];
  readonly quickTechnologySuggestions = [
    'Java',
    'C#',
    'JavaScript',
    'TypeScript',
    'Python',
    'PHP',
    'SQL',
    'Spring Boot',
    '.NET',
    'Angular',
    'React',
    'Node.js',
    'Laravel',
  ];
  readonly availableQuickTechnologySuggestions = computed(() => {
    const requirements = new Set(
      this.skills().flatMap((skill) =>
        [skill.name, ...skill.name.split('/')].map((name) => this.normalizeTechnology(name)),
      ),
    );
    return this.quickTechnologySuggestions.filter(
      (name) => !requirements.has(this.normalizeTechnology(name)) && !this.hasTechnology(name),
    );
  });
  readonly visibleQuickTechnologySuggestions = computed(() => {
    const suggestions = this.availableQuickTechnologySuggestions();
    return this.technologySuggestionsExpanded() ? suggestions : suggestions.slice(0, 6);
  });
  readonly activeIndex = signal(0);
  readonly seconds = signal(1938);
  readonly running = signal(false);
  readonly saveState = signal<'Salvando...' | 'Salvo' | 'Falha ao salvar' | 'Não salvo — somente nesta aba'>('Não salvo — somente nesta aba');
  readonly quickNotes = signal('');
  readonly skills = signal<Skill[]>([]);
  readonly evaluated = computed(
    () => this.skills().filter((s) => s.score !== null || s.skipped).length,
  );
  readonly requirementNames = computed(() =>
    this.skills()
      .map((skill) => skill.name)
      .join(' · '),
  );
  readonly progress = computed(() => Math.round((this.evaluated() / this.skills().length) * 100));
  readonly average = computed(() => {
    const scored = this.skills().filter((s) => s.score !== null && !s.skipped);
    const weights = scored.reduce((sum, s) => sum + s.weight, 0);
    return weights
      ? (scored.reduce((sum, s) => sum + s.score! * s.weight, 0) / weights).toFixed(1)
      : '—';
  });
  readonly formattedTime = computed(
    () =>
      `${String(Math.floor(this.seconds() / 60)).padStart(2, '0')}:${String(this.seconds() % 60).padStart(2, '0')}`,
  );
  readonly technologyResults = computed(() => {
    const query = this.normalizeTechnology(this.technologySearch());
    return query
      ? this.technologyCatalog
          .filter(
            (item) =>
              this.normalizeTechnology(item.name).includes(query) && !this.hasTechnology(item.name),
          )
          .slice(0, 8)
      : [];
  });
  readonly canAddCustomTechnology = computed(() => {
    const value = this.technologySearch().trim();
    return (
      !!value &&
      !this.technologyCatalog.some(
        (item) => this.normalizeTechnology(item.name) === this.normalizeTechnology(value),
      ) &&
      !this.hasTechnology(value)
    );
  });
  readonly complementaryTechnologiesForReport = computed(() => {
    const evaluated = new Set(this.skills().map((skill) => this.normalizeTechnology(skill.name)));
    return this.selectedTechnologies().filter(
      (technology) => !evaluated.has(this.normalizeTechnology(technology.name)),
    );
  });
  private timer?: ReturnType<typeof setInterval>;
  private readonly interviewId = signal('');
  private readonly assessmentVersion = signal(0);
  private readonly autosave = new Subject<AutosaveRequest>();
  private failedAssessment?: InterviewAssessment;
  private autosaveSequence = 0;
  private finishSequence = 0;
  private finishRequested = false;

  constructor() {
    this.autosave.pipe(
      debounceTime(500),
      concatMap(({ sequence, assessment }) => {
        const id = this.interviewId();
        if (!id) return EMPTY;
        this.saveState.set('Salvando...');
        return this.interviews.saveAssessment(id, this.assessmentVersion(), JSON.stringify(assessment)).pipe(
          tap((saved) => {
            this.assessmentVersion.set(saved.version);
            this.failedAssessment = undefined;
            this.saveState.set('Salvo');
            if (this.finishRequested && sequence === this.finishSequence)
              this.openPersistedReview();
          }),
          catchError(() => {
            this.failedAssessment = assessment;
            this.saveState.set('Falha ao salvar');
            if (this.finishRequested && sequence === this.finishSequence) {
              this.finishRequested = false;
              this.finishingInterview.set(false);
              this.finishError.set('Não foi possível salvar os dados finais. Tente novamente.');
            }
            return EMPTY;
          }),
        );
      }),
    ).subscribe();
    if (!this.auth.user())
      this.auth.loadUser().subscribe({ error: () => this.router.navigateByUrl('/auth/login') });
    const interviewId = this.route.snapshot.paramMap.get('id');
    if (interviewId) this.loadInterview(interviewId);
    else this.loadVacancies();
  }

  loadVacancies() {
    this.loadingVacancies.set(true);
    this.vacanciesError.set(false);
    this.jobPositions.list(true).subscribe({
      next: (items) => {
        this.vacancies.set(items.filter((item) => item.active));
        this.loadingVacancies.set(false);
      },
      error: () => {
        this.vacancies.set([]);
        this.vacanciesError.set(true);
        this.loadingVacancies.set(false);
      },
    });
  }

  selectVacancy(id: string) {
    this.selectedVacancyId.set(id);
    const vacancy = this.vacancies().find((item) => item.id === id);
    if (!vacancy) return;
    this.template.set(vacancy.title);
    this.role.set(vacancy.title);
    this.seniority.set(vacancy.seniority);
    this.skills.set(this.requirementSkills(vacancy.requirements));
  }

  startInterview() {
    this.formError.set('');
    if (!this.candidate().trim()) {
      this.formError.set('Informe o nome do candidato.');
      return;
    }
    if (!this.selectedVacancyId()) {
      this.formError.set('Selecione uma vaga antes de iniciar a entrevista.');
      return;
    }
    if (!this.auth.user()) {
      this.formError.set('Não foi possível identificar o entrevistador.');
      return;
    }
    this.creatingInterview.set(true);
    this.interviews
      .create(this.candidate().trim(), this.selectedVacancyId(), this.company().trim())
      .subscribe({
        next: (interview) => this.router.navigate(['/interviews', interview.id]),
        error: () => {
          this.creatingInterview.set(false);
          this.formError.set('Não foi possível iniciar a entrevista. Tente novamente.');
        },
      });
  }

  private loadInterview(id: string) {
    this.interviews.get(id).subscribe({
      next: (interview) => {
        this.interviewId.set(interview.id);
        this.assessmentVersion.set(interview.version || 0);
        this.candidate.set(interview.candidateName);
        this.role.set(interview.vacancyTitleSnapshot);
        this.template.set(interview.vacancyTitleSnapshot);
        this.seniority.set(interview.senioritySnapshot);
        this.company.set(interview.clientCompany || '');
        this.selectedVacancyId.set(interview.vacancyId);
        this.skills.set(this.kitSkills(interview.kitSnapshot, interview.requirementSnapshot));
        this.restoreAssessment(interview.assessmentData);
        this.started.set(true);
        this.seconds.set(
          interview.startedAt
            ? Math.max(0, Math.floor((Date.now() - new Date(interview.startedAt).getTime()) / 1000))
            : 0,
        );
        this.running.set(interview.status === 'IN_PROGRESS');
        this.saveState.set('Salvo');
        if (this.running()) this.timer = setInterval(() => this.seconds.update((v) => v + 1), 1000);
      },
      error: () => {
        this.formError.set('Não foi possível carregar a entrevista.');
      },
    });
  }

  private requirementSkills(requirements: string[]): Skill[] {
    return requirements.map((name) => ({
      name,
      category: 'Requisito',
      weight: 3,
      importance: 'Obrigatória',
      expected: 3,
      score: null,
      skipped: false,
      comment: '',
      source: 'JOB_REQUIREMENT',
    }));
  }

  private kitSkills(snapshot: string, requirements: string[]): Skill[] {
    try {
      const kit = JSON.parse(snapshot || '{}') as KitSnapshot;
      if (!Array.isArray(kit.items) || !kit.items.length) return this.requirementSkills(requirements);
      return kit.items.map((item) => ({
        name: item.criterion,
        category: 'Scorecard do kit',
        weight: item.weight,
        importance: 'Obrigatória',
        expected: 3,
        score: null,
        skipped: false,
        comment: '',
        source: 'JOB_REQUIREMENT',
        question: item.question,
        rubric: item.rubric,
      }));
    } catch {
      return this.requirementSkills(requirements);
    }
  }

  finishInterview() {
    if (this.finishingInterview()) return;
    this.finishError.set('');
    this.finishingInterview.set(true);
    this.finishRequested = true;
    this.finalAssessment.set(this.buildAssessmentDraft());
    if (!this.recommendationRationale().trim())
      this.recommendationRationale.set(this.buildRecommendationDraft());
    this.finishSequence = this.markSaving();
  }

  backToInterview() {
    this.closePdfPreview();
    this.reviewing.set(false);
  }

  async previewPdf() {
    this.closePdfPreview();
    const pdf = await this.exportPdf(false);
    const blob = pdf.output('blob');
    this.pdfPreviewObjectUrl = URL.createObjectURL(blob);
    this.pdfPreviewUrl.set(
      this.sanitizer.bypassSecurityTrustResourceUrl(this.pdfPreviewObjectUrl),
    );
  }

  closePdfPreview() {
    this.pdfPreviewUrl.set(null);
    if (this.pdfPreviewObjectUrl) URL.revokeObjectURL(this.pdfPreviewObjectUrl);
    this.pdfPreviewObjectUrl = undefined;
  }

  async exportPdf(download = true) {
    const { jsPDF } = await import('jspdf');
    const pdf = new jsPDF({ unit: 'mm', format: 'a4' });
    const left = 16,
      width = 178,
      rightColumn = 116;
    let y = 16;
    const border = () => {
      pdf.setDrawColor(125);
      pdf.setLineWidth(0.25);
    };
    const ensureSpace = (needed: number) => {
      if (y + needed > 280) {
        pdf.addPage();
        y = 16;
      }
    };
    const textLines = (text: string, maxWidth = width - 8) =>
      pdf.splitTextToSize(text.trim() || 'Não informado.', maxWidth);
    const fitText = (text: string, maxWidth: number) => {
      const value = text.trim() || 'Não informado.';
      if (pdf.getTextWidth(value) <= maxWidth) return value;
      let shortened = value;
      while (shortened.length > 1 && pdf.getTextWidth(`${shortened}...`) > maxWidth)
        shortened = shortened.slice(0, -1);
      return `${shortened}...`;
    };
    const section = (title: string) => {
      ensureSpace(14);
      border();
      pdf.setFillColor(190, 190, 190);
      pdf.rect(left, y, width, 8, 'FD');
      pdf.setTextColor(15);
      pdf.setFont('helvetica', 'normal');
      pdf.setFontSize(11);
      pdf.text(title, left + 2, y + 5.5);
      y += 8;
    };
    const paragraphBox = (text: string, minimumHeight = 18, continuationTitle = 'Continuação') => {
      const remaining = [...textLines(text)];
      const lineHeight = 4.3;
      ensureSpace(Math.min(minimumHeight, 264));
      while (remaining.length) {
        const available = 280 - y;
        const maxLines = Math.max(1, Math.floor((available - 8) / lineHeight));
        const lines = remaining.splice(0, maxLines);
        const naturalHeight = lines.length * lineHeight + 8;
        const height = remaining.length ? naturalHeight : Math.max(minimumHeight, naturalHeight);
        border();
        pdf.setTextColor(20);
        pdf.setFont('helvetica', 'normal');
        pdf.setFontSize(9);
        pdf.rect(left, y, width, height);
        pdf.text(lines, left + 4, y + 6, { lineHeightFactor: 1.2 });
        y += height;
        if (remaining.length) {
          pdf.addPage();
          y = 16;
          pdf.setTextColor(80);
          pdf.setFont('helvetica', 'bold');
          pdf.setFontSize(9);
          pdf.text(`${continuationTitle} (continuação)`, left, y + 3);
          y += 7;
        }
      }
    };

    border();
    pdf.setFillColor(180, 180, 180);
    pdf.rect(left, y, width, 11, 'FD');
    pdf.setTextColor(0);
    pdf.setFont('helvetica', 'bold');
    pdf.setFontSize(15);
    pdf.text('Parecer Técnico Seletivo', 105, y + 7.5, { align: 'center' });
    y += 11;

    const row = (leftLabel: string, leftValue: string, rightLabel: string, rightValue: string) => {
      border();
      pdf.rect(left, y, rightColumn - left, 8);
      pdf.rect(rightColumn, y, left + width - rightColumn, 8);
      pdf.setFontSize(8.5);
      pdf.setFont('helvetica', 'bold');
      pdf.text(leftLabel, left + 1.5, y + 5.3);
      pdf.text(rightLabel, rightColumn + 1.5, y + 5.3);
      pdf.setFont('helvetica', 'normal');
      pdf.text(fitText(leftValue, rightColumn - left - 42), left + 40, y + 5.3);
      pdf.text(fitText(rightValue, left + width - rightColumn - 28), rightColumn + 26, y + 5.3);
      y += 8;
    };
    row('Pessoa Candidata:', this.candidate(), 'Data:', new Date().toLocaleDateString('pt-BR'));
    row(
      'Pessoa Entrevistadora:',
      this.authenticatedUserName(),
      'Vaga:',
      this.role() || 'Não informada',
    );
    row('Senioridade:', this.seniority(), 'Template:', this.template());

    section('Cultura Técnica');
    paragraphBox(this.technicalCultureForPdf(), 30, 'Cultura Técnica');

    section('Tecnologias e Habilidades');
    paragraphBox(this.technologiesAndSkillsTextForPdf(), 24, 'Tecnologias e Habilidades');

    section('Feedback');
    paragraphBox(this.feedbackForPdf(), 24, 'Feedback');

    section('Conclusão: recomenda a contratação? Para qual perfil e com qual senioridade?');
    paragraphBox(this.conclusionForPdf(), 26, 'Conclusão');

    const pages = pdf.getNumberOfPages();
    for (let page = 1; page <= pages; page++) {
      pdf.setPage(page);
      pdf.setFontSize(7.5);
      pdf.setTextColor(110);
      pdf.text(`Página ${page} de ${pages}`, 105, 290, { align: 'center' });
    }
    if (download) {
      pdf.save(
        `parecer-${
          this.candidate()
            .normalize('NFD')
            .replace(/[\u0300-\u036f]/g, '')
            .toLowerCase()
            .replace(/[^a-z0-9]+/g, '-') || 'entrevista'
        }.pdf`,
      );
      this.completePersistedInterview();
    }
    return pdf;
  }

  private buildAssessmentDraft() {
    const scored = this.skills().filter((s) => s.score !== null);
    if (!scored.length)
      return 'Nenhuma competência recebeu nota. Revise as anotações antes de concluir o parecer.';
    const strong = scored.filter((s) => s.score! >= 4).map((s) => s.name);
    const attention = scored.filter((s) => s.score! <= 2).map((s) => s.name);
    const parts = [
      `Foram avaliadas ${scored.length} competências, com média ponderada de ${this.average()} em 5.`,
    ];
    if (strong.length)
      parts.push(`Os melhores resultados registrados foram em ${strong.join(', ')}.`);
    if (attention.length)
      parts.push(
        `Os principais pontos de desenvolvimento registrados foram ${attention.join(', ')}.`,
      );
    const complementary = this.complementaryTechnologiesForReport().filter(
      (item) => item.reviewScore != null || item.notEvaluated || !!item.description?.trim(),
    );
    if (complementary.length) {
      const details = complementary.map((item) => {
        const result = item.notEvaluated
          ? 'N/A'
          : item.reviewScore == null
            ? ''
            : `${item.reviewScore}/5`;
        return `${item.name}${result ? ` (${result})` : ''}`;
      });
      parts.push(
        `Como experiências tecnológicas complementares, foram registradas: ${details.join(', ')}.`,
      );
    }
    return parts.join(' ');
  }

  private buildRecommendationDraft() {
    const level = this.recommendedLevel();
    const subject = level ? ` para o nível ${level}` : '';
    if (this.recommendation() === 'Recomendo')
      return `Recomendo o candidato${subject}, considerando as evidências registradas durante a entrevista.`;
    if (this.recommendation() === 'Não recomendo')
      return `Não recomendo o candidato${subject} neste momento, considerando os pontos de atenção registrados durante a entrevista.`;
    return `Requer avaliação adicional${subject}, pois os dados registrados ainda não sustentam uma decisão definitiva.`;
  }

  feedbackForPdf() {
    const blocks: string[] = [];
    const strengths = this.formatList(this.strengths());
    const concerns = this.formatList(this.concerns());
    if (strengths) blocks.push(`Pontos fortes\n${strengths}`);
    if (concerns) blocks.push(`Pontos fracos e de atenção\n${concerns}`);
    if (this.feedbackNotes().trim()) blocks.push(`Feedback complementar\n${this.feedbackNotes().trim()}`);
    return blocks.join('\n\n') || 'Nenhum feedback registrado.';
  }

  technicalCultureForPdf() {
    return `Resumo técnico\n${this.finalAssessment()}\n\nAnotações da entrevista\n${this.quickNotes().trim() || 'Nenhuma anotação registrada.'}`;
  }

  technologiesAndSkillsForPdf() {
    const requirements = this.skills().map((skill) => ({
      name: skill.name,
      evaluation: skill.skipped
        ? 'N/A'
        : skill.score === null
          ? 'Não avaliado'
          : `${skill.score}/5`,
      comment: skill.comment.trim(),
    }));
    const complementary = this.complementaryTechnologiesForReport().map((item) => ({
      name: item.name,
      evaluation: item.notEvaluated
        ? 'N/A'
        : item.reviewScore == null
          ? 'Sem nota'
          : `${item.reviewScore}/5`,
      comment: item.description?.trim() || '',
    }));

    return [
      { category: 'REQUISITOS DA VAGA', items: requirements },
      { category: 'EXPERIÊNCIAS COMPLEMENTARES', items: complementary },
    ];
  }

  technologiesAndSkillsTextForPdf() {
    const labels: Record<string, string> = {
      'REQUISITOS DA VAGA': 'Requisitos da vaga:',
      'EXPERIÊNCIAS COMPLEMENTARES': 'Experiências complementares:',
    };

    const structured = this.technologiesAndSkillsForPdf()
      .filter((group) => group.items.length)
      .map((group) => {
        const items = group.items.map(
          (item) => `${item.name}: ${item.evaluation}${item.comment ? ` — ${item.comment}` : ''}`,
        );
        return `${labels[group.category]}\n${items.join('\n')}`;
      })
      .join('\n\n');
    const notes = this.technicalSkillsNotes().trim();
    return [structured, notes ? `Observações sobre habilidades técnicas\n${notes}` : '']
      .filter(Boolean)
      .join('\n\n');
  }

  conclusionForPdf() {
    const lines = [
      `Recomendação: ${this.recommendation()}`,
      `Média ponderada: ${this.average()} / 5`,
      `Nivelamento sugerido: ${this.recommendedLevel() || 'Não definido'}`,
    ];
    if (this.recommendationRationale().trim()) lines.push(this.recommendationRationale().trim());
    return lines.join('\n');
  }

  private formatList(text: string) {
    return text
      .split('\n')
      .map((item) => item.trim())
      .filter(Boolean)
      .map((item) => `- ${item.replace(/^[-+]\s*/, '')}`)
      .join('\n');
  }

  setScore(index: number, score: number) {
    this.patchSkill(index, { score, skipped: false });
  }
  setNotEvaluated(index: number) {
    this.patchSkill(index, { score: null, skipped: true });
  }
  updateComment(index: number, comment: string) {
    this.patchSkill(index, { comment });
  }
  appendSkillComment(index: number, suggestion: string) {
    const current = this.skills()[index].comment.trim();
    this.updateComment(index, `${current}${current ? ' ' : ''}${suggestion}`);
  }
  updateNotes(notes: string) {
    this.quickNotes.set(notes);
    this.markSaving();
  }
  selectTechnology(technology: Technology) {
    if (this.hasTechnology(technology.name)) return;
    this.selectedTechnologies.update((items) => [
      ...items,
      { ...technology, source: 'COMPLEMENTARY_EXPERIENCE' },
    ]);
    this.technologySearch.set('');
    this.lastSupportMessage.set(`${technology.name} adicionada às experiências complementares.`);
    this.markSaving();
  }
  selectSuggestedTechnology(name: string) {
    const technology = this.technologyCatalog.find((item) => item.name === name);
    if (technology) this.selectTechnology(technology);
  }
  addCustomTechnology() {
    const name = this.technologySearch().trim();
    if (name && !this.hasTechnology(name)) this.selectTechnology({ name, type: 'OTHER' });
  }
  removeTechnology(name: string) {
    this.selectedTechnologies.update((items) =>
      items.filter(
        (item) => this.normalizeTechnology(item.name) !== this.normalizeTechnology(name),
      ),
    );
    this.markSaving();
  }
  updateTechnologyReview(
    name: string,
    patch: Partial<Pick<Technology, 'description' | 'reviewScore' | 'notEvaluated'>>,
  ) {
    this.selectedTechnologies.update((items) =>
      items.map((item) =>
        this.normalizeTechnology(item.name) === this.normalizeTechnology(name)
          ? { ...item, ...patch }
          : item,
      ),
    );
    this.markSaving();
  }
  scoreTechnology(name: string, score: number) {
    this.updateTechnologyReview(name, { reviewScore: score, notEvaluated: false });
  }
  skipTechnologyReview(name: string) {
    this.updateTechnologyReview(name, { reviewScore: null, notEvaluated: true });
  }
  appendTechnologyDescription(name: string, suggestion: string) {
    const technology = this.selectedTechnologies().find(
      (item) => this.normalizeTechnology(item.name) === this.normalizeTechnology(name),
    );
    const current = technology?.description?.trim() || '';
    this.updateTechnologyReview(name, {
      description: `${current}${current ? ' ' : ''}${suggestion}`,
    });
  }
  select(index: number) {
    this.activeIndex.set(index);
  }
  toggleTimer() {
    this.running.update((v) => !v);
    if (this.running()) this.timer = setInterval(() => this.seconds.update((v) => v + 1), 1000);
    else clearInterval(this.timer);
  }

  @HostListener('window:keydown', ['$event'])
  handleShortcut(event: KeyboardEvent) {
    if ((event.target as HTMLElement).matches('input, textarea')) return;
    if (/^[0-5]$/.test(event.key)) this.setScore(this.activeIndex(), Number(event.key));
    if (event.key.toLowerCase() === 'n') this.setNotEvaluated(this.activeIndex());
    if (event.key === 'ArrowDown')
      this.activeIndex.update((i) => Math.min(i + 1, this.skills().length - 1));
    if (event.key === 'ArrowUp') this.activeIndex.update((i) => Math.max(i - 1, 0));
  }
  private patchSkill(index: number, patch: Partial<Skill>) {
    this.skills.update((items) =>
      items.map((item, i) => (i === index ? { ...item, ...patch } : item)),
    );
    this.markSaving();
  }
  private markSaving() {
    if (!this.interviewId()) {
      this.saveState.set('Não salvo — somente nesta aba');
      return 0;
    }
    this.saveState.set('Salvando...');
    const sequence = ++this.autosaveSequence;
    if (this.finishRequested) this.finishSequence = sequence;
    this.autosave.next({ sequence, assessment: this.assessmentSnapshot() });
    return sequence;
  }
  updateFinalAssessment(value: string) {
    this.finalAssessment.set(value);
    this.markSaving();
  }
  updateStrengths(value: string) {
    this.strengths.set(value);
    this.markSaving();
  }
  updateConcerns(value: string) {
    this.concerns.set(value);
    this.markSaving();
  }
  updateRecommendation(value: string) {
    this.recommendation.set(value);
    this.markSaving();
  }
  updateRecommendedLevel(value: string) {
    this.recommendedLevel.set(value);
    this.markSaving();
  }
  updateFeedbackNotes(value: string) {
    this.feedbackNotes.set(value);
    this.markSaving();
  }
  updateTechnicalSkillsNotes(value: string) {
    this.technicalSkillsNotes.set(value);
    this.markSaving();
  }
  updateRecommendationRationale(value: string) {
    this.recommendationRationale.set(value);
    this.markSaving();
  }
  retryAutosave() {
    if (!this.failedAssessment) return;
    this.saveState.set('Salvando...');
    const sequence = ++this.autosaveSequence;
    if (this.finishRequested) this.finishSequence = sequence;
    this.autosave.next({ sequence, assessment: this.failedAssessment });
  }
  private completePersistedInterview() {
    const id = this.interviewId();
    if (!id) return;
    this.interviews.complete(id).subscribe({
      next: (completed) => {
        this.assessmentVersion.set(completed.version);
        this.finishRequested = false;
        this.finishingInterview.set(false);
        clearInterval(this.timer);
        this.running.set(false);
        this.reviewing.set(true);
      },
      error: () => {
        this.finishRequested = false;
        this.finishingInterview.set(false);
        this.finishError.set('Não foi possível finalizar a entrevista. Tente novamente.');
      },
    });
  }
  private openPersistedReview() {
    this.finishRequested = false;
    this.finishingInterview.set(false);
    this.reviewing.set(true);
  }
  private assessmentSnapshot(): InterviewAssessment {
    return {
      requiredSkills: this.skills(),
      complementaryTechnologies: this.selectedTechnologies(),
      interviewNotes: this.quickNotes(),
      feedbackNotes: this.feedbackNotes(),
      strengths: this.strengths(),
      concerns: this.concerns(),
      recommendation: this.recommendation(),
      recommendedLevel: this.recommendedLevel(),
      technicalSkillsNotes: this.technicalSkillsNotes(),
      recommendationRationale: this.recommendationRationale(),
      finalAssessment: this.finalAssessment(),
    };
  }
  private restoreAssessment(serialized: string | null | undefined) {
    if (!serialized || serialized === '{}') return;
    try {
      const assessment = JSON.parse(serialized) as Partial<InterviewAssessment>;
      if (Array.isArray(assessment.requiredSkills)) this.skills.set(assessment.requiredSkills);
      if (Array.isArray(assessment.complementaryTechnologies))
        this.selectedTechnologies.set(assessment.complementaryTechnologies);
      this.quickNotes.set(assessment.interviewNotes || '');
      this.feedbackNotes.set(assessment.feedbackNotes || '');
      this.strengths.set(assessment.strengths || '');
      this.concerns.set(assessment.concerns || '');
      this.recommendation.set(assessment.recommendation || 'Requer avaliação adicional');
      this.recommendedLevel.set(assessment.recommendedLevel || '');
      this.technicalSkillsNotes.set(assessment.technicalSkillsNotes || '');
      this.recommendationRationale.set(assessment.recommendationRationale || '');
      this.finalAssessment.set(assessment.finalAssessment || '');
    } catch {
      this.formError.set('Os dados salvos da entrevista estão inválidos. Recarregue a página.');
    }
  }
  private hasTechnology(name: string) {
    return this.selectedTechnologies().some(
      (item) => this.normalizeTechnology(item.name) === this.normalizeTechnology(name),
    );
  }
  private normalizeTechnology(name: string) {
    return name.trim().toLocaleLowerCase('pt-BR');
  }
}
