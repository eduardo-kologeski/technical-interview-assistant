import { Component, OnDestroy, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EMPTY, Subject, Subscription, catchError, concatMap, debounceTime, finalize, forkJoin, tap } from 'rxjs';
import {
  PersonalAssessmentCollection, PersonalAssessmentCriterion, PersonalAssessmentDocument,
  PersonalAssessmentFinding, PersonalAssessmentService, PersonalAssessmentSummary,
} from './personal-assessment.service';

type SaveRequest = { revision: number; document: PersonalAssessmentDocument };

@Component({
  selector: 'app-personal-assessment-collection',
  imports: [FormsModule, RouterLink],
  template: `<section class="page">
    <a routerLink="/dashboard" class="back">← Minhas avaliações</a>
    @if (loading()) { <p class="state">Carregando avaliação…</p> }
    @else if (loadError()) { <p class="error" role="alert">{{ loadError() }}</p> }
    @else if (assessment(); as current) {
      <header><div><p class="eyebrow">AVALIAÇÃO TÉCNICA</p><h1>{{ current.title }}</h1><span>{{ current.personName }} @if(current.context){ · {{ current.context }} }</span></div>
        <div class="save-state" [class.failed]="saveError()">{{ saveLabel() }}</div>
      </header>
      @if (collection()?.status === 'CREATED') {
        <section class="welcome"><h2>Prepare a coleta</h2><p>Adicione critérios e registre evidências durante a conversa. Pontuação é opcional e nunca determina uma decisão.</p><button (click)="start()" [disabled]="working()">{{ working() ? 'Iniciando…' : 'Iniciar coleta' }}</button></section>
      } @else {
        @if (saveError()) { <div class="error save-error" role="alert"><span>{{ saveError() }}</span><button (click)="retrySave()">Tentar novamente</button></div> }
        @if (collection()?.status === 'COMPLETED') { <div class="completed"><strong>Coleta encerrada</strong><span>{{ collection()?.generationEligible ? 'Os dados mínimos para uma futura minuta estão presentes.' : 'Não há critério com evidência suficiente para gerar uma minuta.' }}</span></div> }
        <div class="toolbar"><div><h2>Critérios e evidências</h2><p>Registre somente o que foi observado.</p></div>@if(!readOnly()){<div class="add"><input [(ngModel)]="newCriterion" placeholder="Novo critério" aria-label="Novo critério"><button (click)="addCriterion()" [disabled]="!newCriterion.trim()">Adicionar</button></div>}</div>
        <div class="criteria">@for (criterion of document().criteria; track criterion.id) {
          <article>
            <div class="criterion-head"><input aria-label="Nome do critério" [disabled]="readOnly()" [ngModel]="criterion.name" (ngModelChange)="updateCriterion(criterion.id,{name:$event})"><button class="remove" [hidden]="readOnly()" (click)="removeCriterion(criterion.id)">Remover</button></div>
            <div class="criterion-meta"><label>Tecnologia<input [disabled]="readOnly()" [ngModel]="criterion.technology" (ngModelChange)="updateCriterion(criterion.id,{technology:$event||null})"></label><label>Pontuação opcional<select [disabled]="readOnly()" [ngModel]="criterion.score ?? ''" (ngModelChange)="updateScore(criterion.id,$event)"><option value="">Sem nota</option>@for(score of [0,1,2,3,4,5];track score){<option [value]="score">{{score}}</option>}</select></label></div>
            <label>Descrição<textarea [disabled]="readOnly()" [ngModel]="criterion.description" (ngModelChange)="updateCriterion(criterion.id,{description:$event||null})" rows="2"></textarea></label>
            <h3>Evidências</h3>
            @for (item of evidenceFor(criterion.id); track item.id) { <div class="entry"><textarea aria-label="Evidência" [disabled]="readOnly()" [ngModel]="item.text" (ngModelChange)="updateEvidence(item.id,$event)" rows="2"></textarea><button [hidden]="readOnly()" (click)="removeEvidence(item.id)">×</button></div> }
            @if(!readOnly()){<div class="add-entry"><textarea [(ngModel)]="evidenceDrafts[criterion.id]" rows="2" placeholder="Evidência observada para este critério"></textarea><button (click)="addEvidence(criterion.id)" [disabled]="!evidenceDrafts[criterion.id]?.trim()">Registrar evidência</button></div>}
          </article>
        } @empty { <p class="empty">Nenhum critério definido. A coleta pode ser salva vazia, mas não ficará elegível para geração.</p> }</div>
        <section class="support"><h2>Registros de apoio</h2><div class="support-grid">
          <div><h3>Notas gerais</h3>@for(item of document().notes;track item.id){<div class="entry"><textarea [disabled]="readOnly()" [ngModel]="item.text" (ngModelChange)="updateNote(item.id,$event)"></textarea><button [hidden]="readOnly()" (click)="removeNote(item.id)">×</button></div>}@if(!readOnly()){<div class="add-entry"><textarea [(ngModel)]="noteDraft" placeholder="Observação geral"></textarea><button (click)="addNote()" [disabled]="!noteDraft.trim()">Adicionar</button></div>}</div>
          <div><h3>Pontos positivos</h3>@for(item of document().strengths;track item.id){<div class="entry"><textarea [disabled]="readOnly()" [ngModel]="item.text" (ngModelChange)="updateFinding('strengths',item.id,$event)"></textarea><button [hidden]="readOnly()" (click)="removeFinding('strengths',item.id)">×</button></div>}@if(!readOnly()){<div class="add-entry"><textarea [(ngModel)]="strengthDraft" placeholder="Ponto positivo observado"></textarea><button (click)="addFinding('strengths')" [disabled]="!strengthDraft.trim()">Adicionar</button></div>}</div>
          <div><h3>Pontos de atenção</h3>@for(item of document().concerns;track item.id){<div class="entry"><textarea [disabled]="readOnly()" [ngModel]="item.text" (ngModelChange)="updateFinding('concerns',item.id,$event)"></textarea><button [hidden]="readOnly()" (click)="removeFinding('concerns',item.id)">×</button></div>}@if(!readOnly()){<div class="add-entry"><textarea [(ngModel)]="concernDraft" placeholder="Ponto de atenção observado"></textarea><button (click)="addFinding('concerns')" [disabled]="!concernDraft.trim()">Adicionar</button></div>}</div>
        </div></section>
        @if(!readOnly()){<footer><span>@if(dirty()||pending()){Aguarde a confirmação do salvamento antes de encerrar.}@else{Todos os registros foram confirmados no servidor.}</span><button (click)="complete()" [disabled]="dirty()||pending()||!!saveError()||working()">Encerrar coleta</button></footer>}
      }
    }
  </section>`,
  styles: [`:host{display:block}.page{max-width:1050px;margin:auto;padding:34px 24px 80px}.back{color:#176b66;text-decoration:none;font-size:12px;font-weight:800}header{display:flex;justify-content:space-between;gap:20px;margin:28px 0}header h1{margin:5px 0;font-size:33px}header span,.toolbar p{color:#66767e}.eyebrow{margin:0;color:#278d83;font-size:10px;font-weight:900;letter-spacing:.14em}.save-state{align-self:start;padding:7px 11px;border-radius:14px;background:#e8f4ef;color:#23704d;font-size:11px;font-weight:800}.save-state.failed{background:#fff0ee;color:#a44035}.welcome,.support,article{padding:24px;border:1px solid #dbe5e8;border-radius:12px;background:#fff}.welcome{text-align:center}.welcome button,.add button,.add-entry button,footer button{padding:10px 14px;border:0;border-radius:8px;background:#176b66;color:#fff;font-weight:800}.completed{display:grid;gap:4px;margin-bottom:20px;padding:14px;border-radius:9px;background:#eaf5f1;color:#285f53}.completed span{font-size:12px}.toolbar{display:flex;justify-content:space-between;align-items:end;gap:20px}.toolbar h2,.toolbar p{margin:4px 0}.add{display:flex;gap:7px}.add input,input,select,textarea{box-sizing:border-box;width:100%;padding:10px;border:1px solid #ccd9de;border-radius:7px;font:inherit}.criteria{display:grid;gap:14px;margin-top:18px}article{display:grid;gap:13px}.criterion-head{display:flex;gap:10px}.criterion-head input{font-size:17px;font-weight:800}.remove,.entry button{border:0;background:transparent;color:#a44035}.criterion-meta{display:grid;grid-template-columns:1fr 180px;gap:12px}label{display:grid;gap:6px;color:#52636b;font-size:11px;font-weight:750}h3{margin:5px 0;color:#41535b;font-size:13px}.entry{display:grid;grid-template-columns:1fr 32px;gap:5px}.add-entry{display:grid;grid-template-columns:1fr auto;gap:7px}.support{margin-top:22px}.support-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:18px}.support-grid>div{display:grid;align-content:start;gap:8px}.support-grid .add-entry{grid-template-columns:1fr}.empty,.state{padding:24px;text-align:center;color:#6b7980}.error{padding:12px;border-radius:8px;background:#fff0ee;color:#a44035}.save-error{display:flex;justify-content:space-between;align-items:center}.save-error button{border:0;background:transparent;color:#8f352c;font-weight:800}footer{position:sticky;bottom:0;display:flex;align-items:center;justify-content:space-between;gap:18px;margin-top:24px;padding:15px 18px;border:1px solid #d4e1e3;border-radius:10px;background:#fff;box-shadow:0 10px 35px #193b4718}footer span{color:#66767e;font-size:12px}button:disabled{opacity:.45}@media(max-width:760px){.page{padding:24px 14px 70px}header,.toolbar,footer{align-items:stretch;flex-direction:column}.criterion-meta,.support-grid{grid-template-columns:1fr}.add{display:grid}.add-entry{grid-template-columns:1fr}}`],
})
export class PersonalAssessmentCollectionComponent implements OnDestroy {
  private readonly service = inject(PersonalAssessmentService);
  private readonly route = inject(ActivatedRoute);
  private readonly id = this.route.snapshot.paramMap.get('id') || '';
  private readonly saves = new Subject<SaveRequest>();
  private readonly subscription: Subscription;
  private revision = 0;
  readonly assessment = signal<PersonalAssessmentSummary | null>(null);
  readonly collection = signal<PersonalAssessmentCollection | null>(null);
  readonly document = signal<PersonalAssessmentDocument>(this.emptyDocument());
  readonly loading = signal(true); readonly loadError = signal(''); readonly saveError = signal('');
  readonly dirty = signal(false); readonly pending = signal(false); readonly working = signal(false);
  newCriterion = ''; noteDraft = ''; strengthDraft = ''; concernDraft = '';
  evidenceDrafts: Record<string, string> = {};

