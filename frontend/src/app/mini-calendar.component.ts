import { Component, EventEmitter, Input, Output } from '@angular/core';

export interface MiniCalendarDay {
  date: string;
  number: number;
  currentMonth: boolean;
  today: boolean;
}

@Component({
  selector: 'app-mini-calendar',
  template: `
    <section class="mini-calendar" aria-label="Escolher uma data">
      <header>
        <button class="icon" aria-label="Mês anterior" (click)="monthChange.emit(-1)">‹</button>
        <strong>{{ monthLabel }}</strong>
        <button class="icon" aria-label="Próximo mês" (click)="monthChange.emit(1)">›</button>
        <button class="minimize" (click)="dismissed.emit()">Minimizar</button>
      </header>
      <div class="weekdays" aria-hidden="true">
        @for (day of weekdays; track day) { <span>{{ day }}</span> }
      </div>
      <div class="days">
        @for (day of days; track day.date) {
          <button
            [class.outside]="!day.currentMonth"
            [class.selected]="day.date === selectedDate"
            [class.today-date]="day.today"
            (click)="dateSelected.emit(day.date)"
          >{{ day.number }}</button>
        }
      </div>
    </section>
  `,
  styles: `
    :host{display:block}.mini-calendar{box-sizing:border-box;width:300px;margin-top:10px;padding:14px;border:1px solid #d4dfe3;border-radius:10px;background:#fff;box-shadow:0 10px 28px #1737451f}header{display:grid;grid-template-columns:auto 1fr auto auto;align-items:center;gap:5px}header strong{text-align:center;font-size:13px}button{font:inherit}.icon{width:34px;height:34px;border:1px solid #ccd7dc;border-radius:7px;background:#fff;color:#176b66;font-size:22px}.minimize{border:0;background:transparent;color:#176b66;font-size:11px}.weekdays,.days{display:grid;grid-template-columns:repeat(7,1fr);gap:2px}.weekdays{margin:12px 0 4px}.weekdays span{color:#708087;font-size:9px;font-weight:800;text-align:center}.days button{aspect-ratio:1;border:0;border-radius:50%;background:transparent;color:#263d46;font-size:11px}.days button:hover{background:#edf5f4}.days button.outside{color:#a8b2b7}.days button.today-date{outline:1px solid #176b66}.days button.selected{background:#176b66;color:#fff}@media(max-width:760px){.mini-calendar{width:100%}}
  `,
})
export class MiniCalendarComponent {
  @Input({ required: true }) monthLabel = '';
  @Input({ required: true }) days: MiniCalendarDay[] = [];
  @Input({ required: true }) selectedDate = '';
  @Input() weekdays: string[] = [];
  @Output() readonly monthChange = new EventEmitter<number>();
  @Output() readonly dateSelected = new EventEmitter<string>();
  @Output() readonly dismissed = new EventEmitter<void>();
}
