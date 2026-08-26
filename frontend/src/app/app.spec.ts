import { TestBed } from '@angular/core/testing';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
    })
      .compileComponents();
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
    expect(compiled.querySelectorAll('.skill-card')).toHaveLength(0);
  });

  it('should search languages and frameworks with partial case-insensitive text', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.technologySearch.set('JAVA');

    expect(app.technologyResults().map(item => item.name)).toEqual(expect.arrayContaining(['Java', 'JavaScript']));
  });

  it('should add a custom technology once and allow removing it', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.technologySearch.set('AdonisJS');
    app.addCustomTechnology();
    app.technologySearch.set('adonISjs');
    app.addCustomTechnology();

    expect(app.selectedTechnologies()).toEqual([{ name: 'AdonisJS', type: 'OTHER', source: 'COMPLEMENTARY_EXPERIENCE' }]);
    app.removeTechnology('ADONISJS');
    expect(app.selectedTechnologies()).toEqual([]);
  });

  it('should only deduplicate evaluated technologies in the report', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.selectSuggestedTechnology('Angular');
    app.selectSuggestedTechnology('PHP');

    expect(app.selectedTechnologies().map(item => item.name)).toEqual(['Angular', 'PHP']);
    expect(app.complementaryTechnologiesForReport().map(item => item.name)).toEqual(['PHP']);
  });

  it('should review a complementary technology without changing the general score', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.setScore(0, 4);
    const averageBefore = app.average();
    app.selectSuggestedTechnology('PHP');
    app.scoreTechnology('PHP', 5);
    app.updateTechnologyReview('PHP', { description: 'Experiência anterior em projetos web.' });

    expect(app.average()).toBe(averageBefore);
    expect(app.complementaryTechnologiesForReport()[0]).toMatchObject({ name: 'PHP', reviewScore: 5, description: 'Experiência anterior em projetos web.' });
  });

  it('should hide job requirements and compound requirement aliases from quick suggestions', () => {
    const app = TestBed.createComponent(App).componentInstance;

    expect(app.visibleQuickTechnologySuggestions()).not.toEqual(expect.arrayContaining(['C#', '.NET', 'SQL', 'Angular']));
    expect(app.visibleQuickTechnologySuggestions()).toEqual(expect.arrayContaining(['Java', 'PHP', 'React', 'Laravel']));
  });

  it('keeps duplicate complementary data available for review but removes it from reports', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.selectSuggestedTechnology('Angular');
    app.scoreTechnology('Angular', 4);

    expect(app.selectedTechnologies()[0]).toMatchObject({ name: 'Angular', reviewScore: 4, source: 'COMPLEMENTARY_EXPERIENCE' });
    expect(app.complementaryTechnologiesForReport()).toEqual([]);
  });

  it('should append quick description suggestions during the interview', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.selectSuggestedTechnology('Laravel');
    app.appendTechnologyDescription('Laravel', 'Possui experiência recente.');
    app.appendTechnologyDescription('Laravel', 'Utilizou de forma pontual.');

    expect(app.selectedTechnologies()[0].description).toBe('Possui experiência recente. Utilizou de forma pontual.');
  });

  it('mentions only report-eligible complementary evaluations in the assessment draft', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.setScore(0, 4);
    app.selectSuggestedTechnology('Angular');
    app.scoreTechnology('Angular', 5);
    app.selectSuggestedTechnology('PHP');
    app.scoreTechnology('PHP', 4);
    app.finishInterview();

    expect(app.finalAssessment()).toContain('PHP (4/5)');
    expect(app.finalAssessment()).not.toContain('Angular (5/5)');
  });

  it('consolidates strengths, concerns and interviewer notes into one PDF feedback block', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.strengths.set('Boa comunicação\nDomínio técnico');
    app.concerns.set('Pouca experiência em cloud');
    app.quickNotes.set('Recomendo aprofundar arquitetura.');

    expect(app.feedbackForPdf()).toBe(
      'Pontos fortes\n- Boa comunicação\n- Domínio técnico\n\n' +
      'Pontos fracos e de atenção\n- Pouca experiência em cloud\n\n' +
      'Feedback do entrevistador\nRecomendo aprofundar arquitetura.'
    );
  });

  it('does not create empty subsections in PDF feedback', () => {
    const app = TestBed.createComponent(App).componentInstance;
    app.concerns.set('Necessita aprofundamento');

    expect(app.feedbackForPdf()).toBe('Pontos fracos e de atenção\n- Necessita aprofundamento');
  });
});
