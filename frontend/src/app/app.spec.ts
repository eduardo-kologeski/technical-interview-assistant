import { TestBed } from '@angular/core/testing';
import { InterviewPage } from './app';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

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
      expect.arrayContaining(['Java', 'PHP', 'React', 'Laravel']),
    );
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
      .expectOne('http://localhost:8081/api/users/me')
      .flush({ id: 7, name: 'Eduardo', email: 'eduardo@example.com' });
    http
      .expectOne(
        (request) =>
          request.url === 'http://localhost:8081/api/job-positions' &&
          request.params.get('active') === 'true',
      )
      .flush([
        {
          id: 'job-1',
          title: 'Backend Java',
          seniority: 'Pleno',
          description: '',
          requirements: ['Java', 'SQL'],
          active: true,
          createdAt: '',
          createdBy: '7',
        },
      ]);
    app.selectVacancy('job-1');
    expect(app.seniority()).toBe('Pleno');
    expect(app.requirementNames()).toBe('Java · SQL');
    expect(app.authenticatedUserName()).toBe('Eduardo');
  });

  it('does not use fallback vacancies when the backend fails', () => {
    const app = TestBed.createComponent(App).componentInstance;
    const http = TestBed.inject(HttpTestingController);
    http
      .match('http://localhost:8081/api/users/me')[0]
      ?.flush({ id: 7, name: 'Eduardo', email: 'eduardo@example.com' });
    http
      .expectOne((request) => request.url === 'http://localhost:8081/api/job-positions')
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
        'Pontos fracos e de atenção\n- Pouca experiência em cloud',
    );
    expect(app.feedbackForPdf()).not.toContain('Recomendo aprofundar arquitetura.');
  });

  it('does not create empty subsections in PDF feedback', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.concerns.set('Necessita aprofundamento');

    expect(app.feedbackForPdf()).toBe('Pontos fracos e de atenção\n- Necessita aprofundamento');
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

  it('renders each interview concept once without the obsolete summary sidebar', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    seedSkills(app);
    app.started.set(true);
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent || '';

    expect(text).not.toContain('RESUMO ATUAL');
    expect(text.match(/Experiências complementares/g) || []).toHaveLength(1);
    expect(text.match(/Habilidades comportamentais/g) || []).toHaveLength(1);
    expect(text.match(/ANOTAÇÕES DA ENTREVISTA/g) || []).toHaveLength(1);
    expect(text.match(/Pontos fortes/g) || []).toHaveLength(1);
    expect(text.match(/Pontos de atenção/g) || []).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('aside')).toBeNull();
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
