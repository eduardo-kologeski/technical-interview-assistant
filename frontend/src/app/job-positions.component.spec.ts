import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { JobPositionsComponent } from './job-positions.component';

describe('JobPositionsComponent', () => {
  beforeEach(() => TestBed.configureTestingModule({
    imports: [JobPositionsComponent],
    providers: [provideHttpClient(), provideHttpClientTesting()],
  }));

  it('sends the loaded version and explains a concurrent update', () => {
    const component = TestBed.createComponent(JobPositionsComponent).componentInstance;
    const http = TestBed.inject(HttpTestingController);
    http.expectOne((request) => request.url === '/api/job-positions').flush(page([]));
    component.edit({
      id: 'vacancy-1', title: 'Backend', seniority: 'Pleno', description: '',
      requirements: ['Java'], active: true, createdAt: '', createdBy: '1', version: 4,
    });

    component.save();
    const request = http.expectOne('/api/job-positions/vacancy-1');
    expect(request.request.body.version).toBe(4);
    request.flush({ detail: 'conflict' }, { status: 409, statusText: 'Conflict' });

    expect(component.error()).toBe(
      'Este registro foi alterado por outro usuário. Recarregue os dados.',
    );
    expect(component.editing()).toBe(true);
  });

  it('sends the current version when deactivating', () => {
    const component = TestBed.createComponent(JobPositionsComponent).componentInstance;
    const http = TestBed.inject(HttpTestingController);
    http.expectOne((request) => request.url === '/api/job-positions').flush(page([]));
    const vacancy = {
      id: 'vacancy-2', title: 'Frontend', seniority: 'Sênior', description: '',
      requirements: ['Angular'], active: true, createdAt: '', createdBy: '1', version: 7,
    };

    component.deactivate(vacancy);

    const request = http.expectOne('/api/job-positions/vacancy-2/deactivate');
    expect(request.request.body).toEqual({ version: 7 });
    request.flush({ ...vacancy, active: false, version: 8 });
    http.expectOne((request) => request.url === '/api/job-positions').flush(page([]));
  });

  it('loads another vacancy page from the backend', () => {
    const fixture = TestBed.createComponent(JobPositionsComponent);
    const component = fixture.componentInstance;
    const http = TestBed.inject(HttpTestingController);
    http.expectOne((request) => request.params.get('page') === '0').flush(page([], 0, 2));

    component.goTo(1);

    http.expectOne((request) => request.params.get('page') === '1').flush(page([], 1, 2));
    expect(component.page()).toBe(1);
  });
});

function page(content: unknown[], current = 0, totalPages = 0) {
  return { content, page: current, size: 20, totalElements: content.length, totalPages, first: current === 0, last: current + 1 >= totalPages };
}
