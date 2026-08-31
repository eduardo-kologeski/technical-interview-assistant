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
    http.expectOne('/api/profile').flush({
      id: 1, name: 'João', email: 'joao@example.com', technicalSkills: [], availability: [],
    });
    http.expectOne(req => req.url === '/api/profile/availability').flush([]);
    const initial = component.weekStart();

    component.changeWeek(3);
    const futureRequest = http.expectOne(req => req.url === '/api/profile/availability');
    expect(futureRequest.request.params.has('from')).toBe(true);
    expect(futureRequest.request.params.has('to')).toBe(true);
    futureRequest.flush([]);
    expect(component.weekStart()).not.toBe(initial);

    component.currentWeek();
    http.expectOne(req => req.url === '/api/profile/availability').flush([]);
    expect(component.weekStart()).toBe(initial);
  });

  it('opens a date-specific inline form and preserves local times', () => {
    const fixture = TestBed.createComponent(ProfileComponent);
    const component = fixture.componentInstance;
    http.expectOne('/api/profile').flush({
      id: 1, name: 'João', email: 'joao@example.com', technicalSkills: [], availability: [],
    });
    http.expectOne(req => req.url === '/api/profile/availability').flush([]);
    const date = component.weekDays()[0].date;
    component.openAvailability(date);
    component.startTime = '08:00';
    component.endTime = '09:00';
    component.saveAvailability();

    const request = http.expectOne('/api/profile/availability');
    expect(request.request.body).toEqual({ date, startTime: '08:00', endTime: '09:00' });
    request.flush({ id: 1, availabilityDate: date, startTime: '08:00:00', endTime: '09:00:00' });
    expect(component.slotsFor(date)[0].startTime).toBe('08:00:00');
    expect(component.editingDate).toBe(date);
  });

  it('clears an availability error when the form is cancelled', () => {
    const fixture = TestBed.createComponent(ProfileComponent);
    const component = fixture.componentInstance;
    http.expectOne('/api/profile').flush({
      id: 1, name: 'João', email: 'joao@example.com', technicalSkills: [], availability: [],
    });
    http.expectOne(req => req.url === '/api/profile/availability').flush([]);
    component.openAvailability(component.weekDays()[0].date);
    component.saveAvailability();
    http.expectOne('/api/profile/availability').flush(
      { detail: 'Não é possível cadastrar disponibilidade em data ou horário passado.' },
      { status: 409, statusText: 'Conflict' },
    );
    expect(component.error()).toContain('data ou horário passado');

    component.cancelAvailabilityEdit();

    expect(component.error()).toBe('');
    expect(component.editingDate).toBe('');
  });

  it('creates two pending intervals with two clicks each and saves them as one batch', () => {
    const fixture = TestBed.createComponent(ProfileComponent);
    const component = fixture.componentInstance;
    http.expectOne('/api/profile').flush({
      id: 1, name: 'João', email: 'joao@example.com', technicalSkills: [], availability: [],
    });
    http.expectOne(req => req.url === '/api/profile/availability').flush([]);
    const future = component.weekDays()[0];

    component.selectTimeCell(future.date, '09:30', false);
    expect(component.editingDate).toBe(future.date);
    component.selectTimeCell(future.date, '10:15', false);
    component.selectTimeCell(future.date, '16:30', false);
    component.selectTimeCell(future.date, '18:00', false);
    expect(component.pendingIntervals()).toEqual([
      { date: future.date, startTime: '09:30', endTime: '10:15' },
      { date: future.date, startTime: '16:30', endTime: '18:00' },
    ]);

    component.saveAvailability();
    const request = http.expectOne('/api/profile/availability/batch');
    expect(request.request.body).toHaveLength(2);
    request.flush([
      { id: 1, availabilityDate: future.date, startTime: '09:30:00', endTime: '10:15:00' },
      { id: 2, availabilityDate: future.date, startTime: '16:30:00', endTime: '18:00:00' },
    ]);
    expect(component.pendingIntervals()).toEqual([]);
  });

  it('expands the mini calendar and navigates to the chosen date', () => {
    const fixture = TestBed.createComponent(ProfileComponent);
    const component = fixture.componentInstance;
    http.expectOne('/api/profile').flush({
      id: 1, name: 'João', email: 'joao@example.com', technicalSkills: [], availability: [],
    });
    http.expectOne(req => req.url === '/api/profile/availability').flush([]);
    component.toggleMiniCalendar();
    expect(component.miniCalendarOpen()).toBe(true);
    const date = component.calendarDays().find(day => day.currentMonth)!.date;

    component.selectCalendarDate(date);

    http.expectOne(req => req.url === '/api/profile/availability').flush([]);
    expect(component.selectedDay()).toBe(date);
    expect(component.miniCalendarOpen()).toBe(false);
  });
});
