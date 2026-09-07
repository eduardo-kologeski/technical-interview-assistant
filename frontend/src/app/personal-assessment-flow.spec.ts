import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { PersonalAssessmentCollectionComponent } from './personal-assessment-collection.component';
import { PersonalAssessmentCreateComponent } from './personal-assessment-create.component';
import { PersonalAssessmentCollection, PersonalAssessmentService, PersonalAssessmentSummary } from './personal-assessment.service';

const summary: PersonalAssessmentSummary = {
  id: 'assessment-1', title: 'Java', personName: 'Pessoa', context: 'Desenvolvimento', status: 'CREATED',
  version: 0, createdAt: '2026-09-07T00:00:00Z', updatedAt: '2026-09-07T00:00:00Z',
};
const collection: PersonalAssessmentCollection = {
  id: summary.id, status: 'IN_PROGRESS', version: 1, generationEligible: false,
  updatedAt: summary.updatedAt,
  document: { schemaVersion: 1, criteria: [], evidence: [], notes: [], strengths: [], concerns: [] },
};

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
    fixture.componentInstance.newCriterion = 'APIs REST'; fixture.componentInstance.addCriterion();
    fixture.componentInstance.complete();
    expect(service.complete).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(500); fixture.detectChanges();
    expect(service.save).toHaveBeenCalled();
    expect(fixture.componentInstance.document().criteria[0].name).toBe('APIs REST');
    expect(fixture.componentInstance.saveError()).toContain('outra sessão');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('outra sessão');
  });
});
