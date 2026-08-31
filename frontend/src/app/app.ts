import { CommonModule } from '@angular/common';
import { Component, computed, HostListener, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { EvaluationSource, SkillEvaluationCardComponent } from './skill-evaluation-card.component';
import { JobPosition, JobPositionService } from './job-position.service';
import { InterviewService } from './interview.service';
import { AuthService } from './auth.service';
import { CompanyMember, CompanyService } from './company.service';
import { ActivatedRoute, Router } from '@angular/router';
import { EMPTY, Subject, catchError, concatMap, debounceTime, tap } from 'rxjs';

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
  behavioralSkills: string[];
  interviewNotes: string;
  strengths: string;
  concerns: string;
  recommendation: string;
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
  private readonly companies = inject(CompanyService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
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
  readonly completedInterview = signal(false);
  readonly candidate = signal('');
  readonly role = signal('');
  readonly company = signal('');
  readonly authenticatedUserName = computed(() => this.auth.user()?.name || '');
  readonly companyInterviewers = signal<CompanyMember[]>([]);
  readonly selectedParticipantIds = signal<number[]>([]);
  readonly loadingUser = computed(() => !this.auth.user());
  readonly seniority = signal('');
  readonly template = signal('');
  readonly recommendation = signal('Sem recomendação');
  readonly strengths = signal('');
  readonly concerns = signal('');
  readonly finalAssessment = signal('');
  readonly technologySearch = signal('');
  readonly selectedTechnologies = signal<Technology[]>([]);
  readonly behavioralSearch = signal('');
  readonly lastSupportMessage = signal('');
  readonly technologySuggestionsExpanded = signal(false);
  readonly behavioralSuggestionsExpanded = signal(false);
  readonly selectedBehavioralSkills = signal<string[]>([]);
  readonly behavioralCatalog = [
    'Comunicação',
    'Trabalho em equipe',
    'Proatividade',
    'Autonomia',
    'Organização',
    'Adaptabilidade',
    'Capacidade analítica',
    'Resolução de problemas',
    'Colaboração',
    'Clareza na comunicação',
  ];
  readonly behavioralResults = computed(() => {
    const query = this.normalizeTechnology(this.behavioralSearch());
    return this.behavioralCatalog
      .filter(
        (item) => this.normalizeTechnology(item).includes(query) && !this.hasBehavioralSkill(item),
      )
      .slice(0, 8);
  });
  readonly availableBehavioralSuggestions = computed(() =>
    this.behavioralCatalog.filter((item) => !this.hasBehavioralSkill(item)),
  );
  readonly visibleBehavioralSuggestions = computed(() => {
    if (this.behavioralSearch().trim()) return this.behavioralResults();
    const suggestions = this.availableBehavioralSuggestions();
    return this.behavioralSuggestionsExpanded() ? suggestions : suggestions.slice(0, 4);
  });
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
    const value = this.technicalAverage();
    return value === null ? '—' : value.toFixed(1);
  });
  readonly estimatedTechnicalSeniority = computed(() =>
    this.seniorityForAverage(this.technicalAverage()),
  );
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
            if (this.finishRequested && sequence === this.finishSequence) this.enterReview();
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
    else { this.loadVacancies(); this.loadCompanyInterviewers(); }
  }

  private loadCompanyInterviewers() {
    this.companies.members().subscribe({
      next: members => this.companyInterviewers.set(members.filter(member => member.id !== this.auth.user()?.id)),
      error: () => this.companyInterviewers.set([]),
    });
  }

  toggleParticipant(id: number, selected: boolean) {
    this.selectedParticipantIds.update(ids => selected ? [...ids, id] : ids.filter(value => value !== id));
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
      .create(this.candidate().trim(), this.selectedVacancyId(), this.company().trim(), this.selectedParticipantIds())
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
        this.skills.set(this.requirementSkills(interview.requirementSnapshot));
        this.restoreAssessment(interview.assessmentData);
        this.started.set(true);
        this.completedInterview.set(interview.status === 'COMPLETED');
        this.reviewing.set(interview.status === 'COMPLETED');
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

  finishInterview() {
    if (this.finishingInterview()) return;
    this.finishError.set('');
    this.finishingInterview.set(true);
    this.finishRequested = true;
    this.finalAssessment.set(this.buildAssessmentDraft());
    this.finishSequence = this.markSaving();
  }

  backToInterview() {
    if (this.completedInterview()) return;
    this.reviewing.set(false);
  }

  async exportPdf(download = true) {
    const { jsPDF } = await import('jspdf');
    const pdf = new jsPDF({ unit: 'mm', format: 'a4' });
    const left = 18;
    const width = 174;
    const contentBottom = 278;
    const navy: [number, number, number] = [30, 53, 78];
    const blue: [number, number, number] = [46, 91, 125];
    const pale: [number, number, number] = [238, 243, 247];
    let y = 18;
    const ensureSpace = (needed: number) => {
      if (y + needed > contentBottom) {
        pdf.addPage();
        y = 18;
      }
    };
    const textLines = (text: string, maxWidth = width - 8) =>
      pdf.splitTextToSize(text.trim(), maxWidth);
    const section = (title: string) => {
      ensureSpace(15);
      y += 5;
      pdf.setTextColor(...navy);
      pdf.setFont('helvetica', 'bold');
      pdf.setFontSize(10);
      pdf.text(title.toUpperCase(), left, y);
      y += 3;
      pdf.setDrawColor(...blue);
      pdf.setLineWidth(0.5);
      pdf.line(left, y, left + width, y);
      y += 5;
    };
    const paragraph = (text: string, continuationTitle = 'Continuação') => {
      const remaining = [...textLines(text)];
      const lineHeight = 4.3;
      while (remaining.length) {
        ensureSpace(10);
        const maxLines = Math.max(1, Math.floor((contentBottom - y) / lineHeight));
        const lines = remaining.splice(0, maxLines);
        pdf.setTextColor(45);
        pdf.setFont('helvetica', 'normal');
        pdf.setFontSize(9);
        pdf.text(lines, left, y, { lineHeightFactor: 1.2 });
        y += lines.length * lineHeight;
        if (remaining.length) {
          pdf.addPage();
          y = 18;
          pdf.setTextColor(80);
          pdf.setFont('helvetica', 'bold');
          pdf.setFontSize(9);
          pdf.text(`${continuationTitle} (continuação)`, left, y);
          y += 6;
        }
      }
      y += 2;
    };
    const detailRows = (rows: Array<[string, string]>) => {
      const labelWidth = 45;
      for (const [label, value] of rows) {
        const lines = textLines(value, width - labelWidth - 6);
        const height = Math.max(8, lines.length * 4.2 + 3);
        ensureSpace(height);
        pdf.setFillColor(...pale);
        pdf.rect(left, y, labelWidth, height, 'F');
        pdf.setDrawColor(210, 218, 224);
        pdf.rect(left, y, width, height);
        pdf.setTextColor(...navy);
        pdf.setFont('helvetica', 'bold');
        pdf.setFontSize(8.5);
        pdf.text(label, left + 3, y + 5.2);
        pdf.setTextColor(45);
        pdf.setFont('helvetica', 'normal');
        pdf.text(lines, left + labelWidth + 3, y + 5.2, { lineHeightFactor: 1.15 });
        y += height;
      }
    };
    const resultCard = (values: Array<[string, string]>) => {
      ensureSpace(33);
      pdf.setFillColor(...pale);
      pdf.setDrawColor(195, 207, 216);
      pdf.roundedRect(left, y, width, 29, 2, 2, 'FD');
      const columnWidth = width / values.length;
      values.forEach(([label, value], index) => {
        const column = left + 5 + index * columnWidth;
        pdf.setTextColor(85);
        pdf.setFont('helvetica', 'normal');
        pdf.setFontSize(7.5);
        pdf.text(label, column, y + 7);
        pdf.setTextColor(...navy);
        pdf.setFont('helvetica', 'bold');
        pdf.setFontSize(index === 0 ? 14 : 9.5);
        pdf.text(textLines(value, columnWidth - 10), column, y + 15, { lineHeightFactor: 1.1 });
      });
      y += 29;
    };

    pdf.setFillColor(...navy);
    pdf.rect(0, 0, 210, 35, 'F');
    pdf.setTextColor(255);
    pdf.setFont('helvetica', 'bold');
    pdf.setFontSize(16);
    pdf.text('PARECER TÉCNICO SELETIVO', left, 20);
    pdf.setFontSize(8.5);
      pdf.setFont('helvetica', 'normal');
    pdf.text('Documento de apoio à decisão do processo seletivo', left, 27);
    y = 43;

    const identificationRows = this.identificationRowsForPdf();
    const resultValues = this.resultValuesForPdf();
    const requiredKnowledge = this.requiredKnowledgeForPdf();
    const complementaryExperience = this.complementaryExperienceForPdf();
    const behavioralSkills = this.behavioralSkillsForPdf();
    const feedback = this.feedbackForPdf();
    const technicalAssessment = this.technicalAssessmentForPdf();
    const conclusion = this.executiveConclusion();
    const sections: Array<{ title: string; render: () => void }> = [];

    if (identificationRows.length)
      sections.push({ title: 'Identificação', render: () => detailRows(identificationRows) });
    if (resultValues.length)
      sections.push({ title: 'Resultado da avaliação', render: () => resultCard(resultValues) });
    if (requiredKnowledge)
      sections.push({ title: 'Avaliação dos conhecimentos requeridos', render: () => paragraph(requiredKnowledge, 'Avaliação dos conhecimentos requeridos') });
    if (complementaryExperience)
      sections.push({ title: 'Experiências complementares', render: () => paragraph(complementaryExperience, 'Experiências complementares') });
    if (behavioralSkills)
      sections.push({ title: 'Habilidades comportamentais', render: () => paragraph(behavioralSkills, 'Habilidades comportamentais') });
    if (feedback)
      sections.push({ title: 'Feedback', render: () => paragraph(feedback, 'Feedback') });
    if (technicalAssessment)
      sections.push({ title: 'Parecer técnico', render: () => paragraph(technicalAssessment, 'Parecer técnico') });
    if (conclusion) {
      sections.push({
        title: 'Conclusão',
        render: () => {
          const lines = textLines(conclusion, width - 10);
          const height = Math.max(24, 11 + lines.length * 4.3);
          ensureSpace(height);
          pdf.setFillColor(...pale);
          pdf.setDrawColor(...blue);
          pdf.setLineWidth(0.7);
          pdf.roundedRect(left, y, width, height, 2, 2, 'FD');
          pdf.setTextColor(45);
          pdf.setFont('helvetica', 'normal');
          pdf.setFontSize(9);
          pdf.text(lines, left + 5, y + 8, { lineHeightFactor: 1.2 });
          y += height;
        },
      });
    }

    sections.forEach((item, index) => {
      section(`${index + 1}. ${item.title}`);
      item.render();
    });

    const pages = pdf.getNumberOfPages();
    for (let page = 1; page <= pages; page++) {
      pdf.setPage(page);
      pdf.setFontSize(7.5);
      pdf.setTextColor(110);
      pdf.text(`Página ${page} de ${pages}`, 105, 290, { align: 'center' });
    }
    if (download)
      pdf.save(
        `parecer-${
          this.candidate()
            .normalize('NFD')
            .replace(/[\u0300-\u036f]/g, '')
            .toLowerCase()
            .replace(/[^a-z0-9]+/g, '-') || 'entrevista'
        }.pdf`,
      );
    if (download && this.interviewId()) this.completePersistedInterview();
    return pdf;
  }

  private buildAssessmentDraft() {
    const scored = this.skills().filter((s) => s.score !== null);
    if (!scored.length)
      return 'Nenhuma competência recebeu nota. Revise as anotações antes de concluir o parecer.';
    const strong = scored.filter((s) => s.score! >= 4).map((s) => s.name);
    const attention = scored.filter((s) => s.score! <= 2).map((s) => s.name);
    const parts = ['A avaliação técnica foi consolidada com base nos conhecimentos e nas evidências registrados durante a entrevista.'];
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
    if (this.selectedBehavioralSkills().length)
      parts.push(
        `Como habilidades comportamentais, foram registradas: ${this.selectedBehavioralSkills().join(', ')}.`,
      );
    return parts.join(' ');
  }

  feedbackForPdf() {
    const blocks: string[] = [];
    const strengths = this.formatList(this.strengths());
    const concerns = this.formatList(this.concerns());
    if (strengths) blocks.push(`Pontos fortes\n${strengths}`);
    if (concerns) blocks.push(`Pontos de atenção\n${concerns}`);
    return blocks.join('\n\n');
  }

  recommendationText() {
    return this.recommendation() === 'Sem recomendação'
      ? ''
      : `${this.recommendation()} para a vaga`;
  }

  technicalAverage() {
    const scored = this.skills().filter((skill) => skill.score !== null && !skill.skipped);
    const totalWeight = scored.reduce((sum, skill) => sum + skill.weight, 0);
    if (!totalWeight) return null;
    return scored.reduce((sum, skill) => sum + skill.score! * skill.weight, 0) / totalWeight;
  }

  formattedTechnicalAverage() {
    const value = this.technicalAverage();
    return value === null
      ? ''
      : value.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  seniorityForAverage(average: number | null) {
    if (average === null || average < 0 || average > 5) return '';
    if (average < 1.5) return 'Júnior inicial';
    if (average < 2.5) return 'Júnior';
    if (average < 3) return 'Pleno inicial';
    if (average < 3.5) return 'Pleno';
    if (average < 4) return 'Pleno avançado';
    if (average < 4.5) return 'Sênior';
    return 'Sênior avançado';
  }

  identificationRowsForPdf(): Array<[string, string]> {
    return [
      ['Pessoa candidata', this.candidate()],
      ['Vaga', this.role()],
      ['Empresa / cliente', this.company()],
      ['Senioridade da vaga', this.seniority()],
      ['Pessoa entrevistadora', this.authenticatedUserName()],
      ['Data', new Date().toLocaleDateString('pt-BR')],
    ].filter((row): row is [string, string] => this.hasMeaningfulText(row[1]));
  }

  resultValuesForPdf(): Array<[string, string]> {
    const values: Array<[string, string]> = [];
    const average = this.formattedTechnicalAverage();
    if (average) values.push(['Média técnica', `${average} / 5`]);
    const recommendation = this.recommendationText();
    if (recommendation) values.push(['Recomendação', recommendation]);
    const seniority = this.estimatedTechnicalSeniority();
    if (seniority) values.push(['Senioridade técnica estimada', seniority]);
    return values;
  }

  sentenceItems(value?: string | null) {
    if (!this.hasMeaningfulText(value)) return [];
    return value!
      .trim()
      .split(/\r?\n+|(?<=[.!?])\s+/u)
      .map((item) => item.trim().replace(/^[-+•]\s*/, ''))
      .filter((item) => this.hasMeaningfulText(item));
  }

  private hasMeaningfulText(value?: string | null) {
    if (!value?.trim()) return false;
    return !/^(?:não informad[oa]|nenhum(?:a)?\b.*\bregistrad[oa]|nenhuma competência recebeu nota)/iu.test(value.trim());
  }

  executiveConclusion() {
    const parts: string[] = [];
    if (this.recommendation() !== 'Sem recomendação') parts.push(this.recommendationSentence());
    const seniority = this.estimatedTechnicalSeniority();
    if (seniority)
      parts.push(`Senioridade técnica estimada com base na média ponderada: ${seniority}.`);
    return parts.join(' ');
  }

  private recommendationSentence() {
    switch (this.recommendation()) {
      case 'Fortemente recomendado':
        return 'Recomendo fortemente a pessoa candidata para a vaga.';
      case 'Recomendado':
        return 'Recomendo a pessoa candidata para a vaga.';
      case 'Recomendado com ressalvas':
        return 'Recomendo a pessoa candidata para a vaga com as ressalvas registradas neste parecer.';
      case 'Não recomendado':
        return 'Neste momento, não recomendo a pessoa candidata para a vaga.';
      default:
        return 'A recomendação para a vaga não foi registrada.';
    }
  }

  requiredKnowledgeForPdf() {
    return this.skills()
      .filter((skill) => skill.score !== null || skill.skipped || this.hasMeaningfulText(skill.comment))
      .map((skill) => {
        const lines = [skill.name];
        if (skill.skipped) lines.push('Avaliação: N/A');
        else if (skill.score !== null) lines.push(`Avaliação: ${skill.score}/5`);
        lines.push(...this.sentenceItems(skill.comment).map((item) => `- ${item}`));
        return lines.join('\n');
      })
      .join('\n\n');
  }

  complementaryExperienceForPdf() {
    return this.complementaryTechnologiesForReport()
      .filter((item) => item.reviewScore != null || !!item.notEvaluated || this.hasMeaningfulText(item.description))
      .map((item) => {
        const evaluation = item.notEvaluated ? ' - N/A' : item.reviewScore != null ? ` - ${item.reviewScore}/5` : '';
        const details = this.sentenceItems(item.description).map((value) => `- ${value}`);
        return [`${item.name}${evaluation}`, ...details].join('\n');
      })
      .join('\n\n');
  }

  behavioralSkillsForPdf() {
    return this.selectedBehavioralSkills().map((item) => `- ${item}`).join('\n');
  }

  technicalAssessmentForPdf() {
    return [...this.sentenceItems(this.finalAssessment()), ...this.sentenceItems(this.quickNotes())]
      .map((item) => `- ${item}`)
      .join('\n');
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
    }));
    const complementary = this.complementaryTechnologiesForReport().map((item) => ({
      name: item.name,
      evaluation: item.notEvaluated
        ? 'N/A'
        : item.reviewScore == null
          ? 'Sem nota'
          : `${item.reviewScore}/5`,
    }));
    const behavioral = this.selectedBehavioralSkills().map((name) => ({ name, evaluation: '-' }));

    return [
      { category: 'REQUISITOS DA VAGA', items: requirements },
      { category: 'EXPERIÊNCIAS COMPLEMENTARES', items: complementary },
      { category: 'HABILIDADES COMPORTAMENTAIS', items: behavioral },
    ];
  }

  technologiesAndSkillsTextForPdf() {
    const labels: Record<string, string> = {
      'REQUISITOS DA VAGA': 'Requisitos da vaga:',
      'EXPERIÊNCIAS COMPLEMENTARES': 'Experiências complementares:',
      'HABILIDADES COMPORTAMENTAIS': 'Habilidades comportamentais:',
    };

    return this.technologiesAndSkillsForPdf()
      .filter((group) => group.items.length)
      .map((group) => {
        const items = group.items.map((item) =>
          item.evaluation === '-' ? item.name : `${item.name}: ${item.evaluation}`,
        );
        return `${labels[group.category]}\n${items.join('\n')}`;
      })
      .join('\n\n');
  }

  private formatList(text: string) {
    return this.sentenceItems(text).map((item) => `- ${item}`).join('\n');
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
  addBehavioralSkill(name = this.behavioralSearch()) {
    const value = name.trim();
    if (value && !this.hasBehavioralSkill(value))
      this.selectedBehavioralSkills.update((items) => [...items, value]);
    this.behavioralSearch.set('');
    this.markSaving();
  }
  removeBehavioralSkill(name: string) {
    this.selectedBehavioralSkills.update((items) =>
      items.filter((item) => this.normalizeTechnology(item) !== this.normalizeTechnology(name)),
    );
    this.markSaving();
  }
  hasBehavioralSkill(name: string) {
    return this.selectedBehavioralSkills().some(
      (item) => this.normalizeTechnology(item) === this.normalizeTechnology(name),
    );
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
  private enterReview() {
    this.finishRequested = false;
    this.finishingInterview.set(false);
    clearInterval(this.timer);
    this.running.set(false);
    this.reviewing.set(true);
  }
  private assessmentSnapshot(): InterviewAssessment {
    return {
      requiredSkills: this.skills(),
      complementaryTechnologies: this.selectedTechnologies(),
      behavioralSkills: this.selectedBehavioralSkills(),
      interviewNotes: this.quickNotes(),
      strengths: this.strengths(),
      concerns: this.concerns(),
      recommendation: this.recommendation(),
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
      if (Array.isArray(assessment.behavioralSkills))
        this.selectedBehavioralSkills.set(assessment.behavioralSkills);
      this.quickNotes.set(assessment.interviewNotes || '');
      this.strengths.set(assessment.strengths || '');
      this.concerns.set(assessment.concerns || '');
      this.recommendation.set(assessment.recommendation || 'Sem recomendação');
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
