import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { MiniCalendarComponent } from './mini-calendar.component';
import { MobileDayStripComponent } from './mobile-day-strip.component';
import { OpenNativePickerDirective } from './open-native-picker.directive';
import {
  Availability,
  Profile,
  ProfileService,
  ProficiencyLevel,
  TechnicalSkill,
} from './profile.service';

@Component({
  selector: 'app-profile',
  imports: [
    FormsModule,
    OpenNativePickerDirective,
    MiniCalendarComponent,
    MobileDayStripComponent,
  ],
  template: `
    <section class="page">
      <header>
        <p>PERFIL</p>
        <h1>Seu perfil de entrevistador</h1>
        <span>Informe os conhecimentos e horários que ajudam na organização das entrevistas.</span>
      </header>
      @if (loading()) {
        <div class="state">Carregando perfil...</div>
      } @else {
        <section class="card personal">
          <div class="section-title">
            <div>
              <p>DADOS PESSOAIS</p>
              <h2>Identificação</h2>
            </div>
            <button (click)="savePersonal()" [disabled]="saving()">
              {{ saving() ? 'Salvando...' : 'Salvar alterações' }}
            </button>
          </div>
          <div class="personal-grid">
            <label>Nome<input [(ngModel)]="name" /></label
            ><label>E-mail<input type="email" [(ngModel)]="email" /></label>
          </div>
        </section>
        <section class="card">
          <div class="section-title">
            <div>
              <p>CONHECIMENTOS TÉCNICOS</p>
              <h2>Tecnologias que você domina</h2>
              <span>Linguagens, frameworks, bancos, ferramentas de desenvolvimento e QA.</span>
            </div>
          </div>
          <div class="skill-add">
            <label
              >Pesquisar tecnologia<input
                [(ngModel)]="skillSearch"
                (keydown.enter)="$event.preventDefault(); addSkill(skillSearch)"
                placeholder="Ex.: Java, Playwright, Kafka Streams" /></label
            ><label
              >Nível<select [(ngModel)]="newLevel">
                <option value="BEGINNER">Básico</option>
                <option value="INTERMEDIATE">Intermediário</option>
                <option value="ADVANCED">Avançado</option>
              </select></label
            ><button (click)="addSkill(skillSearch)">Adicionar</button>
          </div>
          @if (skillSearch.trim()) {
            <div class="suggestions">
              @for (item of filteredCatalog(); track item) {
                <button (click)="addSkill(item)">{{ item }}</button>
              }
              @if (canAddCustom()) {
                <button class="custom" (click)="addSkill(skillSearch)">
                  + Adicionar “{{ skillSearch.trim() }}”
                </button>
              }
            </div>
          }
          @if (profile().technicalSkills.length) {
            <div class="skill-list">
              @for (skill of profile().technicalSkills; track skill.id) {
                <article>
                  <strong>{{ skill.name }}</strong
                  ><select
                    [ngModel]="skill.proficiencyLevel"
                    (ngModelChange)="changeLevel(skill, $event)"
                  >
                    <option value="BEGINNER">Básico</option>
                    <option value="INTERMEDIATE">Intermediário</option>
                    <option value="ADVANCED">Avançado</option></select
                  ><button class="remove" (click)="removeSkill(skill)">Remover</button>
                </article>
              }
            </div>
          } @else {
            <div class="empty">
              Adicione as tecnologias que você domina para ajudar na escolha de entrevistadores.
            </div>
          }
        </section>
        <section class="card">
          <div class="section-title">
            <div>
              <p>DISPONIBILIDADE PARA ENTREVISTAS</p>
              <h2>Horários por semana</h2>
              <span>Informe datas e horários concretos para as próximas entrevistas.</span>
            </div>
          </div>
          <div class="calendar-toolbar">
            <button class="today" (click)="currentWeek()">Hoje</button>
            <button class="icon" aria-label="Semana anterior" (click)="changeWeek(-1)">‹</button>
            <button class="icon" aria-label="Próxima semana" (click)="changeWeek(1)">›</button>
            <button
              class="date-trigger"
              (click)="toggleMiniCalendar()"
              [attr.aria-expanded]="miniCalendarOpen()"
            >
              <span>{{ weekLabel() }}</span><b aria-hidden="true">{{ miniCalendarOpen() ? '▴' : '▾' }}</b>
            </button>
            <span class="view-label">Semana de trabalho</span>
          </div>
          @if (miniCalendarOpen()) {
            <app-mini-calendar
              [monthLabel]="calendarMonthLabel()"
              [days]="calendarDays()"
              [selectedDate]="selectedDay()"
              [weekdays]="miniWeekdays"
              (monthChange)="changeCalendarMonth($event)"
              (dateSelected)="selectCalendarDate($event)"
              (dismissed)="toggleMiniCalendar()"
            />
          }
          @if (error()) {
            <p class="error">{{ error() }}</p>
          }
          @if (weekHasOnlyPastDays()) {
            <div class="past-week-notice">
              <span>Esta semana de trabalho já terminou.</span>
              <button (click)="changeWeek(1)">Abrir próxima semana</button>
            </div>
          }
          <app-mobile-day-strip
            [days]="weekDays()"
            [selectedDate]="selectedDay()"
            (dateSelected)="selectDay($event)"
          />
          @if (editingDate) {
            <div class="availability-editor">
              <div><small>{{ editingAvailabilityId ? 'EDITAR DISPONIBILIDADE' : 'HORÁRIOS SELECIONADOS' }}</small><strong>{{ editingDateLabel() }}</strong></div>
              @if (editingAvailabilityId) {
                <label>Início<input type="time" step="900" [(ngModel)]="startTime" /></label>
                <label>Fim<input type="time" step="900" [(ngModel)]="endTime" /></label>
              } @else {
                <p class="selection-summary">
                  @if (selectionStart()) { Selecione o horário final. }
                  @else { <b>{{ pendingIntervals().length }}</b> {{ pendingIntervals().length === 1 ? 'intervalo pendente' : 'intervalos pendentes' }} · dois cliques por intervalo }
                </p>
                @for (interval of pendingIntervals(); track interval.startTime + interval.endTime; let index = $index) {
                  <div class="pending-interval"><span>Pendente {{ index + 1 }}: {{ interval.startTime }}–{{ interval.endTime }}</span><button type="button" (click)="removePendingInterval(index)">Desfazer</button></div>
                }
              }
              <button (click)="saveAvailability()">{{ editingAvailabilityId ? 'Atualizar' : 'Salvar todos' }}</button>
              @if (editingAvailabilityId) { <button class="remove" (click)="removeEditingAvailability()">Excluir</button> }
              <button class="secondary" (click)="cancelAvailabilityEdit()">Cancelar</button>
            </div>
          }
          <div class="schedule-scroll" aria-label="Grade semanal de disponibilidade" (pointerup)="finishSelectionDrag()" (pointercancel)="finishSelectionDrag()" (pointerleave)="finishSelectionDrag()">
            <div class="schedule-grid">
              <div class="corner"></div>
              @for (dayItem of weekDays(); track dayItem.date) {
                <button class="day-header" [class.mobile-hidden]="dayItem.date !== selectedDay()" (click)="selectDay(dayItem.date)">
                  <span>{{ dayItem.day }}</span><strong>{{ dayItem.dateNumber }}</strong>
                </button>
              }
              @for (time of timeSlots; track time) {
                <div class="time-label" [class.full-hour]="time.endsWith(':00')">{{ time.endsWith(':00') ? time : '' }}</div>
                @for (dayItem of weekDays(); track dayItem.date) {
                  @let firstSlot = slotAt(dayItem.date, time);
                  @let secondTime = addMinutes(time, 15);
                  @let secondSlot = slotAt(dayItem.date, secondTime);
                  <div
                    class="time-cell"
                    [class.mobile-hidden]="dayItem.date !== selectedDay()"
                    [class.past]="dayItem.past"
                  >
                    <button
                      class="time-half first"
                      [class.occupied]="!!firstSlot"
                      [class.slot-start]="firstSlot && isSlotStart(firstSlot, time)"
                      [class.selected]="isEditingTime(dayItem.date, time)"
                      [disabled]="dayItem.past"
                      [attr.aria-label]="cellLabel(dayItem, time, firstSlot)"
                      (click)="handleSelectionClick(dayItem.date, time, dayItem.past, firstSlot)"
                    >
                      @if (firstSlot && isSlotStart(firstSlot, time)) {
                        <span>Disponível</span><b>{{ timeLabel(firstSlot.startTime) }}–{{ timeLabel(firstSlot.endTime) }}</b>
                      }
                    </button>
                    <button
                      class="time-half second"
                      [class.occupied]="!!secondSlot"
                      [class.slot-start]="secondSlot && isSlotStart(secondSlot, secondTime)"
                      [class.selected]="isEditingTime(dayItem.date, secondTime)"
                      [disabled]="dayItem.past"
                      [attr.aria-label]="cellLabel(dayItem, secondTime, secondSlot)"
                      (click)="handleSelectionClick(dayItem.date, secondTime, dayItem.past, secondSlot)"
                    >
                      @if (secondSlot && isSlotStart(secondSlot, secondTime)) {
                        <span>Disponível</span><b>{{ timeLabel(secondSlot.startTime) }}–{{ timeLabel(secondSlot.endTime) }}</b>
                      }
                    </button>
                  </div>
                }
              }
            </div>
          </div>
        </section>
      }
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .page {
        max-width: 1060px;
        margin: auto;
        padding: 38px 30px 70px;
      }
      header p,
      .section-title p {
        margin: 0;
        color: #176b66;
        font-size: 11px;
        font-weight: 900;
        letter-spacing: 0.14em;
      }
      header h1 {
        margin: 7px 0;
        font-size: 34px;
      }
      header span,
      .section-title span {
        color: #697881;
      }
      .card {
        margin-top: 22px;
        padding: 24px;
        background: #fff;
        border: 1px solid #dce5ea;
        border-radius: 13px;
        box-shadow: 0 5px 20px #1434460a;
      }
      .section-title {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        gap: 18px;
      }
      .section-title h2 {
        margin: 5px 0;
        font-size: 21px;
      }
      .section-title button,
      .skill-add button {
        padding: 10px 14px;
        border: 0;
        border-radius: 8px;
        background: #165d7a;
        color: #fff;
        font-weight: 800;
      }
      .personal-grid,
      .skill-add {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 14px;
        margin-top: 20px;
      }
      .skill-add {
        grid-template-columns: 1fr 170px auto;
        align-items: end;
      }
      .card label {
        display: grid;
        gap: 6px;
        color: #52616b;
        font-size: 12px;
        font-weight: 750;
      }
      .card input,
      .card select {
        width: 100%;
        padding: 10px;
        border: 1px solid #d3dde3;
        border-radius: 7px;
        background: #fff;
        font: inherit;
      }
      .suggestions {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
        margin-top: 10px;
      }
      .suggestions button {
        padding: 6px 9px;
        border: 1px solid #cbd9d8;
        border-radius: 15px;
        background: #f7fbfa;
        color: #226b67;
      }
      .suggestions .custom {
        border-style: dashed;
      }
      .skill-list {
        display: grid;
        gap: 8px;
        margin-top: 18px;
      }
      .skill-list article {
        display: grid;
        grid-template-columns: 1fr 180px auto;
        align-items: center;
        gap: 12px;
        padding: 11px 13px;
        border: 1px solid #e0e7eb;
        border-radius: 9px;
      }
      .remove {
        border: 0 !important;
        background: transparent !important;
        color: #a54c42 !important;
      }
      .empty,
      .state {
        margin-top: 18px;
        padding: 18px;
        border: 1px dashed #ccd9de;
        border-radius: 9px;
        color: #5c6973;
        background: #fafcfd;
      }
      .calendar-toolbar {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-top: 20px;
      }
      .calendar-toolbar button,
      .schedule-grid button {
        font: inherit;
      }
      .today {
        padding: 9px 16px;
        border: 1px solid #ccd7dc;
        border-radius: 7px;
        background: #fff;
        color: #314750;
        font-weight: 800;
      }
      .icon {
        width: 34px;
        height: 34px;
        border: 1px solid #ccd7dc;
        border-radius: 7px;
        background: #fff;
        color: #176b66;
        font-size: 22px;
      }
      .date-trigger {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 9px 12px;
        border: 0;
        border-radius: 7px;
        background: transparent;
        color: #263d46;
        font-weight: 850;
      }
      .date-trigger:hover { background: #f0f5f5; }
      .view-label {
        margin-left: auto;
        color: #63747c;
        font-size: 12px;
        font-weight: 750;
      }
      .past-week-notice {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 12px;
        margin-top: 14px;
        padding: 10px 12px;
        border-radius: 8px;
        background: #fff7e6;
        color: #765a22;
        font-size: 12px;
        font-weight: 750;
      }
      .past-week-notice button { border: 0; background: transparent; color: #176b66; font-weight: 850; }
      .availability-editor {
        display: grid;
        grid-template-columns: minmax(160px, 1fr) 115px 115px auto auto auto;
        align-items: end;
        gap: 9px;
        margin-top: 16px;
        padding: 12px;
        border: 1px solid #b9d6d3;
        border-radius: 9px;
        background: #f2f8f7;
      }
      .availability-editor > div {
        display: grid;
        gap: 3px;
      }
      .availability-editor small { color: #176b66; font-size: 9px; font-weight: 900; }
      .selection-summary {
        grid-column: span 2;
        margin: 0;
        color: #536970;
        font-size: 11px;
      }
      .selection-summary b { color: #176b66; font-size: 16px; }
      .availability-editor button {
        min-height: 38px;
        padding: 8px 11px;
        border: 0;
        border-radius: 7px;
        background: #165d7a;
        color: #fff;
        font-weight: 800;
      }
      .schedule-scroll {
        max-height: 610px;
        margin-top: 16px;
        overflow-y: auto;
        overscroll-behavior: contain;
        border: 1px solid #dce5e8;
        border-radius: 9px;
        scrollbar-color: #aabdc2 #eef3f4;
      }
      .schedule-grid {
        display: grid;
        grid-template-columns: 62px repeat(5, minmax(112px, 1fr));
        min-width: 650px;
      }
      .corner,
      .day-header {
        position: sticky;
        top: 0;
        z-index: 3;
        min-height: 62px;
        border: 0;
        border-bottom: 1px solid #d5e0e4;
        background: #fff;
      }
      .corner { left: 0; z-index: 4; }
      .day-header {
        display: grid;
        place-content: center;
        gap: 3px;
        border-left: 1px solid #e3eaed;
        color: #435861;
      }
      .day-header span { font-size: 10px; font-weight: 850; letter-spacing: .08em; }
      .day-header strong { font-size: 18px; }
      .time-label {
        position: sticky;
        left: 0;
        z-index: 2;
        height: 32px;
        padding: 4px 9px 0 0;
        border-bottom: 1px solid #edf1f2;
        background: #fff;
        color: #65757c;
        font-size: 10px;
        text-align: right;
      }
      .time-cell {
        position: relative;
        display: grid;
        grid-template-rows: 1fr 1fr;
        height: 32px;
        border-left: 1px solid #e3eaed;
        border-bottom: 1px solid #edf1f2;
        background: #fff;
      }
      .time-cell.past { cursor: not-allowed; background: #f6f7f7; }
      .time-half {
        position: relative;
        min-width: 0;
        padding: 0;
        border: 0;
        background: transparent;
        color: #165d7a;
        text-align: left;
        user-select: none;
      }
      .time-half.first { border-bottom: 1px dotted #e1e8ea; }
      .time-half:hover:not(:disabled):not(.occupied) {
        z-index: 1;
        background: #eef7f6;
        box-shadow: inset 0 0 0 1px #8bbdb8;
      }
      .time-half:disabled { cursor: not-allowed; }
      .time-half.occupied { background: #dff0ee; }
      .time-half.slot-start { border-top: 2px solid #24877f; border-left: 4px solid #24877f; }
      .time-half.selected:not(.occupied) { background: #b8deda; box-shadow: inset 3px 0 #176b66; }
      .time-half.occupied span,
      .time-half.occupied b {
        position: absolute;
        z-index: 2;
        display: grid;
        padding-left: 5px;
        line-height: 1.15;
        white-space: nowrap;
      }
      .time-half.occupied span { top: 1px; font-size: 8px; font-weight: 850; }
      .time-half.occupied b { top: 10px; font-size: 8px; }
      .secondary {
        border: 1px solid #ccd7dc !important;
        background: #fff !important;
        color: #53636c !important;
      }
      .error {
        padding: 10px;
        border-radius: 7px;
        background: #fff0ee;
        color: #a44035;
      }
      @media (max-width: 760px) {
        .page {
          padding: 25px 14px;
        }
        .personal-grid,
        .skill-add {
          grid-template-columns: 1fr;
        }
        .skill-list article {
          grid-template-columns: 1fr;
        }
        .section-title {
          flex-direction: column;
        }
        .calendar-toolbar { flex-wrap: wrap; }
        .date-trigger { order: -1; width: 100%; justify-content: space-between; background: #f4f7f7; }
        .view-label { display: none; }
        .availability-editor { grid-template-columns: 1fr 1fr; }
        .availability-editor > div { grid-column: 1 / -1; }
        .schedule-scroll { max-height: 530px; margin-top: 10px; }
        .schedule-grid { grid-template-columns: 54px minmax(220px, 1fr); min-width: 0; }
        .schedule-grid .mobile-hidden { display: none; }
      }
    `,
  ],
})
export class ProfileComponent {
  private readonly service = inject(ProfileService);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly profile = signal<Profile>({
    id: 0,
    name: '',
    email: '',
    technicalSkills: [],
    availability: [],
  });
  name = '';
  email = '';
  skillSearch = '';
  newLevel: ProficiencyLevel = 'INTERMEDIATE';
  readonly weekStart = signal(this.isoDate(this.startOfWeek(new Date())));
  readonly selectedDay = signal(this.weekStart());
  readonly miniCalendarOpen = signal(false);
  readonly calendarMonth = signal(this.weekStart().slice(0, 7));
  selectedWeekDate = this.weekStart();
  editingDate = '';
  startTime = '14:00';
  endTime = '17:00';
  editingAvailabilityId: number | null = null;
  readonly pendingIntervals = signal<Array<{ date: string; startTime: string; endTime: string }>>([]);
  readonly selectionStart = signal<{ date: string; time: string } | null>(null);
  private selectionDragActive = false;
  private selectionDragDate = '';
  private selectionDragAnchor = '';
  private suppressSelectionClick = false;
  readonly miniWeekdays = ['S', 'T', 'Q', 'Q', 'S', 'S', 'D'];
  readonly timeSlots = Array.from({ length: 28 }, (_, index) => {
    const minutes = 7 * 60 + index * 30;
    return `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;
  });
  readonly catalog = [
    'Java',
    'Spring Boot',
    'C#',
    '.NET',
    'PHP',
    'Laravel',
    'Python',
    'JavaScript',
    'TypeScript',
    'Angular',
    'React',
    'Vue.js',
    'Node.js',
    'SQL',
    'PostgreSQL',
    'Oracle',
    'MongoDB',
    'Docker',
    'Kubernetes',
    'Kafka',
    'RabbitMQ',
    'Selenium',
    'Cypress',
    'Playwright',
    'Postman',
    'REST Assured',
    'JUnit',
    'Automação de testes',
    'Testes de API',
    'Testes E2E',
    'CI/CD',
  ];
  readonly weekDays = computed(() => {
    const start = this.localDate(this.weekStart());
    const today = this.isoDate(new Date());
    return Array.from({ length: 5 }, (_, index) => {
      const date = new Date(start);
      date.setDate(start.getDate() + index);
      const iso = this.isoDate(date);
      return {
        date: iso,
        day: date.toLocaleDateString('pt-BR', { weekday: 'short' }).replace('.', '').toUpperCase(),
        label: date.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' }),
        dateNumber: date.getDate(),
        past: iso < today,
      };
    });
  });
  readonly calendarDays = computed(() => {
    const [year, month] = this.calendarMonth().split('-').map(Number);
    const first = new Date(year, month - 1, 1);
    const start = new Date(first);
    const mondayIndex = (first.getDay() + 6) % 7;
    start.setDate(first.getDate() - mondayIndex);
    const today = this.isoDate(new Date());
    return Array.from({ length: 42 }, (_, index) => {
      const date = new Date(start);
      date.setDate(start.getDate() + index);
      const iso = this.isoDate(date);
      return { date: iso, number: date.getDate(), currentMonth: date.getMonth() === month - 1, today: iso === today };
    });
  });
  readonly weekHasOnlyPastDays = computed(() => this.weekDays().every((day) => day.past));
  constructor() {
    this.load();
  }
  load() {
    this.service.getProfile().subscribe({
      next: (p) => {
        this.profile.set(p);
        this.name = p.name;
        this.email = p.email;
        this.loading.set(false);
        this.loadAvailabilityWeek();
      },
      error: () => {
        this.error.set('Não foi possível carregar o perfil.');
        this.loading.set(false);
      },
    });
  }
  normalized(value: string) {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .trim()
      .toLowerCase();
  }
  filteredCatalog() {
    const q = this.normalized(this.skillSearch);
    return this.catalog
      .filter(
        (item) =>
          this.normalized(item).includes(q) &&
          !this.profile().technicalSkills.some(
            (skill) => this.normalized(skill.name) === this.normalized(item),
          ),
      )
      .slice(0, 8);
  }
  canAddCustom() {
    const q = this.normalized(this.skillSearch);
    return (
      !!q &&
      !this.catalog.some((item) => this.normalized(item) === q) &&
      !this.profile().technicalSkills.some((item) => this.normalized(item.name) === q)
    );
  }
  savePersonal() {
    this.saving.set(true);
    this.service.updateProfile({ name: this.name, email: this.email }).subscribe({
      next: (p) => {
        this.profile.set(p);
        this.saving.set(false);
      },
      error: (e) => {
        this.error.set(e.error?.detail || 'Não foi possível salvar.');
        this.saving.set(false);
      },
    });
  }
  addSkill(name: string) {
    const value = name.trim();
    if (!value) return;
    this.service.addSkill(value, this.newLevel).subscribe({
      next: (skill) => {
        this.profile.update((p) => ({
          ...p,
          technicalSkills: [...p.technicalSkills, skill].sort((a, b) =>
            a.name.localeCompare(b.name),
          ),
        }));
        this.skillSearch = '';
        this.error.set('');
      },
      error: (e) => this.error.set(e.error?.detail || 'Não foi possível adicionar.'),
    });
  }
  changeLevel(skill: TechnicalSkill, level: ProficiencyLevel) {
    this.service.updateSkill(skill.id, skill.name, level).subscribe((updated) =>
      this.profile.update((p) => ({
        ...p,
        technicalSkills: p.technicalSkills.map((item) => (item.id === updated.id ? updated : item)),
      })),
    );
  }
  removeSkill(skill: TechnicalSkill) {
    this.service.removeSkill(skill.id).subscribe(() =>
      this.profile.update((p) => ({
        ...p,
        technicalSkills: p.technicalSkills.filter((item) => item.id !== skill.id),
      })),
    );
  }
  saveAvailability() {
    if (!this.editingAvailabilityId && this.pendingIntervals().length) {
      this.service.addAvailabilityBatch(this.pendingIntervals()).subscribe({
        next: (slots) => {
          this.profile.update((profile) => ({
            ...profile,
            availability: [...profile.availability, ...slots].sort((a, b) =>
              a.availabilityDate.localeCompare(b.availabilityDate) || a.startTime.localeCompare(b.startTime)),
          }));
          this.cancelAvailabilityEdit();
        },
        error: (e) => this.error.set(e.error?.detail || 'Não foi possível salvar os intervalos.'),
      });
      return;
    }
    const request = this.editingAvailabilityId
      ? this.service.updateAvailability(
          this.editingAvailabilityId,
          this.editingDate,
          this.startTime,
          this.endTime,
        )
      : this.service.addAvailability(this.editingDate, this.startTime, this.endTime);
    request.subscribe({
      next: (slot) => {
        this.profile.update((p) => ({
          ...p,
          availability: [...p.availability.filter((item) => item.id !== slot.id), slot].sort(
            (a, b) =>
              a.availabilityDate.localeCompare(b.availabilityDate) ||
              a.startTime.localeCompare(b.startTime),
          ),
        }));
        if (this.editingAvailabilityId) this.cancelAvailabilityEdit();
        else {
          this.startTime = '14:00';
          this.endTime = '17:00';
        }
        this.error.set('');
      },
      error: (e) => this.error.set(e.error?.detail || 'Não foi possível adicionar o horário.'),
    });
  }
  editAvailability(slot: Availability) {
    this.error.set('');
    this.editingAvailabilityId = slot.id;
    this.editingDate = slot.availabilityDate;
    this.startTime = slot.startTime;
    this.endTime = slot.endTime;
  }
  cancelAvailabilityEdit() {
    this.error.set('');
    this.editingAvailabilityId = null;
    this.editingDate = '';
    this.startTime = '14:00';
    this.endTime = '17:00';
    this.pendingIntervals.set([]);
    this.selectionStart.set(null);
  }
  removeAvailability(slot: Availability) {
    this.service.removeAvailability(slot.id).subscribe(() => {
      this.profile.update((p) => ({
        ...p,
        availability: p.availability.filter((item) => item.id !== slot.id),
      }));
      if (this.editingAvailabilityId === slot.id) this.cancelAvailabilityEdit();
    });
  }
  slotsFor(date: string) {
    return this.profile().availability.filter((slot) => slot.availabilityDate === date);
  }
  openAvailability(date: string) {
    this.error.set('');
    this.editingDate = date;
    this.editingAvailabilityId = null;
    this.startTime = '14:00';
    this.endTime = '17:00';
  }
  selectTimeCell(date: string, time: string, past: boolean) {
    if (past) return;
    this.error.set('');
    this.selectedDay.set(date);
    const start = this.selectionStart();
    if (!start) {
      this.editingDate = date;
      this.selectionStart.set({ date, time });
      this.startTime = time;
      this.endTime = time;
      return;
    }
    if (start.date !== date) {
      this.selectionStart.set({ date, time });
      this.editingDate = date;
      return;
    }
    const interval = { date, startTime: start.time < time ? start.time : time, endTime: start.time < time ? time : start.time };
    if (interval.startTime === interval.endTime || this.minutesBetween(interval.startTime, interval.endTime) < 30) {
      this.error.set('Selecione um intervalo de pelo menos 30 minutos.');
      return;
    }
    const conflicts = [...this.pendingIntervals(), ...this.slotsFor(date).map(slot => ({ date, startTime: this.timeLabel(slot.startTime), endTime: this.timeLabel(slot.endTime) }))]
      .some(item => item.date === date && interval.startTime < item.endTime && interval.endTime > item.startTime);
    if (conflicts) { this.error.set('Este intervalo se sobrepõe a outro horário.'); return; }
    this.pendingIntervals.update(items => [...items, interval]);
    this.selectionStart.set(null);
  }
  selectedSegments() {
    if (!this.editingDate || this.editingAvailabilityId) return [];
    const segments: string[] = [];
    for (const interval of this.pendingIntervals())
      for (let time = interval.startTime; time < interval.endTime; time = this.addMinutes(time, 15)) segments.push(time);
    return segments;
  }
  startSelectionDrag(date: string, time: string, past: boolean, slot: Availability | undefined, event: PointerEvent) {
    if (past) return;
    event.preventDefault();
    this.suppressSelectionClick = true;
    if (slot) {
      this.editAvailability(slot);
      return;
    }
    const extendsCurrentSelection = this.editingDate === date && !this.editingAvailabilityId;
    const currentStart = this.startTime;
    this.error.set('');
    this.selectedDay.set(date);
    this.editingDate = date;
    this.editingAvailabilityId = null;
    this.selectionDragActive = true;
    this.selectionDragDate = date;
    this.selectionDragAnchor = extendsCurrentSelection ? currentStart : time;
    this.startTime = this.selectionDragAnchor;
    this.endTime = this.addMinutes(time, 15);
    this.continueSelectionDrag(date, time, false);
  }
  continueSelectionDrag(date: string, time: string, past: boolean, slot?: Availability) {
    if (!this.selectionDragActive || past || slot || date !== this.selectionDragDate) return;
    if (time < this.selectionDragAnchor) {
      this.startTime = time;
      this.endTime = this.addMinutes(this.selectionDragAnchor, 15);
    } else {
      this.startTime = this.selectionDragAnchor;
      this.endTime = this.addMinutes(time, 15);
    }
  }
  finishSelectionDrag() {
    this.selectionDragActive = false;
    this.selectionDragDate = '';
    this.selectionDragAnchor = '';
  }
  handleSelectionClick(date: string, time: string, past: boolean, slot?: Availability) {
    if (this.suppressSelectionClick) {
      this.suppressSelectionClick = false;
      return;
    }
    if (slot) this.editAvailability(slot);
    else this.selectTimeCell(date, time, past);
  }
  selectDay(date: string) {
    this.selectedDay.set(date);
  }
  slotAt(date: string, time: string) {
    return this.slotsFor(date).find(
      (slot) => time >= this.timeLabel(slot.startTime) && time < this.timeLabel(slot.endTime),
    );
  }
  isSlotStart(slot: Availability, time: string) {
    return this.timeLabel(slot.startTime) === time;
  }
  isEditingTime(date: string, time: string) {
    return this.pendingIntervals().some(interval => interval.date === date && time >= interval.startTime && time < interval.endTime)
      || (this.selectionStart()?.date === date && this.selectionStart()?.time === time);
  }
  removePendingInterval(index: number) { this.pendingIntervals.update(items => items.filter((_, itemIndex) => itemIndex !== index)); }
  private minutesBetween(start: string, end: string) {
    const value = (time: string) => { const [hours, minutes] = time.split(':').map(Number); return hours * 60 + minutes; };
    return value(end) - value(start);
  }
  addMinutes(time: string, amount: number) {
    const [hours, minutes] = time.split(':').map(Number);
    const total = Math.min(hours * 60 + minutes + amount, 21 * 60);
    return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
  }
  cellLabel(day: { day: string; label: string }, time: string, slot?: Availability) {
    return slot
      ? `Disponível em ${day.day}, ${day.label}, das ${this.timeLabel(slot.startTime)} às ${this.timeLabel(slot.endTime)}. Clique para editar.`
      : `Selecionar ${day.day}, ${day.label}, às ${time}`;
  }
  editingDateLabel() {
    if (!this.editingDate) return '';
    return this.localDate(this.editingDate).toLocaleDateString('pt-BR', {
      weekday: 'long', day: '2-digit', month: 'long',
    });
  }
  removeEditingAvailability() {
    const slot = this.profile().availability.find((item) => item.id === this.editingAvailabilityId);
    if (slot) this.removeAvailability(slot);
  }
  toggleMiniCalendar() {
    this.miniCalendarOpen.update((open) => !open);
    if (this.miniCalendarOpen()) this.calendarMonth.set(this.selectedDay().slice(0, 7));
  }
  changeCalendarMonth(offset: number) {
    const [year, month] = this.calendarMonth().split('-').map(Number);
    const date = new Date(year, month - 1 + offset, 1);
    this.calendarMonth.set(this.isoDate(date).slice(0, 7));
  }
  calendarMonthLabel() {
    const [year, month] = this.calendarMonth().split('-').map(Number);
    return new Date(year, month - 1, 1).toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' });
  }
  selectCalendarDate(date: string) {
    this.selectedDay.set(date);
    this.goToDate(date);
    this.miniCalendarOpen.set(false);
  }
  loadAvailabilityWeek() {
    const from = this.weekStart();
    const end = this.localDate(from);
    end.setDate(end.getDate() + 6);
    this.service
      .getAvailability(from, this.isoDate(end))
      .subscribe((slots) => this.profile.update((p) => ({ ...p, availability: slots })));
  }
  changeWeek(offset: number) {
    const date = this.localDate(this.weekStart());
    date.setDate(date.getDate() + offset * 7);
    this.weekStart.set(this.isoDate(date));
    this.selectedWeekDate = this.weekStart();
    this.selectedDay.set(this.weekStart());
    this.cancelAvailabilityEdit();
    this.loadAvailabilityWeek();
  }
  currentWeek() {
    this.weekStart.set(this.isoDate(this.startOfWeek(new Date())));
    this.selectedWeekDate = this.weekStart();
    this.selectedDay.set(this.isoDate(new Date()));
    this.cancelAvailabilityEdit();
    this.loadAvailabilityWeek();
  }
  goToDate(value: string) {
    if (!value) return;
    this.weekStart.set(this.isoDate(this.startOfWeek(this.localDate(value))));
    this.selectedWeekDate = value;
    this.selectedDay.set(value);
    this.cancelAvailabilityEdit();
    this.loadAvailabilityWeek();
  }
  weekLabel() {
    const start = this.localDate(this.weekStart());
    const end = new Date(start);
    end.setDate(end.getDate() + 6);
    return `${start.toLocaleDateString('pt-BR', { day: '2-digit', month: 'short' }).toUpperCase()} — ${end.toLocaleDateString('pt-BR', { day: '2-digit', month: 'short' }).toUpperCase()}`;
  }
  private startOfWeek(date: Date) {
    const copy = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    const day = copy.getDay() || 7;
    copy.setDate(copy.getDate() - day + 1);
    return copy;
  }
  private localDate(value: string) {
    const [year, month, day] = value.split('-').map(Number);
    return new Date(year, month - 1, day);
  }
  private isoDate(date: Date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
  timeLabel(time: string) {
    return time.slice(0, 5);
  }
}
