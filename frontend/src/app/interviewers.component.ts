import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { JobPosition, JobPositionService } from './job-position.service';
import { InterviewerResult, ProfileService, ProficiencyLevel } from './profile.service';
import { OpenNativePickerDirective } from './open-native-picker.directive';

@Component({
  selector: 'app-interviewers',
  imports: [FormsModule, OpenNativePickerDirective],
  template: `
    <section class="page">
      <header>
        <p>ENTREVISTADORES</p>
        <h1>Encontrar entrevistador</h1>
        <span>Compare conhecimentos relacionados e disponibilidade para apoiar sua decisão.</span>
      </header>
      <section class="filters">
        <label
          >Vaga<select [(ngModel)]="vacancyId" (ngModelChange)="search()">
            <option value="">Todas / sem vaga</option>
            @for (vacancy of vacancies(); track vacancy.id) {
              <option [value]="vacancy.id">{{ vacancy.title }}</option>
            }
          </select></label
        ><label
          >Tecnologia<input
            [(ngModel)]="technology"
            (keyup.enter)="search()"
            placeholder="Ex.: Java ou Playwright" /></label
        ><label>Data<input type="date" [(ngModel)]="date" (ngModelChange)="search()" /></label
        ><label>Horário<input type="time" [(ngModel)]="time" /></label
        ><button (click)="search()">Buscar</button>
      </section>
      @if (selectedVacancy()) {
        <section class="requirements">
          <strong>Requisitos da vaga</strong>
          <div>
            @for (req of selectedVacancy()!.requirements; track req) {
              <span>{{ req }}</span>
            }
          </div>
        </section>
      }
      <div class="results">
        @for (person of interviewers(); track person.id) {
          <article>
            <div class="person">
              <div>
                <h2>{{ person.name }}</h2>
                @if (person.requirementCount) {
                  <strong
                    >{{ person.matchedCount }} de {{ person.requirementCount }} requisitos
                    relacionados</strong
                  >
                } @else {
                  <span>Perfil técnico e próximas disponibilidades</span>
                }
              </div>
              @if (person.requirementCount) {
                <span
                  class="compatibility"
                  [class.high]="person.matchedCount === person.requirementCount"
                  >{{ person.matchedCount }}/{{ person.requirementCount }}</span
                >
              }
            </div>
            @if (person.requirementCount) {
              <div class="matches">
                @for (match of person.requirementMatches; track match.name) {
                  <span [class.missing]="!match.matched"
                    ><b>{{ match.matched ? '✓' : '×' }}</b
                    >{{ match.name
                    }}<small>{{
                      match.matched ? levelLabel(match.proficiencyLevel) : 'Não informado'
                    }}</small></span
                  >
                }
              </div>
            } @else if (person.allSkills.length) {
              <div class="skills">
                @for (skill of person.allSkills.slice(0, 6); track skill.id) {
                  <span>{{ skill.name }} · {{ levelLabel(skill.proficiencyLevel) }}</span>
                }
              </div>
            } @else {
              <p class="muted">Nenhum conhecimento técnico cadastrado.</p>
            }
            <div class="availability">
              <strong>Próximas disponibilidades</strong>
              @if (person.availability.length) {
                <div>
                  @for (slot of person.availability; track slot.id) {
                    <span
                      >{{ dateLabel(slot.availabilityDate) }} {{ timeLabel(slot.startTime) }}–{{
                        timeLabel(slot.endTime)
                      }}</span
                    >
                  }
                </div>
              } @else {
                <p class="muted">Nenhuma disponibilidade informada.</p>
              }
            </div>
          </article>
        } @empty {
          <div class="empty">Nenhum entrevistador encontrado para os filtros selecionados.</div>
        }
      </div>
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .page {
        max-width: 1120px;
        margin: auto;
        padding: 38px 30px 70px;
      }
      header p {
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
      header span {
        color: #697881;
      }
      .filters {
        display: grid;
        grid-template-columns: 1.5fr 1fr 1fr 0.8fr auto;
        gap: 12px;
        align-items: end;
        margin-top: 28px;
        padding: 20px;
        background: #fff;
        border: 1px solid #dce5ea;
        border-radius: 12px;
      }
      .filters label {
        display: grid;
        gap: 6px;
        color: #53626c;
        font-size: 12px;
        font-weight: 750;
      }
      .filters input,
      .filters select {
        width: 100%;
        padding: 10px;
        border: 1px solid #d2dce2;
        border-radius: 7px;
        background: #fff;
        font: inherit;
      }
      .filters button {
        padding: 11px 17px;
        border: 0;
        border-radius: 8px;
        background: #165d7a;
        color: #fff;
        font-weight: 800;
      }
      .requirements {
        display: flex;
        align-items: center;
        gap: 16px;
        margin-top: 16px;
        padding: 15px 18px;
        background: #eaf4f3;
        border-radius: 10px;
        color: #245c58;
      }
      .requirements div,
      .skills {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
      }
      .requirements span,
      .skills span {
        padding: 5px 8px;
        border-radius: 13px;
        background: #fff;
        font-size: 11px;
      }
      .results {
        display: grid;
        gap: 15px;
        margin-top: 20px;
      }
      .results article {
        display: grid;
        gap: 17px;
        padding: 22px;
        background: #fff;
        border: 1px solid #dce5ea;
        border-radius: 12px;
        box-shadow: 0 4px 16px #1434460a;
      }
      .person {
        display: flex;
        justify-content: space-between;
        align-items: center;
      }
      .person h2 {
        margin: 0 0 5px;
      }
      .person strong {
        color: #176b66;
        font-size: 13px;
      }
      .compatibility {
        display: grid;
        place-items: center;
        width: 54px;
        height: 54px;
        border-radius: 50%;
        background: #fff2d9;
        color: #916119;
        font-weight: 900;
      }
      .compatibility.high {
        background: #e2f4ec;
        color: #23744f;
      }
      .matches {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
        gap: 7px;
      }
      .matches span {
        display: grid;
        grid-template-columns: 18px 1fr auto;
        align-items: center;
        padding: 9px;
        border-radius: 7px;
        background: #edf7f2;
        color: #245e4d;
        font-size: 12px;
      }
      .matches span.missing {
        background: #f5f6f7;
        color: #5c6973;
      }
      .matches small {
        font-weight: 700;
      }
      .availability {
        padding-top: 13px;
        border-top: 1px solid #e7ecef;
      }
      .availability div {
        display: flex;
        flex-wrap: wrap;
        gap: 7px;
        margin-top: 9px;
      }
      .availability div span {
        padding: 6px 9px;
        border-radius: 14px;
        background: #eef4f7;
        color: #405e6d;
        font-size: 11px;
      }
      .muted {
        margin: 5px 0;
        color: #5c6973;
      }
      .empty {
        padding: 30px;
        text-align: center;
        border: 1px dashed #cdd9df;
        border-radius: 10px;
        color: #697881;
      }
      @media (max-width: 850px) {
        .filters {
          grid-template-columns: 1fr 1fr;
        }
        .filters button {
          grid-column: 1/-1;
        }
        .requirements {
          align-items: flex-start;
          flex-direction: column;
        }
      }
      @media (max-width: 560px) {
        .page {
          padding: 25px 14px;
        }
        .filters {
          grid-template-columns: 1fr;
        }
        .person {
          align-items: flex-start;
        }
        .matches {
          grid-template-columns: 1fr;
        }
      }
    `,
  ],
})
export class InterviewersComponent {
  private readonly profiles = inject(ProfileService);
  private readonly positions = inject(JobPositionService);
  readonly vacancies = signal<JobPosition[]>([]);
  readonly interviewers = signal<InterviewerResult[]>([]);
  vacancyId = '';
  technology = '';
  date = '';
  time = '';
  constructor() {
    this.positions.list(true).subscribe((items) => this.vacancies.set(items));
    this.search();
  }
  selectedVacancy() {
    return this.vacancies().find((item) => item.id === this.vacancyId);
  }
  search() {
    this.profiles
      .searchInterviewers({
        vacancyId: this.vacancyId,
        technology: this.technology.trim(),
        date: this.date,
        time: this.time,
      })
      .subscribe((items) => this.interviewers.set(items));
  }
  levelLabel(level: ProficiencyLevel | null) {
    return level === 'ADVANCED'
      ? 'Avançado'
      : level === 'INTERMEDIATE'
        ? 'Intermediário'
        : level === 'BEGINNER'
          ? 'Básico'
          : '';
  }
  dateLabel(value: string) {
    const [year, month, day] = value.split('-').map(Number);
    const date = new Date(year, month - 1, day);
    const weekday = date.toLocaleDateString('pt-BR', { weekday: 'short' }).replace('.', '');
    return `${weekday.charAt(0).toUpperCase() + weekday.slice(1)}, ${String(day).padStart(2, '0')}/${String(month).padStart(2, '0')}`;
  }
  timeLabel(time: string) {
    return time.slice(0, 5);
  }
}
