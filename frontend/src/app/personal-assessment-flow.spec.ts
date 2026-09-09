import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { PersonalAssessmentCollectionComponent } from './personal-assessment-collection.component';
import { PersonalAssessmentCreateComponent } from './personal-assessment-create.component';
import { PersonalAssessmentReportComponent } from './personal-assessment-report.component';
import { PersonalAssessmentCollection, PersonalAssessmentReport, PersonalAssessmentService, PersonalAssessmentSummary, ReportDraft } from './personal-assessment.service';

const summary: PersonalAssessmentSummary = {
  id: 'assessment-1', title: 'Java', personName: 'Pessoa', context: 'Desenvolvimento', status: 'CREATED',
  version: 0, createdAt: '2026-09-07T00:00:00Z', updatedAt: '2026-09-07T00:00:00Z',
};
const collection: PersonalAssessmentCollection = {
  id: summary.id, status: 'IN_PROGRESS', version: 1, generationEligible: false,
  updatedAt: summary.updatedAt,
  document: { schemaVersion: 1, criteria: [{ id: 'criterion-1', name: 'APIs REST', technology: 'Spring', description: 'Integrações', question: 'Como desenha uma API?', rubric: 'Explique contratos', score: null }], evidence: [], notes: [], strengths: [], concerns: [] },
};
const reportDraft: ReportDraft = {
  context: { text: 'Contexto', evidenceReferences: [], insufficientData: false },
  observedKnowledge: { text: 'Conhecimento', evidenceReferences: ['evidence-1'], insufficientData: false },
  strengths: { text: 'Dados insuficientes', evidenceReferences: [], insufficientData: true },
  concerns: { text: 'Dados insuficientes', evidenceReferences: [], insufficientData: true },
  synthesis: { text: 'Síntese', evidenceReferences: ['evidence-1'], insufficientData: false },
};
const report: PersonalAssessmentReport = { id: 'report-1', assessmentId: summary.id, status: 'DRAFT', sourceVersion: 4,
  version: 0, promptVersion: 'v1', model: 'local-model', originalDraft: reportDraft, draft: reportDraft,
  humanConclusion: null, reviewConfirmed: false, createdAt: summary.createdAt, updatedAt: summary.updatedAt,
  approvedAt: null, replayed: false };