  constructor() {
    this.subscription = this.saves.pipe(debounceTime(450), concatMap(request => {
      this.pending.set(true); this.saveError.set('');
      return this.service.save(this.id, this.collection()!.version, request.document).pipe(
        tap(saved => { this.collection.set(saved); if (request.revision === this.revision) this.dirty.set(false); }),
        catchError(error => { this.dirty.set(true); this.saveError.set(error.status === 409 ? 'A avaliação mudou em outra sessão. Recarregue antes de continuar.' : 'Falha ao salvar. Seus registros continuam nesta tela.'); return EMPTY; }),
        finalize(() => this.pending.set(false)),
      );
    })).subscribe();
    this.load();
  }
  ngOnDestroy() { this.subscription.unsubscribe(); this.saves.complete(); }
  readOnly() { return this.collection()?.status === 'COMPLETED'; }
  saveLabel() { return this.saveError() ? 'Falha ao salvar' : this.pending() ? 'Salvando…' : this.dirty() ? 'Alterações pendentes' : 'Salvo'; }
  evidenceFor(criterionId: string) { return this.document().evidence.filter(item => item.criterionId === criterionId); }
  start() { const current=this.collection(); if(!current||this.working())return; this.working.set(true); this.service.start(this.id,current.version).subscribe({next:value=>{this.collection.set(value);this.document.set(value.document);this.working.set(false)},error:()=>{this.working.set(false);this.loadError.set('Não foi possível iniciar a coleta.')}}); }
  complete() { const current=this.collection(); if(!current||this.dirty()||this.pending()||this.saveError()||this.working())return; this.working.set(true); this.service.complete(this.id,current.version).subscribe({next:value=>{this.collection.set(value);this.document.set(value.document);this.working.set(false)},error:error=>{this.working.set(false);this.saveError.set(error.status===409?'A avaliação mudou em outra sessão. Recarregue antes de continuar.':'Não foi possível encerrar a coleta.')}}); }
  addCriterion(){const name=this.newCriterion.trim();if(!name)return;this.change({...this.document(),criteria:[...this.document().criteria,{id:crypto.randomUUID(),name,technology:null,description:null,question:null,rubric:null,score:null}]});this.newCriterion='';}
  updateCriterion(id:string,patch:Partial<PersonalAssessmentCriterion>){this.change({...this.document(),criteria:this.document().criteria.map(item=>item.id===id?{...item,...patch}:item)});}
  updateScore(id:string,value:string|number){this.updateCriterion(id,{score:value===''?null:Number(value)});}
  removeCriterion(id:string){this.change({...this.document(),criteria:this.document().criteria.filter(item=>item.id!==id),evidence:this.document().evidence.filter(item=>item.criterionId!==id),strengths:this.document().strengths.map(item=>item.criterionId===id?{...item,criterionId:null}:item),concerns:this.document().concerns.map(item=>item.criterionId===id?{...item,criterionId:null}:item)});}
  addEvidence(criterionId:string){const text=(this.evidenceDrafts[criterionId]||'').trim();if(!text)return;this.change({...this.document(),evidence:[...this.document().evidence,{id:crypto.randomUUID(),criterionId,text}]});this.evidenceDrafts[criterionId]='';}
  updateEvidence(id:string,text:string){this.change({...this.document(),evidence:this.document().evidence.map(item=>item.id===id?{...item,text}:item)});}
  removeEvidence(id:string){this.change({...this.document(),evidence:this.document().evidence.filter(item=>item.id!==id)});}
  addNote(){const text=this.noteDraft.trim();if(!text)return;this.change({...this.document(),notes:[...this.document().notes,{id:crypto.randomUUID(),text}]});this.noteDraft='';}
  updateNote(id:string,text:string){this.change({...this.document(),notes:this.document().notes.map(item=>item.id===id?{...item,text}:item)});}
  removeNote(id:string){this.change({...this.document(),notes:this.document().notes.filter(item=>item.id!==id)});}
  addFinding(kind:'strengths'|'concerns'){const draft=kind==='strengths'?this.strengthDraft:this.concernDraft;const text=draft.trim();if(!text)return;const item:PersonalAssessmentFinding={id:crypto.randomUUID(),criterionId:null,text};this.change({...this.document(),[kind]:[...this.document()[kind],item]});if(kind==='strengths')this.strengthDraft='';else this.concernDraft='';}
  updateFinding(kind:'strengths'|'concerns',id:string,text:string){this.change({...this.document(),[kind]:this.document()[kind].map(item=>item.id===id?{...item,text}:item)});}
  removeFinding(kind:'strengths'|'concerns',id:string){this.change({...this.document(),[kind]:this.document()[kind].filter(item=>item.id!==id)});}
  retrySave(){this.saves.next({revision:this.revision,document:this.document()});}
  private load(){forkJoin({assessment:this.service.get(this.id),collection:this.service.collection(this.id)}).subscribe({next:value=>{this.assessment.set(value.assessment);this.collection.set(value.collection);this.document.set(value.collection.document);this.loading.set(false)},error:()=>{this.loading.set(false);this.loadError.set('Não foi possível carregar esta avaliação.')}});}
  private change(document:PersonalAssessmentDocument){if(this.readOnly())return;this.document.set(document);this.dirty.set(true);this.saveError.set('');this.saves.next({revision:++this.revision,document});}
  private emptyDocument():PersonalAssessmentDocument{return{schemaVersion:1,criteria:[],evidence:[],notes:[],strengths:[],concerns:[]};}
}
