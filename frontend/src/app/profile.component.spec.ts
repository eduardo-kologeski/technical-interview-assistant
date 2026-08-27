import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ProfileComponent } from './profile.component';

describe('ProfileComponent date-based availability', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads only the visible week and navigates any number of weeks', () => {
    const fixture = TestBed.createComponent(ProfileComponent);
    const component = fixture.componentInstance;
    http.expectOne('http://localhost:8081/api/profile').flush({
      id: 1, name: 'João', email: 'joao@example.com', technicalSkills: [], availability: [],
    });
    http.expectOne(req => req.url === 'http://localhost:8081/api/profile/availability').flush([]);
    const initial = component.weekStart();

    component.changeWeek(3);
    const futureRequest = http.expectOne(req => req.url === 'http://localhost:8081/api/profile/availability');
    expect(futureRequest.request.params.has('from')).toBe(true);
    expect(futureRequest.request.params.has('to')).toBe(true);
    futureRequest.flush([]);
    expect(component.weekStart()).not.toBe(initial);

    component.currentWeek();
    http.expectOne(req => req.url === 'http://localhost:8081/api/profile/availability').flush([]);
    expect(component.weekStart()).toBe(initial);
  });

  it('opens a date-specific inline form and preserves local times', () => {
    const fixture = TestBed.createComponent(ProfileComponent);
    const component = fixture.componentInstance;
    http.expectOne('http://localhost:8081/api/profile').flush({
      id: 1, name: 'João', email: 'joao@example.com', technicalSkills: [], availability: [],
    });
    http.expectOne(req => req.url === 'http://localhost:8081/api/profile/availability').flush([]);
    const date = component.weekDays()[0].date;
    component.openAvailability(date);
    component.startTime = '08:00';
    component.endTime = '09:00';
    component.saveAvailability();

    const request = http.expectOne('http://localhost:8081/api/profile/availability');
    expect(request.request.body).toEqual({ date, startTime: '08:00', endTime: '09:00' });
    request.flush({ id: 1, availabilityDate: date, startTime: '08:00:00', endTime: '09:00:00' });
    expect(component.slotsFor(date)[0].startTime).toBe('08:00:00');
    expect(component.editingDate).toBe(date);
  });
});
