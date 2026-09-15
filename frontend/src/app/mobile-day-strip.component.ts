import { Component, EventEmitter, Input, Output } from '@angular/core';

export interface AvailabilityWeekDay {
  date: string;
  day: string;
  dateNumber: number;
}

@Component({
  selector: 'app-mobile-day-strip',
  template: `
    <div class="mobile-day-strip" aria-label="Dias da semana">
      @for (dayItem of days; track dayItem.date) {
        <button
          [class.active]="dayItem.date === selectedDate"
          (click)="dateSelected.emit(dayItem.date)"
        >
          <span>{{ dayItem.day }}</span><b>{{ dayItem.dateNumber }}</b>
        </button>
      }
    </div>
  `,
  styles: `
    :host{display:none}@media(max-width:760px){:host{display:block}.mobile-day-strip{display:grid;grid-template-columns:repeat(5,1fr);margin-top:14px;border-bottom:1px solid #dce5e8}.mobile-day-strip button{display:grid;place-items:center;gap:4px;padding:9px 2px;border:0;border-bottom:3px solid transparent;background:transparent;color:#63747c;font:inherit}.mobile-day-strip span{font-size:9px;font-weight:850}.mobile-day-strip b{font-size:15px}.mobile-day-strip button.active{border-bottom-color:#176b66;color:#176b66}}
  `,
})
export class MobileDayStripComponent {
  @Input({ required: true }) days: AvailabilityWeekDay[] = [];
  @Input({ required: true }) selectedDate = '';
  @Output() readonly dateSelected = new EventEmitter<string>();
}
