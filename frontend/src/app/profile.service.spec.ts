import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ProfileService } from './profile.service';

describe('ProfileService', () => {
  let service: ProfileService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ProfileService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads and updates only the authenticated profile endpoint', () => {
    service.getProfile().subscribe();
    http.expectOne('/api/profile').flush({
      id: 1,
      name: 'João',
      email: 'joao@example.com',
      technicalSkills: [],
      availability: [],
    });
    service.updateProfile({ name: 'João Silva', email: 'joao@example.com' }).subscribe();
    const request = http.expectOne('/api/profile');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ name: 'João Silva', email: 'joao@example.com' });
    request.flush({});
  });

  it('manages date-based availability without sending a user id', () => {
    service.addSkill('Playwright', 'ADVANCED').subscribe();
    const skill = http.expectOne('/api/profile/technical-skills');
    expect(skill.request.body).toEqual({ name: 'Playwright', proficiencyLevel: 'ADVANCED' });
    expect(skill.request.body.userId).toBeUndefined();
    skill.flush({});

    service.getAvailability('2026-09-01', '2026-09-07').subscribe();
    http.expectOne('/api/profile/availability?from=2026-09-01&to=2026-09-07').flush([]);

    service.addAvailability('2026-09-01', '14:00', '17:00').subscribe();
    const slot = http.expectOne('/api/profile/availability');
    expect(slot.request.body).toEqual({
      date: '2026-09-01',
      startTime: '14:00',
      endTime: '17:00',
    });
    expect(slot.request.body.userId).toBeUndefined();
    slot.flush({});
  });

  it('sends transparent interviewer filters', () => {
    service
      .searchInterviewers({
        vacancyId: 'vacancy-1',
        technology: 'Java',
        date: '2026-09-01',
        time: '14:00',
      })
      .subscribe();
    const request = http.expectOne((req) => req.url === '/api/interviewers');
    expect(request.request.params.get('vacancyId')).toBe('vacancy-1');
    expect(request.request.params.get('technology')).toBe('Java');
    expect(request.request.params.get('date')).toBe('2026-09-01');
    expect(request.request.params.get('time')).toBe('14:00');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');
    request.flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
    });
  });
});
