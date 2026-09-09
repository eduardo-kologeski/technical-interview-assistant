import { Component, OnDestroy, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EMPTY, Subject, Subscription, catchError, concatMap, debounceTime, finalize, forkJoin, tap } from 'rxjs';
import {
  PersonalAssessmentCollection, PersonalAssessmentCriterion, PersonalAssessmentDocument,
  PersonalAssessmentFinding, PersonalAssessmentService, PersonalAssessmentSummary,
} from './personal-assessment.service';
import { QUICK_EVALUATION_OPTIONS, QuickOption } from './skill-evaluation-card.component';

type SaveRequest = { revision: number; document: PersonalAssessmentDocument };

@Component({
  selector: 'app-personal-assessment-collection',
  imports: [FormsModule, RouterLink],
  template: `<section class="page">
    <a routerLink="/dashboard" class="back">← Minhas avaliações</a>
    @if (loading()) { <p class="state">Carregando avaliação…</p> }
    @else if (loadError()) { <p class="error" role="alert">{{ loadError() }}</p> }
    @else if (assessment(); as current) {
      <header>
        <div><p class="eyebrow">AVALIAÇÃO TÉCNICA</p><h1>{{ current.title }}</h1><span>{{ current.personName }} @if(current.context){ · {{ current.context }} }</span></div>
        @if (!readOnly()) { <span class="save-state" [class.failed]="!!saveError()">{{ saveLabel() }}</span> }
      </header>
      @if (saveError()) { <p class="error save-error" role="alert"><span>{{ saveError() }}</span><button type="button" (click)="retrySave()">Tentar novamente</button></p> }

      @if (collection()?.status === 'CREATED') {
        <section class="phase-card">
          <p class="step">ETAPA 1 DE 2</p><h2>Defina os requisitos antes da entrevista</h2>
          <p>Prepare o que será avaliado. Perguntas e rubricas ficam disponíveis como apoio durante a conversa; depois de iniciar, os requisitos são bloqueados.</p>
          <div class="add-requirement"><input [(ngModel)]="newCriterion" (keydown.enter)="addCriterion(); $event.preventDefault()" placeholder="Ex.: APIs REST com Spring Boot" aria-label="Novo requisito"><button type="button" (click)="addCriterion()" [disabled]="!newCriterion.trim()">Adicionar requisito</button></div>
        </section>
        <div class="requirements">
          @for (criterion of document().criteria; track criterion.id; let index = $index) {
            <article class="requirement-card">
              <div class="requirement-heading"><strong>Requisito {{ index + 1 }}</strong><button type="button" class="remove" (click)="removeCriterion(criterion.id)">Remover</button></div>
              <div class="two-columns">
                <label>Nome<input [ngModel]="criterion.name" (ngModelChange)="updateCriterion(criterion.id,{name:$event})"></label>
                <label>Tecnologia ou tema<input [ngModel]="criterion.technology" (ngModelChange)="updateCriterion(criterion.id,{technology:$event||null})" placeholder="Ex.: Java"></label>
              </div>
              <label>O que deve ser observado<textarea [ngModel]="criterion.description" (ngModelChange)="updateCriterion(criterion.id,{description:$event||null})" rows="2" placeholder="Descreva a experiência ou competência esperada"></textarea></label>
              <label>Pergunta de apoio<textarea [ngModel]="criterion.question" (ngModelChange)="updateCriterion(criterion.id,{question:$event||null})" rows="2" placeholder="Pergunta sugerida para explorar este requisito"></textarea></label>
              <label>Rubrica de apoio<textarea [ngModel]="criterion.rubric" (ngModelChange)="updateCriterion(criterion.id,{rubric:$event||null})" rows="2" placeholder="Sinais esperados em uma resposta"></textarea></label>
            </article>
          } @empty { <p class="empty">Adicione ao menos um requisito para liberar o início da avaliação.</p> }
        </div>
        <footer><span>@if(dirty()||pending()){Aguarde o salvamento dos requisitos.}@else{Os requisitos salvos serão usados durante toda a avaliação.}</span><button type="button" (click)="start()" [disabled]="!document().criteria.length||dirty()||pending()||!!saveError()||working()">Iniciar avaliação</button></footer>
      }

      @if (collection()?.status === 'IN_PROGRESS') {
        <section class="phase-card compact"><p class="step">ETAPA 2 DE 2</p><h2>Registre o que foi observado</h2><p>Use as frases rápidas durante a conversa. Verde também registra um ponto forte; vermelho registra um ponto de atenção.</p></section>
        <div class="requirements">
          @for (criterion of document().criteria; track criterion.id) {
            <article class="evaluation-card">
              <div class="criterion-title"><div><h2>{{ criterion.name }}</h2>@if(criterion.technology){<span>{{ criterion.technology }}</span>}</div><label class="score">Nota<select [ngModel]="criterion.score" (ngModelChange)="updateScore(criterion.id,$event)"><option [ngValue]="null">Não avaliado</option>@for(value of scores;track value){<option [ngValue]="value">{{ value }}/5</option>}</select></label></div>
              @if(criterion.description){<p class="expectation"><strong>Observar</strong>{{ criterion.description }}</p>}
              @if(criterion.question){<p class="question"><strong>Pergunta sugerida</strong>{{ criterion.question }}</p>}
              @if(criterion.rubric){<details><summary>Ver rubrica</summary><p>{{ criterion.rubric }}</p></details>}
              <div class="quick-tags" aria-label="Frases rápidas para {{ criterion.name }}">
                @for(option of quickOptions;track option.label){<button type="button" [class]="option.tone" (click)="appendQuickObservation(criterion.id,option)">{{ option.label }}</button>}
              </div>
              <h3>Evidências e experiência observada</h3>
              @for (item of evidenceFor(criterion.id); track item.id) {
                <div class="evidence-entry"><textarea aria-label="Evidência de {{ criterion.name }}" [ngModel]="item.text" (ngModelChange)="updateEvidence(item.id,$event)" rows="2"></textarea><div><button type="button" class="mark positive" (click)="markFinding('strengths',criterion.id,item.text)">+ Forte</button><button type="button" class="mark negative" (click)="markFinding('concerns',criterion.id,item.text)">+ Atenção</button><button type="button" class="remove" (click)="removeEvidence(item.id)">Remover</button></div></div>
              }
              <div class="add-entry"><textarea [(ngModel)]="evidenceDrafts[criterion.id]" rows="2" placeholder="Escreva uma evidência específica"></textarea><button type="button" (click)="addEvidence(criterion.id)" [disabled]="!evidenceDrafts[criterion.id]?.trim()">Registrar evidência</button></div>
            </article>
          }
        </div>
        <section class="support"><h2>Registros gerais</h2><div class="support-grid">
          <div><h3>Notas</h3>@for(item of document().notes;track item.id){<div class="entry"><textarea [ngModel]="item.text" (ngModelChange)="updateNote(item.id,$event)"></textarea><button type="button" class="remove" (click)="removeNote(item.id)">×</button></div>}<div class="add-entry vertical"><textarea [(ngModel)]="noteDraft" placeholder="Observação geral"></textarea><button type="button" (click)="addNote()" [disabled]="!noteDraft.trim()">Adicionar nota</button></div></div>
          <div><h3>Pontos fortes</h3>@for(item of document().strengths;track item.id){<div class="entry"><textarea [ngModel]="item.text" (ngModelChange)="updateFinding('strengths',item.id,$event)"></textarea><button type="button" class="remove" (click)="removeFinding('strengths',item.id)">×</button></div>}<div class="add-entry vertical"><textarea [(ngModel)]="strengthDraft" placeholder="Ponto forte observado"></textarea><button type="button" (click)="addFinding('strengths')" [disabled]="!strengthDraft.trim()">Adicionar ponto forte</button></div></div>
          <div><h3>Pontos de atenção</h3>@for(item of document().concerns;track item.id){<div class="entry"><textarea [ngModel]="item.text" (ngModelChange)="updateFinding('concerns',item.id,$event)"></textarea><button type="button" class="remove" (click)="removeFinding('concerns',item.id)">×</button></div>}<div class="add-entry vertical"><textarea [(ngModel)]="concernDraft" placeholder="Ponto de atenção observado"></textarea><button type="button" (click)="addFinding('concerns')" [disabled]="!concernDraft.trim()">Adicionar ponto de atenção</button></div></div>
        </div></section>
        <footer><span>@if(dirty()||pending()){Aguarde a confirmação do salvamento antes de encerrar.}@else{Todos os registros foram confirmados no servidor.}</span><button type="button" (click)="complete()" [disabled]="dirty()||pending()||!!saveError()||working()">Encerrar coleta</button></footer>
      }

      @if (collection()?.status === 'COMPLETED') {
        <section class="completed"><p class="step">COLETA ENCERRADA</p><h2>Registros preservados</h2><p>{{ collection()?.generationEligible ? 'Os requisitos e as evidências estão prontos para gerar uma minuta.' : 'A avaliação foi encerrada sem evidência suficiente para gerar uma minuta.' }}</p><div class="summary"><span>{{ document().criteria.length }} requisitos</span><span>{{ document().evidence.length }} evidências</span><span>{{ document().strengths.length }} pontos fortes</span><span>{{ document().concerns.length }} pontos de atenção</span></div>@if(collection()?.generationEligible){<a class="report-link" [routerLink]="['/assessments',id,'report']">Gerar ou revisar minuta</a>}</section>
      }
    }
  </section>`,
  styles: [`:host{display:block}.page{max-width:1050px;margin:auto;padding:34px 24px 90px}.back{color:#176b66;text-decoration:none;font-size:12px;font-weight:800}header{display:flex;justify-content:space-between;gap:20px;margin:28px 0}header h1{margin:5px 0;font-size:33px}header span,.phase-card p{color:#66767e}.eyebrow,.step{margin:0;color:#278d83!important;font-size:10px;font-weight:900;letter-spacing:.14em}.save-state{align-self:start;padding:7px 11px;border-radius:14px;background:#e8f4ef;color:#23704d;font-size:11px;font-weight:800}.save-state.failed{background:#fff0ee;color:#a44035}.phase-card,.support,.requirement-card,.evaluation-card,.completed{padding:24px;border:1px solid #dbe5e8;border-radius:12px;background:#fff}.phase-card h2,.completed h2{margin:6px 0}.phase-card.compact{margin-bottom:18px}.add-requirement,.add-entry{display:grid;grid-template-columns:1fr auto;gap:8px;margin-top:18px}.add-requirement button,.add-entry button,footer button{padding:10px 14px;border:0;border-radius:8px;background:#176b66;color:#fff;font-weight:800}.requirements{display:grid;gap:14px;margin-top:16px}.requirement-card,.evaluation-card{display:grid;gap:13px}.requirement-heading,.criterion-title{display:flex;justify-content:space-between;align-items:start;gap:16px}.two-columns{display:grid;grid-template-columns:1fr 1fr;gap:12px}label{display:grid;gap:6px;color:#52636b;font-size:11px;font-weight:750}input,select,textarea{box-sizing:border-box;width:100%;padding:10px;border:1px solid #ccd9de;border-radius:7px;font:inherit}.remove{border:0;background:transparent;color:#a44035}.criterion-title h2{margin:0}.criterion-title span{color:#66767e;font-size:12px}.score{width:145px}.expectation,.question{display:grid;gap:4px;margin:0;padding:10px 12px;border-left:3px solid #2c718f;background:#f4f9fb;color:#344b57;font-size:13px}.expectation strong,.question strong{font-size:10px;letter-spacing:.08em;text-transform:uppercase}.quick-tags{display:flex;flex-wrap:wrap;gap:7px;padding:12px 0;border-top:1px solid #edf1f3;border-bottom:1px solid #edf1f3}.quick-tags button,.mark{padding:6px 9px;border:1px solid transparent;border-radius:5px;font-size:11px}.positive{background:#e7f5ec!important;color:#25724c!important}.neutral{background:#fff5d9!important;color:#8a6514!important}.negative{background:#fdeaea!important;color:#a13d3d!important}.evaluation-card h3{margin:4px 0;color:#41535b;font-size:13px}.evidence-entry{display:grid;grid-template-columns:1fr 100px;gap:8px}.evidence-entry>div{display:grid;align-content:start;gap:5px}.support{margin-top:22px}.support-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:18px}.support-grid>div{display:grid;align-content:start;gap:8px}.entry{display:grid;grid-template-columns:1fr 30px;gap:5px}.vertical{grid-template-columns:1fr}.completed .summary{display:flex;flex-wrap:wrap;gap:8px;margin-top:18px}.completed .summary span{padding:7px 10px;border-radius:14px;background:#eaf5f1;color:#285f53;font-size:12px}.empty,.state{padding:24px;text-align:center;color:#6b7980}.error{padding:12px;border-radius:8px;background:#fff0ee;color:#a44035}.save-error{display:flex;justify-content:space-between;align-items:center}.save-error button{border:0;background:transparent;color:#8f352c;font-weight:800}footer{position:sticky;bottom:0;display:flex;align-items:center;justify-content:space-between;gap:18px;margin-top:24px;padding:15px 18px;border:1px solid #d4e1e3;border-radius:10px;background:#fff;box-shadow:0 10px 35px #193b4718}footer span{color:#66767e;font-size:12px}button:disabled{opacity:.45}@media(max-width:760px){.page{padding:24px 14px 80px}header,footer,.criterion-title{align-items:stretch;flex-direction:column}.two-columns,.support-grid{grid-template-columns:1fr}.score{width:100%}.add-requirement,.add-entry,.evidence-entry{grid-template-columns:1fr}.quick-tags{flex-wrap:nowrap;overflow-x:auto}.quick-tags button{flex:0 0 auto}}`],
})
export class PersonalAssessmentCollectionComponent implements OnDestroy {
  private readonly service = inject(PersonalAssessmentService);
  private readonly route = inject(ActivatedRoute);
  readonly id = this.route.snapshot.paramMap.get('id') || '';
  private readonly saves = new Subject<SaveRequest>();
  private readonly subscription: Subscription;
  private revision = 0;
  readonly assessment = signal<PersonalAssessmentSummary | null>(null);
  readonly collection = signal<PersonalAssessmentCollection | null>(null);
  readonly document = signal<PersonalAssessmentDocument>(this.emptyDocument());
  readonly loading = signal(true); readonly loadError = signal(''); readonly saveError = signal('');
  readonly dirty = signal(false); readonly pending = signal(false); readonly working = signal(false);
  readonly scores = [0, 1, 2, 3, 4, 5]; readonly quickOptions = QUICK_EVALUATION_OPTIONS;
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
  start() { const current=this.collection(); if(!current||!this.document().criteria.length||this.dirty()||this.pending()||this.working())return; this.working.set(true); this.service.start(this.id,current.version).subscribe({next:value=>{this.collection.set(value);this.document.set(value.document);this.working.set(false)},error:error=>{this.working.set(false);this.saveError.set(error.status===409?error.error?.detail||'Não foi possível iniciar a avaliação.':'Não foi possível iniciar a avaliação.')}}); }
  complete() { const current=this.collection(); if(!current||this.dirty()||this.pending()||this.saveError()||this.working())return; this.working.set(true); this.service.complete(this.id,current.version).subscribe({next:value=>{this.collection.set(value);this.document.set(value.document);this.working.set(false)},error:error=>{this.working.set(false);this.saveError.set(error.status===409?'A avaliação mudou em outra sessão. Recarregue antes de continuar.':'Não foi possível encerrar a coleta.')}}); }
  addCriterion(){const name=this.newCriterion.trim();if(!name)return;this.change({...this.document(),criteria:[...this.document().criteria,{id:crypto.randomUUID(),name,technology:null,description:null,question:null,rubric:null,score:null}]});this.newCriterion='';}
  updateCriterion(id:string,patch:Partial<PersonalAssessmentCriterion>){if(this.collection()?.status!=='CREATED')return;this.change({...this.document(),criteria:this.document().criteria.map(item=>item.id===id?{...item,...patch}:item)});}
  updateScore(id:string,value:number|null){this.change({...this.document(),criteria:this.document().criteria.map(item=>item.id===id?{...item,score:value}:item)});}
  removeCriterion(id:string){if(this.collection()?.status!=='CREATED')return;this.change({...this.document(),criteria:this.document().criteria.filter(item=>item.id!==id)});}
  addEvidence(criterionId:string){const text=(this.evidenceDrafts[criterionId]||'').trim();if(!text)return;this.change({...this.document(),evidence:[...this.document().evidence,{id:crypto.randomUUID(),criterionId,text}]});this.evidenceDrafts[criterionId]='';}
  appendQuickObservation(criterionId:string,option:QuickOption){const current=this.document();const matches=current.evidence.filter(item=>item.criterionId===criterionId);let evidence=[...current.evidence];if(matches.length){const last=matches[matches.length-1];evidence=evidence.map(item=>item.id===last.id?{...item,text:`${item.text.trim()}${item.text.trim()?' ':''}${option.text}`}:item);}else{evidence.push({id:crypto.randomUUID(),criterionId,text:option.text});}let strengths=current.strengths;let concerns=current.concerns;if(option.tone==='positive'&&!strengths.some(item=>item.criterionId===criterionId&&item.text===option.text))strengths=[...strengths,{id:crypto.randomUUID(),criterionId,text:option.text}];if(option.tone==='negative'&&!concerns.some(item=>item.criterionId===criterionId&&item.text===option.text))concerns=[...concerns,{id:crypto.randomUUID(),criterionId,text:option.text}];this.change({...current,evidence,strengths,concerns});}
  updateEvidence(id:string,text:string){this.change({...this.document(),evidence:this.document().evidence.map(item=>item.id===id?{...item,text}:item)});}
  removeEvidence(id:string){this.change({...this.document(),evidence:this.document().evidence.filter(item=>item.id!==id)});}
  markFinding(kind:'strengths'|'concerns',criterionId:string,text:string){const clean=text.trim();if(!clean||this.document()[kind].some(item=>item.criterionId===criterionId&&item.text===clean))return;this.change({...this.document(),[kind]:[...this.document()[kind],{id:crypto.randomUUID(),criterionId,text:clean}]});}
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
