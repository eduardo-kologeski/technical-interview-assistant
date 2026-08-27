import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  Availability,
  Profile,
  ProfileService,
  ProficiencyLevel,
  TechnicalSkill,
} from './profile.service';

@Component({
  selector: 'app-profile',
  imports: [FormsModule],
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
              <h2>Disponibilidade semanal</h2>
              <span>Cadastre os períodos em que você normalmente pode participar.</span>
            </div>
          </div>
          <div class="availability-form">
            <label
              >Dia da semana<select [(ngModel)]="day">
                <option value="MONDAY">Segunda-feira</option>
                <option value="TUESDAY">Terça-feira</option>
                <option value="WEDNESDAY">Quarta-feira</option>
                <option value="THURSDAY">Quinta-feira</option>
                <option value="FRIDAY">Sexta-feira</option>
                <option value="SATURDAY">Sábado</option>
                <option value="SUNDAY">Domingo</option>
              </select></label
            ><label>Horário inicial<input type="time" [(ngModel)]="startTime" /></label
            ><label>Horário final<input type="time" [(ngModel)]="endTime" /></label
            ><button (click)="saveAvailability()">
              {{ editingAvailabilityId ? 'Atualizar' : 'Adicionar horário' }}
            </button>
            @if (editingAvailabilityId) {
              <button class="secondary" (click)="cancelAvailabilityEdit()">Cancelar</button>
            }
          </div>
          @if (error()) {
            <p class="error">{{ error() }}</p>
          }
          @if (profile().availability.length) {
            <div class="days">
              @for (group of availabilityGroups(); track group.day) {
                <article>
                  <h3>{{ dayLabel(group.day) }}</h3>
                  <div>
                    @for (slot of group.slots; track slot.id) {
                      <span class="slot"
                        ><b>{{ timeLabel(slot.startTime) }} — {{ timeLabel(slot.endTime) }}</b
                        ><button (click)="editAvailability(slot)">Editar</button
                        ><button class="remove" (click)="removeAvailability(slot)">
                          Remover
                        </button></span
                      >
                    }
                  </div>
                </article>
              }
            </div>
          } @else {
            <div class="empty">
              Informe quando você costuma estar disponível para entrevistas técnicas.
            </div>
          }
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
      .availability-form button {
        padding: 10px 14px;
        border: 0;
        border-radius: 8px;
        background: #165d7a;
        color: #fff;
        font-weight: 800;
      }
      .personal-grid,
      .skill-add,
      .availability-form {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 14px;
        margin-top: 20px;
      }
      .skill-add {
        grid-template-columns: 1fr 170px auto;
        align-items: end;
      }
      .availability-form {
        grid-template-columns: 1.3fr 1fr 1fr auto auto;
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
      .days {
        display: grid;
        gap: 12px;
        margin-top: 20px;
      }
      .days article {
        display: grid;
        grid-template-columns: 180px 1fr;
        gap: 15px;
        padding: 14px;
        border-top: 1px solid #e5eaed;
      }
      .days h3 {
        margin: 4px 0;
        font-size: 13px;
        letter-spacing: 0.06em;
      }
      .slot {
        display: flex;
        align-items: center;
        gap: 10px;
        margin-bottom: 7px;
      }
      .slot b {
        min-width: 125px;
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
        .skill-add,
        .availability-form {
          grid-template-columns: 1fr;
        }
        .skill-list article {
          grid-template-columns: 1fr;
        }
        .days article {
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
  day = 'MONDAY';
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
  readonly availabilityGroups = computed(() => {
    const slots = this.profile().availability;
    return [...new Set(slots.map((s) => s.dayOfWeek))].map((day) => ({
      day,
      slots: slots.filter((s) => s.dayOfWeek === day),
    }));
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
          this.day,
          this.startTime,
          this.endTime,
        )
      : this.service.addAvailability(this.day, this.startTime, this.endTime);
    request.subscribe({
      next: (slot) => {
        this.profile.update((p) => ({
          ...p,
          availability: [...p.availability.filter((item) => item.id !== slot.id), slot].sort(
            (a, b) =>
              a.dayOfWeek.localeCompare(b.dayOfWeek) || a.startTime.localeCompare(b.startTime),
          ),
        }));
        this.cancelAvailabilityEdit();
        this.error.set('');
      },
      error: (e) => this.error.set(e.error?.detail || 'Não foi possível adicionar o horário.'),
    });
  }
  editAvailability(slot: Availability) {
    this.editingAvailabilityId = slot.id;
    this.day = slot.dayOfWeek;
    this.startTime = slot.startTime;
    this.endTime = slot.endTime;
  }
  cancelAvailabilityEdit() {
    this.editingAvailabilityId = null;
    this.day = 'MONDAY';
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
  dayLabel(day: string) {
    return (
      (
        {
          MONDAY: 'SEGUNDA-FEIRA',
          TUESDAY: 'TERÇA-FEIRA',
          WEDNESDAY: 'QUARTA-FEIRA',
          THURSDAY: 'QUINTA-FEIRA',
          FRIDAY: 'SEXTA-FEIRA',
          SATURDAY: 'SÁBADO',
          SUNDAY: 'DOMINGO',
        } as Record<string, string>
      )[day] || day
    );
  }
  timeLabel(time: string) {
    return time.slice(0, 5);
  }
}
