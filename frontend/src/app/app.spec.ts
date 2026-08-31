import { TestBed } from '@angular/core/testing';
import { InterviewPage } from './app';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import axe, { type RunOptions } from 'axe-core';

const App = InterviewPage;

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should render an empty candidate registration', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('Prepare a entrevista');
    expect((compiled.querySelector('input[name="candidate"]') as HTMLInputElement).value).toBe('');
    expect(compiled.querySelector('input[name="interviewer"]')).toBeNull();
    expect(compiled.querySelector('.authenticated-interviewer')?.textContent).toContain(
      'Carregando usuário',
    );
    expect(compiled.querySelectorAll('.skill-card')).toHaveLength(0);
    expect(compiled.textContent).toContain('os dados permanecem somente nesta aba');
  });

  it('never claims that in-memory interview data was saved', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.updateNotes('Anotação ainda não persistida');

    expect(app.saveState()).toBe('Não salvo — somente nesta aba');
  });

  it('opens a completed interview directly in its read-only summary for reprinting', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const http = TestBed.inject(HttpTestingController);
    flushInitialRequests(http);
    (app as any).loadInterview('completed-1');
    http.expectOne('/api/interviews/completed-1/workspace').flush({
      id: 'completed-1', vacancyId: 'vacancy-1', candidateName: 'Pessoa Candidata',
      vacancyTitleSnapshot: 'Backend Pleno', senioritySnapshot: 'Pleno', clientCompany: 'Empresa',
      requirementSnapshot: ['Java'], interviewerId: '1', status: 'COMPLETED', startedAt: new Date().toISOString(),
      version: 2, assessmentData: JSON.stringify({ finalAssessment: 'Parecer final', strengths: 'Java' }), participants: []
    });
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(app.reviewing()).toBe(true);
    expect(element.textContent).toContain('REVISÃO DO PARECER');
    expect(element.textContent).toContain('Imprimir parecer novamente');
    expect((element.querySelector('.assessment-card textarea') as HTMLTextAreaElement).readOnly).toBe(true);
    expect(element.textContent).not.toContain('Voltar à entrevista');
  });

  it('shows saved only after the backend confirms autosave', async () => {
    const app = TestBed.createComponent(App).componentInstance;
    const http = TestBed.inject(HttpTestingController);
    flushInitialRequests(http);
    (app as any).interviewId.set('interview-1');
    (app as any).assessmentVersion.set(0);

    app.updateNotes('Persistir esta anotação');
    expect(app.saveState()).toBe('Salvando...');
    await new Promise((resolve) => setTimeout(resolve, 550));
    const request = http.expectOne('/api/interviews/interview-1/workspace');
    expect(request.request.body.version).toBe(0);
    expect(JSON.parse(request.request.body.assessmentData).interviewNotes).toBe(
      'Persistir esta anotação',
    );
    expect(app.saveState()).toBe('Salvando...');
    request.flush({ version: 1 });

    expect(app.saveState()).toBe('Salvo');
  });

  it('keeps autosave failure visible and retries explicitly', async () => {
    const app = TestBed.createComponent(App).componentInstance;
    const http = TestBed.inject(HttpTestingController);
    flushInitialRequests(http);
    (app as any).interviewId.set('interview-2');

    app.updateNotes('Não pode ser perdido');
    await new Promise((resolve) => setTimeout(resolve, 550));
    http
      .expectOne('/api/interviews/interview-2/workspace')
      .flush('offline', { status: 503, statusText: 'Unavailable' });
    expect(app.saveState()).toBe('Falha ao salvar');

    app.retryAutosave();
    await new Promise((resolve) => setTimeout(resolve, 550));
    const retry = http.expectOne('/api/interviews/interview-2/workspace');
    retry.flush({ version: 1 });
    expect(app.saveState()).toBe('Salvo');
  });

  it('waits for the latest autosave before opening review without completing early', async () => {
    const app = TestBed.createComponent(App).componentInstance;
    const http = TestBed.inject(HttpTestingController);
    flushInitialRequests(http);
    (app as any).interviewId.set('interview-3');
    (app as any).assessmentVersion.set(0);

    app.updateNotes('Versão anterior');
    await new Promise((resolve) => setTimeout(resolve, 550));
    const previous = http.expectOne('/api/interviews/interview-3/workspace');

    app.updateNotes('Versão final');
    app.finishInterview();
    previous.flush({ version: 1 });
    expect(http.match('/api/interviews/interview-3/complete')).toHaveLength(0);
    await new Promise((resolve) => setTimeout(resolve, 550));

    const latest = http.expectOne('/api/interviews/interview-3/workspace');
    expect(latest.request.body.version).toBe(1);
    expect(JSON.parse(latest.request.body.assessmentData).interviewNotes).toBe('Versão final');
    latest.flush({ version: 2 });
    expect(http.match('/api/interviews/interview-3/workspace/complete')).toHaveLength(0);

    expect(app.reviewing()).toBe(true);
    expect(app.finishingInterview()).toBe(false);
  });

  it('should search languages and frameworks with partial case-insensitive text', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.technologySearch.set('JAVA');

    expect(app.technologyResults().map((item) => item.name)).toEqual(
      expect.arrayContaining(['Java', 'JavaScript']),
    );
  });

  it('should add a custom technology once and allow removing it', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.technologySearch.set('AdonisJS');
    app.addCustomTechnology();
    app.technologySearch.set('adonISjs');
    app.addCustomTechnology();

    expect(app.selectedTechnologies()).toEqual([
      { name: 'AdonisJS', type: 'OTHER', source: 'COMPLEMENTARY_EXPERIENCE' },
    ]);
    app.removeTechnology('ADONISJS');
    expect(app.selectedTechnologies()).toEqual([]);
  });

  it('should only deduplicate evaluated technologies in the report', () => {
    const app = TestBed.createComponent(App).componentInstance;
    seedSkills(app);
    app.selectSuggestedTechnology('Angular');
    app.selectSuggestedTechnology('PHP');

    expect(app.selectedTechnologies().map((item) => item.name)).toEqual(['Angular', 'PHP']);
    expect(app.complementaryTechnologiesForReport().map((item) => item.name)).toEqual(['PHP']);
  });

  it('should review a complementary technology without changing the general score', () => {
    const app = TestBed.createComponent(App).componentInstance;
    seedSkills(app);
    app.setScore(0, 4);
    const averageBefore = app.average();
    app.selectSuggestedTechnology('PHP');
    app.scoreTechnology('PHP', 5);
    app.updateTechnologyReview('PHP', { description: 'Experiência anterior em projetos web.' });

    expect(app.average()).toBe(averageBefore);
    expect(app.complementaryTechnologiesForReport()[0]).toMatchObject({
      name: 'PHP',
      reviewScore: 5,
      description: 'Experiência anterior em projetos web.',
    });
  });

  it('should hide job requirements and compound requirement aliases from quick suggestions', () => {
    const app = TestBed.createComponent(App).componentInstance;
    seedSkills(app);

    expect(app.visibleQuickTechnologySuggestions()).not.toEqual(
      expect.arrayContaining(['C#', '.NET', 'SQL', 'Angular']),
    );
    expect(app.visibleQuickTechnologySuggestions()).toEqual(
      expect.arrayContaining(['Java', 'PHP']),
    );
    expect(app.visibleQuickTechnologySuggestions()).toHaveLength(6);
  });

  it('expands and collapses compact technical and behavioral suggestions', () => {
    const app = TestBed.createComponent(App).componentInstance;

    expect(app.visibleQuickTechnologySuggestions()).toHaveLength(6);
    expect(app.visibleBehavioralSuggestions()).toHaveLength(4);

    app.technologySuggestionsExpanded.set(true);
    app.behavioralSuggestionsExpanded.set(true);
    expect(app.visibleQuickTechnologySuggestions().length).toBeGreaterThan(6);
    expect(app.visibleBehavioralSuggestions().length).toBeGreaterThan(6);

    app.technologySuggestionsExpanded.set(false);
    app.behavioralSuggestionsExpanded.set(false);
    expect(app.visibleQuickTechnologySuggestions()).toHaveLength(6);
    expect(app.visibleBehavioralSuggestions()).toHaveLength(4);
  });

  it('removes selected technologies and behavioral skills from support suggestions', () => {
    const app = TestBed.createComponent(App).componentInstance;

    app.selectSuggestedTechnology('Java');
    app.addBehavioralSkill('Comunicação');

    expect(app.availableQuickTechnologySuggestions()).not.toContain('Java');
    expect(app.availableBehavioralSuggestions()).not.toContain('Comunicação');
  });

  it('keeps duplicate complementary data available for review but removes it from reports', () => {
    const app = TestBed.createComponent(App).componentInstance;
    seedSkills(app);
    app.selectSuggestedTechnology('Angular');
    app.scoreTechnology('Angular', 4);

    expect(app.selectedTechnologies()[0]).toMatchObject({
      name: 'Angular',
      reviewScore: 4,
      source: 'COMPLEMENTARY_EXPERIENCE',
    });
    expect(app.complementaryTechnologiesForReport()).toEqual([]);
  });

  it('should append quick description suggestions during the interview', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.selectSuggestedTechnology('Laravel');
    app.appendTechnologyDescription('Laravel', 'Possui experiência recente.');
    app.appendTechnologyDescription('Laravel', 'Utilizou de forma pontual.');

    expect(app.selectedTechnologies()[0].description).toBe(
      'Possui experiência recente. Utilizou de forma pontual.',
    );
  });

  it('loads only active job positions and derives seniority and requirements', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    const http = TestBed.inject(HttpTestingController);
    http
      .expectOne('/api/users/me')
      .flush({ id: 7, name: 'Eduardo', email: 'eduardo@example.com' });
    http
      .expectOne(
        (request) =>
          request.url === '/api/job-positions' &&
          request.params.get('active') === 'true',
      )
      .flush({
        content: [{
          id: 'job-1',
          title: 'Backend Java',
          seniority: 'Pleno',
          description: '',
          requirements: ['Java', 'SQL'],
          active: true,
          createdAt: '',
          createdBy: '7',
        }],
        page: 0, size: 100, totalElements: 1, totalPages: 1, first: true, last: true,
      });
    app.selectVacancy('job-1');
    expect(app.seniority()).toBe('Pleno');
    expect(app.requirementNames()).toBe('Java · SQL');
    expect(app.authenticatedUserName()).toBe('Eduardo');
  });

  it('does not use fallback vacancies when the backend fails', () => {
    const app = TestBed.createComponent(App).componentInstance;
    const http = TestBed.inject(HttpTestingController);
    http
      .match('/api/users/me')[0]
      ?.flush({ id: 7, name: 'Eduardo', email: 'eduardo@example.com' });
    http
      .expectOne((request) => request.url === '/api/job-positions')
      .flush('error', { status: 500, statusText: 'Server Error' });
    expect(app.vacancies()).toEqual([]);
    expect(app.vacanciesError()).toBe(true);
  });

  it('mentions only report-eligible complementary evaluations in the assessment draft', () => {
    const app = TestBed.createComponent(App).componentInstance;
    seedSkills(app);
    app.setScore(0, 4);
    app.selectSuggestedTechnology('Angular');
    app.scoreTechnology('Angular', 5);
    app.selectSuggestedTechnology('PHP');
    app.scoreTechnology('PHP', 4);
    app.finishInterview();

    expect(app.finalAssessment()).toContain('PHP (4/5)');
    expect(app.finalAssessment()).not.toContain('Angular (5/5)');
  });

  it('keeps interview notes out of the PDF feedback block', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.strengths.set('Boa comunicação\nDomínio técnico');
    app.concerns.set('Pouca experiência em cloud');
    app.quickNotes.set('Recomendo aprofundar arquitetura.');

    expect(app.feedbackForPdf()).toBe(
      'Pontos fortes\n- Boa comunicação\n- Domínio técnico\n\n' +
        'Pontos de atenção\n- Pouca experiência em cloud',
    );
    expect(app.feedbackForPdf()).not.toContain('Recomendo aprofundar arquitetura.');
  });

  it('does not create empty subsections in PDF feedback', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.concerns.set('Necessita aprofundamento');

    expect(app.feedbackForPdf()).toBe('Pontos de atenção\n- Necessita aprofundamento');
  });

  it('builds one PDF technologies table with three groups and presentation-only deduplication', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.skills.set([
      {
        name: 'Java',
        category: 'Backend',
        weight: 5,
        importance: 'Obrigatória',
        expected: 4,
        score: 5,
        skipped: false,
        comment: '',
        source: 'JOB_REQUIREMENT',
      },
      {
        name: 'Spring Boot',
        category: 'Backend',
        weight: 4,
        importance: 'Obrigatória',
        expected: 4,
        score: 4,
        skipped: false,
        comment: '',
        source: 'JOB_REQUIREMENT',
      },
    ]);
    app.selectedTechnologies.set([
      { name: 'Java', type: 'LANGUAGE', reviewScore: 4, source: 'COMPLEMENTARY_EXPERIENCE' },
      { name: 'PHP', type: 'LANGUAGE', reviewScore: 4, source: 'COMPLEMENTARY_EXPERIENCE' },
      { name: 'Angular', type: 'FRAMEWORK', reviewScore: 3, source: 'COMPLEMENTARY_EXPERIENCE' },
    ]);
    app.selectedBehavioralSkills.set(['Comunicação', 'Proatividade']);

    const groups = app.technologiesAndSkillsForPdf();
    expect(groups.map((group) => group.category)).toEqual([
      'REQUISITOS DA VAGA',
      'EXPERIÊNCIAS COMPLEMENTARES',
      'HABILIDADES COMPORTAMENTAIS',
    ]);
    expect(groups[1].items.map((item) => item.name)).toEqual(['PHP', 'Angular']);
    expect(groups[2].items).toEqual([
      { name: 'Comunicação', evaluation: '-' },
      { name: 'Proatividade', evaluation: '-' },
    ]);
    expect(app.selectedTechnologies()).toHaveLength(3);
  });

  it('maps interview notes only to technical culture', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.finalAssessment.set('Resumo registrado.');
    app.quickNotes.set('Possui experiência com sistemas bancários e aplicações legadas.');
    app.strengths.set('Boa experiência com Java.');

    expect(app.technicalCultureForPdf()).toContain('Anotações da entrevista');
    expect(app.technicalCultureForPdf()).toContain('sistemas bancários');
    expect(app.feedbackForPdf()).not.toContain('sistemas bancários');
  });

  it('builds the executive conclusion from recommendation and estimated technical seniority', () => {
    const app = TestBed.createComponent(App).componentInstance;
    seedSkills(app);
    app.setScore(0, 3);
    app.setScore(1, 3);
    app.setScore(2, 3);
    app.recommendation.set('Recomendado');

    expect(app.executiveConclusion()).toContain('Recomendo a pessoa candidata para a vaga.');
    expect(app.executiveConclusion()).toContain('Pleno');

    app.recommendation.set('Não recomendado');
    expect(app.executiveConclusion()).toContain('não recomendo');
  });

  it('keeps vacancy seniority separate from the estimated technical seniority', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.seniority.set('Sênior');
    seedSkills(app);
    app.setScore(0, 3);

    expect(app.seniority()).toBe('Sênior');
    expect(app.estimatedTechnicalSeniority()).toBe('Pleno');
  });

  it.each([
    [0, 'Júnior inicial'], [1.49, 'Júnior inicial'], [1.5, 'Júnior'],
    [2.49, 'Júnior'], [2.5, 'Pleno inicial'], [2.99, 'Pleno inicial'],
    [3, 'Pleno'], [3.49, 'Pleno'], [3.5, 'Pleno avançado'],
    [3.99, 'Pleno avançado'], [4, 'Sênior'], [4.49, 'Sênior'],
    [4.5, 'Sênior avançado'], [5, 'Sênior avançado'],
  ])('maps technical average %s to %s without overlapping boundaries', (average, expected) => {
    const app = TestBed.createComponent(App).componentInstance;
    expect(app.seniorityForAverage(average as number)).toBe(expected);
  });

  it('uses only scored skills and their weights for average and seniority', () => {
    const app = TestBed.createComponent(App).componentInstance;
    seedSkills(app);
    app.setScore(0, 4);
    app.setScore(1, 2);
    app.setNotEvaluated(2);

    expect(app.technicalAverage()).toBeCloseTo(28 / 9, 8);
    expect(app.formattedTechnicalAverage()).toBe('3,11');
    expect(app.estimatedTechnicalSeniority()).toBe('Pleno');
  });

  it('does not estimate seniority without scored skills', () => {
    const app = TestBed.createComponent(App).componentInstance;
    seedSkills(app);
    app.setNotEvaluated(0);
    app.setNotEvaluated(1);
    app.setNotEvaluated(2);

    expect(app.technicalAverage()).toBeNull();
    expect(app.estimatedTechnicalSeniority()).toBe('');
    expect(app.resultValuesForPdf()).toEqual([]);
  });

  it('splits sentences into list items without breaking technical tokens', () => {
    const app = TestBed.createComponent(App).componentInstance;
    const items = app.sentenceItems(
      'Trabalhou com Node.js e .NET 8. Usou a versão 3.2. Contato dev@example.com. Consulte https://example.com/docs.',
    );

    expect(items).toEqual([
      'Trabalhou com Node.js e .NET 8.',
      'Usou a versão 3.2.',
      'Contato dev@example.com.',
      'Consulte https://example.com/docs.',
    ]);
  });

  it('omits empty optional PDF content and groups available feedback', () => {
    const app = TestBed.createComponent(App).componentInstance;

    expect(app.complementaryExperienceForPdf()).toBe('');
    expect(app.behavioralSkillsForPdf()).toBe('');
    expect(app.feedbackForPdf()).toBe('');
    expect(app.technicalAssessmentForPdf()).toBe('');
    expect(app.executiveConclusion()).toBe('');

    app.strengths.set('Comunicação clara. Domínio técnico.');
    expect(app.feedbackForPdf()).toBe(
      'Pontos fortes\n- Comunicação clara.\n- Domínio técnico.',
    );
    expect(app.feedbackForPdf()).not.toContain('Pontos de atenção');

    app.strengths.set('');
    app.concerns.set('Aprofundar observabilidade.');
    expect(app.feedbackForPdf()).toBe('Pontos de atenção\n- Aprofundar observabilidade.');
    expect(app.feedbackForPdf()).not.toContain('Pontos fortes');
  });

  it('renders technologies and skills as concise text without table labels or the word nota', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.skills.set([
      {
        name: 'Java',
        category: 'Backend',
        weight: 5,
        importance: 'Obrigatória',
        expected: 4,
        score: 5,
        skipped: false,
        comment: '',
        source: 'JOB_REQUIREMENT',
      },
    ]);

    const text = app.technologiesAndSkillsTextForPdf();
    expect(text).toBe('Requisitos da vaga:\nJava: 5/5');
    expect(text.toLowerCase()).not.toContain('nota');
    expect(text).not.toContain('Categoria');
    expect(text).not.toContain('Avaliação');
  });

  it('paginates a high-volume PDF without silently clipping long sections', async () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.candidate.set('José da Silva com um nome profissional muito extenso para o cabeçalho');
    app.quickNotes.set(
      Array.from({ length: 180 }, (_, index) =>
        `Observação ${index + 1}: experiência com integração, segurança, APIs e aplicações legadas.`
      ).join('\n'),
    );
    app.strengths.set('Comunicação clara e sólida experiência prática. '.repeat(80));
    app.concerns.set('Necessita aprofundamento em observabilidade e mensageria. '.repeat(80));
    seedSkills(app);

    const startedAt = performance.now();
    const pdf = await app.exportPdf(false);
    const durationMs = performance.now() - startedAt;
    const bytes = pdf.output('arraybuffer') as ArrayBuffer;

    expect(pdf.getNumberOfPages()).toBeGreaterThan(3);
    expect(bytes.byteLength).toBeGreaterThan(10_000);
    expect(durationMs).toBeLessThan(5_000);
  });

  it('records behavioral skills without creating scored evaluations', () => {
    const app = TestBed.createComponent(App).componentInstance;
    seedSkills(app);
    const averageBefore = app.average();
    app.addBehavioralSkill('Comunicação');
    app.addBehavioralSkill('Proatividade');

    expect(app.selectedBehavioralSkills()).toEqual(['Comunicação', 'Proatividade']);
    expect(app.average()).toBe(averageBefore);
    expect(app.skills()).toHaveLength(3);
  });

  it('renders the support panel during the interview without exposing feedback fields', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    seedSkills(app);
    app.started.set(true);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const text = element.textContent || '';

    expect(text).not.toContain('RESUMO ATUAL');
    expect(element.querySelector('.topbar')).toBeNull();
    expect(element.querySelector('aside.support-panel')).not.toBeNull();
    expect(element.querySelectorAll('input[placeholder="Pesquisar tecnologia..."]')).toHaveLength(
      1,
    );
    expect(element.querySelectorAll('.support-notes textarea')).toHaveLength(1);
    expect(element.querySelector('.support-notes textarea')?.getAttribute('rows')).toBe('3');
    const sections = Array.from(element.querySelectorAll('.support-panel > .support-card'));
    expect(sections.map((section) => section.classList[1])).toEqual([
      'support-notes',
      'complementary-support',
      'behavioral-support',
    ]);
    expect(
      element.querySelectorAll('.complementary-support .compact-suggestions button'),
    ).toHaveLength(6);
    expect(
      element.querySelectorAll('.behavioral-support .compact-suggestions button'),
    ).toHaveLength(4);
    expect(text).toContain('Ver mais');
    expect(text).not.toContain('Pontos fortes');
    expect(text).not.toContain('Pontos de atenção');
    expect(text).not.toContain('FEEDBACK');
  });

  it('exposes evaluation state and support controls to assistive technology', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    seedSkills(app);
    app.started.set(true);
    app.setScore(0, 5);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const firstEvaluation = element.querySelector('app-skill-evaluation-card');
    const radios = Array.from(firstEvaluation?.querySelectorAll('[role="radio"]') || []);

    expect(radios).toHaveLength(7);
    expect(radios.filter((radio) => radio.getAttribute('aria-checked') === 'true')).toHaveLength(1);
    expect(element.querySelector('.progress-track')?.getAttribute('role')).toBe('progressbar');
    expect(element.querySelector('.save')?.getAttribute('aria-live')).toBe('polite');
    expect(
      element.querySelector('.support-notes textarea')?.getAttribute('aria-label'),
    ).toBe('Anotações da entrevista');
    expect(
      element.querySelector('.complementary-support input')?.getAttribute('aria-label'),
    ).toBe('Pesquisar ou adicionar experiência complementar');
  });

  it('has no automatically detectable WCAG A/AA violations in interview and review modes', async () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    seedSkills(app);
    app.started.set(true);
    fixture.detectChanges();

    const options: RunOptions = {
      runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
      rules: { 'color-contrast': { enabled: false } },
    };
    const interviewResult = await axe.run(fixture.nativeElement as HTMLElement, options);

    app.reviewing.set(true);
    fixture.detectChanges();
    const reviewResult = await axe.run(fixture.nativeElement as HTMLElement, options);

    expect(interviewResult.violations.map((violation) => violation.id)).toEqual([]);
    expect(reviewResult.violations.map((violation) => violation.id)).toEqual([]);
  }, 30_000);

  it('shows only the document fields in the compact review', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    seedSkills(app);
    app.started.set(true);
    app.reviewing.set(true);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const text = element.textContent || '';

    expect(element.querySelector('aside.support-panel')).toBeNull();
    expect(element.querySelector('.review-notes')).toBeNull();
    expect(text).toContain('somente o conteúdo que aparecerá no documento');
    expect(text).toContain('FEEDBACK');
    expect(text).toContain('Pontos fortes');
    expect(text).toContain('Pontos de atenção');
  });
});

function seedSkills(app: InterviewPage) {
  app.skills.set([
    {
      name: 'C# / .NET',
      category: 'Backend',
      weight: 5,
      importance: 'Obrigatória',
      expected: 4,
      score: null,
      skipped: false,
      comment: '',
      source: 'JOB_REQUIREMENT',
    },
    {
      name: 'SQL',
      category: 'Dados',
      weight: 4,
      importance: 'Obrigatória',
      expected: 3,
      score: null,
      skipped: false,
      comment: '',
      source: 'JOB_REQUIREMENT',
    },
    {
      name: 'Angular',
      category: 'Frontend',
      weight: 3,
      importance: 'Desejável',
      expected: 3,
      score: null,
      skipped: false,
      comment: '',
      source: 'JOB_REQUIREMENT',
    },
  ]);
}

function flushInitialRequests(http: HttpTestingController) {
  http.match(() => true).forEach((request) => request.flush(
    request.request.url === '/api/job-positions'
      ? { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0, first: true, last: true }
      : [],
  ));
}
