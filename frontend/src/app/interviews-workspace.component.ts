import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { InterviewRecord, InterviewService } from './interview.service';
import { InterviewersComponent } from './interviewers.component';
import { ProfileComponent } from './profile.component';

@Component({
  selector: 'app-interviews-workspace',
  imports: [DatePipe, RouterLink, InterviewersComponent, ProfileComponent],
  template: `<section class="page">
    <header class="heading"><div><p>OPERAÇÃO</p><h1>Entrevistas</h1><span>Agenda, equipe e sua disponibilidade em um só lugar.</span></div>
      @if (tab() === 'agenda') { <a class="primary" routerLink="/interviews/new">+ Nova entrevista</a> }
    </header>
    <nav class="tabs" aria-label="Áreas de entrevistas">
      <button [class.active]="tab()==='agenda'" (click)="tab.set('agenda')">Agenda</button>
      <button [class.active]="tab()==='team'" (click)="tab.set('team')">Entrevistadores</button>
      <button [class.active]="tab()==='availability'" (click)="tab.set('availability')">Minha disponibilidade</button>
    </nav>
    @if (tab() === 'agenda') {
      <div class="toolbar"><div class="filters" role="group" aria-label="Filtrar entrevistas">
        @for (item of filters; track item.value) { <button [class.active]="filter()===item.value" (click)="setFilter(item.value)">{{item.label}}</button> }
      </div><span>{{ interviews().length }} {{ interviews().length === 1 ? 'entrevista' : 'entrevistas' }}</span></div>
      @if (loading()) { <div class="state">Carregando entrevistas...</div> }
      @else if (error()) { <div class="state error">{{error()}} <button (click)="load()">Tentar novamente</button></div> }
      @else { <div class="cards">
        @for (interview of interviews(); track interview.id) {
          <article><div class="avatar">{{ initials(interview.candidateName) }}</div><div class="main"><div><span class="status" [attr.data-status]="interview.status">{{ statusLabel(interview.status) }}</span><h2>{{interview.candidateName}}</h2></div><p>{{interview.vacancyTitleSnapshot}} · {{interview.senioritySnapshot}}</p><small>Iniciada em {{interview.startedAt | date:'dd/MM/yyyy, HH:mm'}}</small></div><a [routerLink]="['/interviews', interview.id]">{{interview.status === 'COMPLETED' ? 'Ver parecer' : 'Continuar'}} →</a></article>
        } @empty { <div class="empty"><strong>Nenhuma entrevista por aqui</strong><span>{{filter() ? 'Altere o filtro para consultar outros status.' : 'Crie uma entrevista a partir de uma vaga ativa.'}}</span><a routerLink="/interviews/new">Criar entrevista</a></div> }
      </div> }
    } @else if (tab() === 'team') { <div class="embedded"><app-interviewers /></div> }
    @else { <div class="embedded"><app-profile /></div> }
  </section>`,
  styles: [`
    .page{max-width:1120px;margin:auto;padding:42px 28px}.heading{display:flex;align-items:flex-end;justify-content:space-between;gap:20px}.heading p{margin:0;color:#176b66;font-size:11px;font-weight:900;letter-spacing:.14em}.heading h1{margin:7px 0;font-size:36px}.heading span,.toolbar>span{color:#64747d}.primary,.empty a{padding:12px 16px;border-radius:8px;background:#176b66;color:#fff;text-decoration:none;font-weight:800}.tabs{display:flex;gap:6px;margin-top:30px;padding:5px;border:1px solid #dce5e8;border-radius:11px;background:#fff;width:max-content}.tabs button,.filters button{border:0;border-radius:7px;background:transparent;color:#617079;padding:9px 14px;font-weight:800}.tabs button.active,.filters button.active{background:#e8f4f2;color:#176b66}.toolbar{display:flex;align-items:center;justify-content:space-between;margin:24px 0 14px}.filters{display:flex;gap:6px}.cards{display:grid;gap:10px}.cards article{display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:15px;padding:18px;background:#fff;border:1px solid #dce5e8;border-radius:11px}.avatar{display:grid;place-items:center;width:44px;height:44px;border-radius:50%;background:#e8f4f2;color:#176b66;font-weight:900}.main>div{display:flex;align-items:center;gap:10px}.main h2{margin:0;font-size:17px}.main p,.main small{margin:5px 0 0;color:#64747d}.main small{font-size:11px}.status{padding:4px 7px;border-radius:999px;background:#fff2da;color:#8a641a;font-size:10px;font-weight:900}.status[data-status="COMPLETED"]{background:#e9f1f4;color:#536b76}.cards article>a{color:#176b66;text-decoration:none;font-weight:850}.state,.empty{display:grid;place-items:center;gap:10px;padding:55px;border:1px dashed #cbd9de;border-radius:11px;color:#64747d;background:#fff}.embedded{margin:0 -28px}.embedded app-interviewers,.embedded app-profile{display:block}.embedded ::ng-deep .page{padding-top:26px}.embedded ::ng-deep .page>h1,.embedded ::ng-deep .page>.title{ }.error{color:#a44035}@media(max-width:650px){.page{padding:28px 14px}.heading{align-items:flex-start;flex-direction:column}.tabs{width:100%;overflow:auto}.toolbar{align-items:flex-start;gap:12px;flex-direction:column}.filters{overflow:auto;max-width:100%}.cards article{grid-template-columns:auto 1fr}.cards article>a{grid-column:2}.embedded{margin:0 -14px}}
  `]
})
export class InterviewsWorkspaceComponent {
  private service=inject(InterviewService);
  readonly tab=signal<'agenda'|'team'|'availability'>('agenda'); readonly filter=signal(''); readonly interviews=signal<InterviewRecord[]>([]); readonly loading=signal(true); readonly error=signal('');
  readonly filters=[{label:'Todas',value:''},{label:'Em andamento',value:'IN_PROGRESS'},{label:'Finalizadas',value:'COMPLETED'}];
  constructor(){this.load()} setFilter(value:string){this.filter.set(value);this.load()} load(){this.loading.set(true);this.error.set('');this.service.list(0,50,this.filter()||undefined).subscribe({next:r=>{this.interviews.set(r.content);this.loading.set(false)},error:()=>{this.error.set('Não foi possível carregar as entrevistas.');this.loading.set(false)}})}
  initials(name:string){return name.split(/\s+/).slice(0,2).map(p=>p[0]).join('').toUpperCase()} statusLabel(status:string){return status==='COMPLETED'?'Finalizada':status==='IN_PROGRESS'?'Em andamento':'Rascunho'}
}
