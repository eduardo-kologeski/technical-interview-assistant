import { Component, Input, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { InterviewKitItem, InterviewKitVersion, JobPositionService } from './job-position.service';

@Component({selector:'app-interview-kit-editor',imports:[FormsModule],template:`
<button type="button" class="toggle" (click)="toggle()">{{open()?'Fechar kit':'Configurar interview kit'}}</button>
@if(open()){
  <section class="kit" aria-label="Editor do interview kit">
    @if(loading()){<p>Carregando kit...</p>} @else {
      <header><div><strong>Interview kit</strong><span>Versões publicadas são imutáveis; entrevistas guardam um snapshot.</span></div><span>{{publishedLabel()}}</span></header>
      <label class="kit-name">Nome do kit<input aria-label="Nome do kit" [(ngModel)]="name" maxlength="160"></label>
      @for(item of items;track $index;let index=$index){
        <article><label>Critério<input [(ngModel)]="item.criterion"></label><label>Pergunta<textarea [(ngModel)]="item.question"></textarea></label><label>Peso<input type="number" min="1" max="10" [(ngModel)]="item.weight"></label><label>Rubrica<textarea [(ngModel)]="item.rubric"></textarea></label><button type="button" class="remove" (click)="remove(index)">Remover critério</button></article>
      }
      <div class="actions"><button type="button" class="secondary" (click)="add()">+ Critério</button><button type="button" [disabled]="saving()" (click)="saveDraft()">Salvar rascunho</button><button type="button" [disabled]="!draftId()||saving()" (click)="publish()">Publicar versão</button></div>
      @if(message()){<p role="status">{{message()}}</p>}
    }
  </section>
}`,
styles:[`:host{display:grid;gap:10px}.toggle,.actions button{width:max-content;padding:9px 12px;border:0;border-radius:7px;background:#165d7a;color:#fff;font-weight:800}.kit{display:grid;gap:12px;padding:16px;border:1px solid #cfdde4;border-radius:10px;background:#f8fbfc}.kit header{display:flex;justify-content:space-between;gap:12px}.kit header div{display:grid;gap:4px}.kit header span{color:#60717b;font-size:12px}.kit article{display:grid;grid-template-columns:1fr 2fr 90px 2fr auto;gap:9px;align-items:end;padding:12px;background:#fff;border:1px solid #dde6eb;border-radius:8px}.kit label{display:grid;gap:5px;font-size:11px;font-weight:800;color:#52616b}.kit-name{max-width:520px}.kit input,.kit textarea{box-sizing:border-box;width:100%;padding:9px;border:1px solid #cad5dc;border-radius:6px}.kit textarea{min-height:62px}.actions{display:flex;flex-wrap:wrap;gap:8px}.actions .secondary,.remove{background:#fff;color:#52616b;border:1px solid #cfd9df}.remove{padding:9px;border-radius:7px}@media(max-width:800px){.kit article{grid-template-columns:1fr}.kit header{flex-direction:column}}`]
})
export class InterviewKitEditorComponent{
  @Input({required:true}) vacancyId=''; private service=inject(JobPositionService);open=signal(false);loading=signal(false);saving=signal(false);message=signal('');draftId=signal('');name='';items:InterviewKitItem[]=[];private versions:InterviewKitVersion[]=[];
  toggle(){this.open.update(value=>!value);if(this.open()&&!this.name)this.load()}
  load(){this.loading.set(true);this.service.getKit(this.vacancyId).subscribe({next:kit=>{this.name=kit.name;this.versions=kit.versions;const editable=kit.versions.find(v=>v.status==='DRAFT')||kit.versions.find(v=>v.status==='PUBLISHED');this.items=(editable?.items||[]).map(item=>({...item}));this.draftId.set(kit.versions.find(v=>v.status==='DRAFT')?.id||'');this.loading.set(false)},error:()=>{this.loading.set(false);this.message.set('Não foi possível carregar o kit.')}})}
  add(){this.items=[...this.items,{criterion:'',question:'',weight:3,rubric:'0: sem evidência; 3: atende; 5: domínio avançado.'}]}
  remove(index:number){this.items=this.items.filter((_,current)=>current!==index)}
  saveDraft(){this.saving.set(true);this.message.set('');this.service.saveKitDraft(this.vacancyId,this.name,this.items).subscribe({next:version=>{this.draftId.set(version.id);this.saving.set(false);this.message.set('Rascunho salvo.')},error:e=>{this.saving.set(false);this.message.set(e.error?.detail||'Revise os campos do kit.')}})}
  publish(){this.saving.set(true);this.service.publishKit(this.vacancyId,this.draftId()).subscribe({next:()=>{this.saving.set(false);this.draftId.set('');this.message.set('Versão publicada. Novas entrevistas usarão este snapshot.');this.load()},error:e=>{this.saving.set(false);this.message.set(e.error?.detail||'Não foi possível publicar.')}})}
  publishedLabel(){const version=this.versions.find(v=>v.status==='PUBLISHED');return version?`Publicada v${version.version}`:'Sem versão publicada'}
}