describe('personal assessment flow', () => {
  afterEach(() => { TestBed.resetTestingModule(); vi.useRealTimers(); });

  it('uses the personal collection endpoints without a company context', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(PersonalAssessmentService);
    const http = TestBed.inject(HttpTestingController);
    service.collection('assessment-1').subscribe();
    http.expectOne('/api/assessments/assessment-1/collection').flush(collection);
    service.start('assessment-1', 1).subscribe();
    const start = http.expectOne('/api/assessments/assessment-1/collection/start');
    expect(start.request.body).toEqual({ version: 1 }); start.flush(collection);
    service.save('assessment-1', 1, collection.document).subscribe();
    const save = http.expectOne('/api/assessments/assessment-1/collection');
    expect(save.request.method).toBe('PUT'); expect(save.request.body.document.schemaVersion).toBe(1); save.flush(collection);
    service.complete('assessment-1', 1).subscribe();
    http.expectOne('/api/assessments/assessment-1/collection/complete').flush({ ...collection, status: 'COMPLETED' });
    service.report('assessment-1').subscribe();
    http.expectOne('/api/assessments/assessment-1/report').flush(report);
    service.generateReport('assessment-1', 4, 'generation-key').subscribe();
    const generate = http.expectOne('/api/assessments/assessment-1/report/generate');
    expect(generate.request.headers.get('Idempotency-Key')).toBe('generation-key'); generate.flush(report);
    service.saveReport('assessment-1', 0, reportDraft, 'Conclusão', true).subscribe();
    const saveReport = http.expectOne('/api/assessments/assessment-1/report');
    expect(saveReport.request.method).toBe('PUT'); expect(saveReport.request.body.humanConclusion).toBe('Conclusão'); saveReport.flush(report);
    service.approveReport('assessment-1', 1, 'approval-key').subscribe();
    const approve = http.expectOne('/api/assessments/assessment-1/report/approve');
    expect(approve.request.headers.get('Idempotency-Key')).toBe('approval-key'); approve.flush({ ...report, status: 'APPROVED' });
    service.delete('assessment-1').subscribe();
    expect(http.expectOne('/api/assessments/assessment-1').request.method).toBe('DELETE');
    http.verify();
  });

  it('creates an assessment and navigates to its collection', async () => {
    const service = { create: vi.fn(() => of(summary)) };
    await TestBed.configureTestingModule({ imports: [PersonalAssessmentCreateComponent], providers: [
      provideRouter([]), { provide: PersonalAssessmentService, useValue: service },
    ] }).compileComponents();
    const router = TestBed.inject(Router); const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(PersonalAssessmentCreateComponent);
    fixture.componentInstance.title = ' Java '; fixture.componentInstance.personName = ' Pessoa ';
    fixture.componentInstance.create();
    expect(service.create).toHaveBeenCalledWith({ title: 'Java', personName: 'Pessoa', context: '' });
    expect(navigate).toHaveBeenCalledWith(['/assessments', 'assessment-1']);
  });

  it('keeps edits visible and blocks completion when autosave conflicts', async () => {
    vi.useFakeTimers();
    const service = {
      get: vi.fn(() => of(summary)), collection: vi.fn(() => of(collection)),
      save: vi.fn(() => throwError(() => ({ status: 409 }))), complete: vi.fn(() => of(collection)),
      start: vi.fn(() => of(collection)),
    };
    await TestBed.configureTestingModule({ imports: [PersonalAssessmentCollectionComponent], providers: [
      provideRouter([]), { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'assessment-1' } } } },
      { provide: PersonalAssessmentService, useValue: service },
    ] }).compileComponents();
    const fixture = TestBed.createComponent(PersonalAssessmentCollectionComponent); fixture.detectChanges();
    fixture.componentInstance.updateScore('criterion-1', 4);
    fixture.componentInstance.complete();
    expect(service.complete).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(500); fixture.detectChanges();
    expect(service.save).toHaveBeenCalled();
    expect(fixture.componentInstance.document().criteria[0].score).toBe(4);
    expect(fixture.componentInstance.saveError()).toContain('outra sessão');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('outra sessão');
  });

  it('requires saved requirements before starting the assessment', async () => {
    vi.useFakeTimers();
    const createdCollection: PersonalAssessmentCollection = { ...collection, status: 'CREATED', version: 0,
      document: { schemaVersion: 1, criteria: [], evidence: [], notes: [], strengths: [], concerns: [] } };
    const service = {
      get: vi.fn(() => of(summary)), collection: vi.fn(() => of(createdCollection)),
      save: vi.fn((_id: string, _version: number, document: PersonalAssessmentCollection['document']) => of({ ...createdCollection, version: 1, document })),
      start: vi.fn(() => of({ ...collection, version: 2 })), complete: vi.fn(),
    };
    await TestBed.configureTestingModule({ imports: [PersonalAssessmentCollectionComponent], providers: [
      provideRouter([]), { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'assessment-1' } } } },
      { provide: PersonalAssessmentService, useValue: service },
    ] }).compileComponents();
    const fixture = TestBed.createComponent(PersonalAssessmentCollectionComponent); fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Defina os requisitos antes da entrevista');
    fixture.componentInstance.start(); expect(service.start).not.toHaveBeenCalled();
    fixture.componentInstance.newCriterion = 'Mensageria'; fixture.componentInstance.addCriterion();
    await vi.advanceTimersByTimeAsync(500);
    fixture.componentInstance.start();
    expect(service.save).toHaveBeenCalled();
    expect(service.start).toHaveBeenCalledWith('assessment-1', 1);
  });

  it('restores quick phrases and records positive observations as evidence and strengths', async () => {
    const service = { get: vi.fn(() => of(summary)), collection: vi.fn(() => of(collection)),
      save: vi.fn(() => of(collection)), start: vi.fn(), complete: vi.fn() };
    await TestBed.configureTestingModule({ imports: [PersonalAssessmentCollectionComponent], providers: [
      provideRouter([]), { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'assessment-1' } } } },
      { provide: PersonalAssessmentService, useValue: service },
    ] }).compileComponents();
    const fixture = TestBed.createComponent(PersonalAssessmentCollectionComponent); fixture.detectChanges();
    const option = fixture.componentInstance.quickOptions.find(item => item.label === 'Forte experiência prática')!;
    fixture.componentInstance.appendQuickObservation('criterion-1', option); fixture.detectChanges();
    expect(fixture.componentInstance.document().evidence[0].text).toBe('Forte experiência prática.');
    expect(fixture.componentInstance.document().strengths[0].criterionId).toBe('criterion-1');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Resposta completa');
  });

  it('autosaves the human review before enabling immutable approval', async () => {
    vi.useFakeTimers();
    const completed = { ...collection, status: 'COMPLETED' as const, version: 4, generationEligible: true };
    const service = {
      get: vi.fn(() => of({ ...summary, status: 'COMPLETED', version: 4 })), collection: vi.fn(() => of(completed)), report: vi.fn(() => of(report)),
      saveReport: vi.fn((_id: string, _version: number, draft: ReportDraft, humanConclusion: string, reviewConfirmed: boolean) => of({ ...report, version: 1, draft, humanConclusion, reviewConfirmed })),
      approveReport: vi.fn(() => of({ ...report, status: 'APPROVED' as const, version: 2, humanConclusion: 'Conclusão humana', reviewConfirmed: true, approvedAt: '2026-09-07T01:00:00Z' })),
      generateReport: vi.fn(),
    };
    await TestBed.configureTestingModule({ imports: [PersonalAssessmentReportComponent], providers: [
      provideRouter([]), { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'assessment-1' } } } },
      { provide: PersonalAssessmentService, useValue: service },
    ] }).compileComponents();
    const fixture = TestBed.createComponent(PersonalAssessmentReportComponent); fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('MINUTA DE PARECER');
    fixture.componentInstance.updateSection('context', 'Contexto revisado');
    fixture.componentInstance.updateConclusion('Conclusão humana');
    fixture.componentInstance.updateConfirmation(true);
    expect(fixture.componentInstance.canApprove()).toBe(false);
    await vi.advanceTimersByTimeAsync(550); fixture.detectChanges();
    expect(service.saveReport).toHaveBeenCalled();
    expect(fixture.componentInstance.canApprove()).toBe(true);
    fixture.componentInstance.approve(); fixture.detectChanges();
    expect(service.approveReport).toHaveBeenCalledWith('assessment-1', 1, expect.any(String));
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('PARECER APROVADO');
    const pdf = await fixture.componentInstance.exportPdf(false);
    expect(pdf?.getNumberOfPages()).toBeGreaterThanOrEqual(1);
    fixture.componentInstance.draft.set({ ...reportDraft, synthesis: { ...reportDraft.synthesis, text: 'Registro sustentado. '.repeat(300) } });
    const longPdf = await fixture.componentInstance.exportPdf(false);
    expect(longPdf?.getNumberOfPages()).toBeGreaterThan(1);
    expect(service.approveReport).toHaveBeenCalledTimes(1);
  });
});
