import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CompanyService } from './company.service';

describe('CompanyService', () => {
  let service: CompanyService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(CompanyService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses the stable paginated contracts for members and invitations', () => {
    service.membersPage(2, 10).subscribe(page => expect(page.page).toBe(2));
    const members = http.expectOne(request => request.url === '/api/companies/members/page');
    expect(members.request.params.get('page')).toBe('2');
    expect(members.request.params.get('size')).toBe('10');
    members.flush({ content: [], page: 2, size: 10, totalElements: 0, totalPages: 0, first: false, last: true });

    service.invitationsPage(1, 5).subscribe(page => expect(page.size).toBe(5));
    const invitations = http.expectOne(request => request.url === '/api/companies/invitations/page');
    expect(invitations.request.params.get('page')).toBe('1');
    expect(invitations.request.params.get('size')).toBe('5');
    invitations.flush({ content: [], page: 1, size: 5, totalElements: 0, totalPages: 0, first: false, last: true });
  });

  it('updates the selected company after a successful rename and changes member roles', () => {
    const original = { id: 'company-1', name: 'Empresa antiga', slug: 'empresa-antiga', role: 'COMPANY_ADMIN' as const };
    service.companies.set([original]);
    service.select(original.id);

    service.rename('Empresa renovada').subscribe();
    http.expectOne('/api/companies/current').flush({ ...original, name: 'Empresa renovada' });
    expect(service.activeCompany()?.name).toBe('Empresa renovada');

    service.changeRole(42, 'COMPANY_ADMIN').subscribe(member => expect(member.role).toBe('COMPANY_ADMIN'));
    const role = http.expectOne('/api/companies/members/42/role');
    expect(role.request.method).toBe('PATCH');
    expect(role.request.body).toEqual({ role: 'COMPANY_ADMIN' });
    role.flush({ id: 42, name: 'Pessoa', email: 'pessoa@empresa.local', role: 'COMPANY_ADMIN' });
  });
});
