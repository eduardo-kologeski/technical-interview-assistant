import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

export type EvaluationSource = 'JOB_REQUIREMENT' | 'COMPLEMENTARY_EXPERIENCE';

type QuickOption = { label: string; text: string; tone: 'positive' | 'neutral' | 'negative' };

@Component({
  selector: 'app-skill-evaluation-card',
  imports: [CommonModule, FormsModule],
  template: `
    <article class="skill-card" [class.compact]="compact" [class.active]="active">
      <div class="skill-heading">
        <div><h3>{{ name }}</h3><span class="tag" [class.complementary]="source === 'COMPLEMENTARY_EXPERIENCE'">{{ sourceLabel }}</span></div>
        @if (contextLabel) { <span class="expectation">{{ contextLabel }}</span> }
      </div>
      @if (prompt) { <p class="kit-question"><strong>Pergunta sugerida</strong>{{ prompt }}</p> }
      @if (rubric) { <details class="kit-rubric"><summary>Ver rubrica</summary><p>{{ rubric }}</p></details> }
      <div class="rating" role="radiogroup" [attr.aria-label]="'Nota de ' + name">
        @for (value of scores; track value) {
          <button type="button" role="radio" (click)="scoreChange.emit(value)" [class.selected]="score === value && !notEvaluated" [attr.aria-checked]="score === value && !notEvaluated" [attr.aria-label]="name + ': ' + value + ' de 5'">{{ value }}</button>
        }
        <button type="button" role="radio" class="na" (click)="notEvaluatedChange.emit()" [class.selected]="notEvaluated" [attr.aria-checked]="notEvaluated" [attr.aria-label]="name + ': não avaliado'">N/A</button>
      </div>
      <label>Comentário<textarea rows="2" [ngModel]="comment" (ngModelChange)="commentChange.emit($event)" placeholder="Registre contexto ou evidências observadas"></textarea></label>
      <div class="quick-tags" aria-label="Opções rápidas de avaliação">
        @for (option of quickOptions; track option.label) {
          <button type="button" [class]="option.tone" (click)="quickOption.emit(option.text)">{{ option.label }}</button>
        }
      </div>
      @if (removable) { <button type="button" class="remove" (click)="remove.emit()" [attr.aria-label]="'Remover ' + name + ' das experiências complementares'">Remover tecnologia</button> }
    </article>
  `,
  styleUrl: './skill-evaluation-card.component.scss',
})
export class SkillEvaluationCardComponent {
  @Input({ required: true }) name = '';
  @Input({ required: true }) source: EvaluationSource = 'JOB_REQUIREMENT';
  @Input() score: number | null = null;
  @Input() notEvaluated = false;
  @Input() comment = '';
  @Input() contextLabel = '';
  @Input() prompt = '';
  @Input() rubric = '';
  @Input() removable = false;
  @Input() compact = false;
  @Input() active = false;
  @Output() scoreChange = new EventEmitter<number>();
  @Output() notEvaluatedChange = new EventEmitter<void>();
  @Output() commentChange = new EventEmitter<string>();
  @Output() quickOption = new EventEmitter<string>();
  @Output() remove = new EventEmitter<void>();

  readonly scores = [0, 1, 2, 3, 4, 5];
  readonly quickOptions: QuickOption[] = [
    { label: 'Experiência sólida', text: 'Experiência sólida.', tone: 'positive' },
    { label: 'Forte experiência prática', text: 'Forte experiência prática.', tone: 'positive' },
    { label: 'Boa experiência', text: 'Boa experiência.', tone: 'positive' },
    { label: 'Domínio técnico', text: 'Demonstrou domínio técnico.', tone: 'positive' },
    { label: 'Resposta completa', text: 'Resposta completa e bem fundamentada.', tone: 'positive' },
    { label: 'Vivência em produção', text: 'Demonstrou vivência em produção.', tone: 'positive' },
    { label: 'Experiência razoável', text: 'Experiência razoável.', tone: 'neutral' },
    { label: 'Conhecimento intermediário', text: 'Demonstrou conhecimento intermediário.', tone: 'neutral' },
    { label: 'Conhecimento mais teórico', text: 'Conhecimento principalmente teórico.', tone: 'neutral' },
    { label: 'Experiência limitada', text: 'Experiência prática limitada.', tone: 'neutral' },
    { label: 'Necessita aprofundamento', text: 'Necessita aprofundamento.', tone: 'neutral' },
    { label: 'Pouca experiência prática', text: 'Pouca experiência prática.', tone: 'negative' },
    { label: 'Conhecimento superficial', text: 'Demonstrou conhecimento superficial.', tone: 'negative' },
    { label: 'Resposta insuficiente', text: 'Resposta insuficiente.', tone: 'negative' },
    { label: 'Não soube responder', text: 'Não soube responder.', tone: 'negative' },
    { label: 'Sem experiência prática', text: 'Sem experiência prática.', tone: 'negative' },
    { label: 'Não demonstrou domínio', text: 'Não demonstrou domínio.', tone: 'negative' },
  ];

  get sourceLabel() { return this.source === 'JOB_REQUIREMENT' ? 'Requisito da vaga' : 'Experiência complementar'; }
}
