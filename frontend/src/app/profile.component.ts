import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
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
  imports: [FormsModule, OpenNativePickerDirective],
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
          <div class="week-navigation">
            <button class="secondary" (click)="currentWeek()">Semana atual</button>
            <div>
              <button class="icon" aria-label="Semana anterior" (click)="changeWeek(-1)">‹</button
              ><strong>{{ weekLabel() }}</strong
              ><button class="icon" aria-label="Próxima semana" (click)="changeWeek(1)">›</button>
            </div>
            <label
              >Ir para data<input
                type="date"
                [ngModel]="selectedWeekDate"
                (ngModelChange)="goToDate($event)"
            /></label>
          </div>
          @if (error()) {
            <p class="error">{{ error() }}</p>
          }
          <div class="week-days">
            @for (dayItem of weekDays(); track dayItem.date) {
              <article [class.past]="dayItem.past">
                <header>
                  <div>
                    <strong>{{ dayItem.day }}</strong
                    ><span>{{ dayItem.label }}</span>
                  </div>
                  @if (!dayItem.past) {
                    <button class="add-time" (click)="openAvailability(dayItem.date)">
                      + Horário
                    </button>
                  }
                </header>
                <div class="day-slots">
                  @for (slot of slotsFor(dayItem.date); track slot.id) {
                    <span class="slot"
                      ><b>{{ timeLabel(slot.startTime) }} — {{ timeLabel(slot.endTime) }}</b
                      ><button (click)="editAvailability(slot)">Editar</button
                      ><button class="remove" (click)="removeAvailability(slot)">
                        Remover
                      </button></span
                    >
                  } @empty {
                    <small>Nenhum horário cadastrado</small>
                  }
                </div>
                @if (editingDate === dayItem.date) {
                  <div class="inline-time-form">
                    <label>Início<input type="time" [(ngModel)]="startTime" /></label
                    ><label>Fim<input type="time" [(ngModel)]="endTime" /></label
                    ><button (click)="saveAvailability()">
                      {{ editingAvailabilityId ? 'Atualizar' : 'Adicionar' }}</button
                    ><button class="secondary" (click)="cancelAvailabilityEdit()">Cancelar</button>
                  </div>
                }
              </article>
            }
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
        color: #278d83;
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
      .skill-add button,
      .week-navigation > button {
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
        color: #6c7b84;
        background: #fafcfd;
      }
      .week-navigation {
        display: grid;
        grid-template-columns: auto 1fr auto;
        align-items: end;
        gap: 14px;
        margin-top: 20px;
      }
      .week-navigation > div {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 14px;
      }
      .week-navigation .icon {
        width: 34px;
        height: 34px;
        border: 1px solid #ccd7dc;
        border-radius: 50%;
        background: #fff;
        color: #176b66;
        font-size: 22px;
      }
      .week-days {
        display: grid;
        grid-template-columns: repeat(3, minmax(0, 1fr));
        gap: 10px;
        margin-top: 20px;
      }
      .week-days article {
        display: grid;
        align-content: start;
        gap: 10px;
        min-height: 145px;
        padding: 12px;
        border: 1px solid #e0e7eb;
        border-radius: 9px;
        background: #fbfcfc;
      }
      .week-days article.past {
        opacity: 0.65;
      }
      .week-days article > header {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        gap: 8px;
      }
      .week-days article > header div {
        display: grid;
        gap: 2px;
      }
      .week-days article > header strong {
        font-size: 12px;
        letter-spacing: 0.07em;
      }
      .week-days article > header span {
        color: #6d7b84;
        font-size: 11px;
      }
      .add-time {
        border: 0;
        background: transparent;
        color: #176b66;
        font-size: 11px;
        font-weight: 800;
      }
      .day-slots {
        display: grid;
        gap: 6px;
      }
      .day-slots small {
        color: #7b878e;
      }
      .inline-time-form {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 7px;
        padding-top: 8px;
        border-top: 1px solid #e2e8eb;
      }
      .inline-time-form button {
        padding: 7px;
        border: 0;
        border-radius: 6px;
        background: #165d7a;
        color: #fff;
        font-weight: 750;
      }
      .inline-time-form label {
        font-size: 10px;
      }
      .inline-time-form input {
        padding: 7px;
      }
      .slot {
        display: grid;
        grid-template-columns: 1fr auto auto;
        align-items: center;
        gap: 6px;
        padding: 6px 7px;
        border-radius: 6px;
        background: #eef5f5;
        font-size: 10px;
      }
      .slot button {
        border: 0;
        background: transparent;
        color: #176b66;
      }
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
        .week-navigation {
          grid-template-columns: 1fr;
          align-items: start;
        }
        .week-navigation > div {
          justify-content: flex-start;
        }
        .week-days {
          grid-template-columns: 1fr;
        }
        .section-title {
          flex-direction: column;
        }
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
  selectedWeekDate = this.weekStart();
  editingDate = '';
  startTime = '14:00';
  endTime = '17:00';
  editingAvailabilityId: number | null = null;
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
        past: iso < today,
      };
    });
  });
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
  }
  removeAvailability(slot: Availability) {
    this.service.removeAvailability(slot.id).subscribe(() =>
      this.profile.update((p) => ({
        ...p,
        availability: p.availability.filter((item) => item.id !== slot.id),
      })),
    );
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
    this.cancelAvailabilityEdit();
    this.loadAvailabilityWeek();
  }
  currentWeek() {
    this.weekStart.set(this.isoDate(this.startOfWeek(new Date())));
    this.selectedWeekDate = this.weekStart();
    this.cancelAvailabilityEdit();
    this.loadAvailabilityWeek();
  }
  goToDate(value: string) {
    if (!value) return;
    this.weekStart.set(this.isoDate(this.startOfWeek(this.localDate(value))));
    this.selectedWeekDate = value;
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
