import { Component, OnDestroy, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EMPTY, Subject, Subscription, catchError, concatMap, debounceTime, finalize, forkJoin, tap } from 'rxjs';
import { PersonalAssessmentCollection, PersonalAssessmentReport, PersonalAssessmentService, PersonalAssessmentSummary, ReportDraft } from './personal-assessment.service';

type SectionKey = keyof ReportDraft;
type SaveRequest = { revision: number; draft: ReportDraft; conclusion: string; confirmed: boolean };

@Component({
  selector: 'app-personal-assessment-report',
  imports: [FormsModule, RouterLink],
  template: `<section class="page">
    <a [routerLink]="['/assessments',id]" class="back">← Voltar à avaliação</a>
    @if(loading()){<p class="state">Carregando parecer…</p>}
    @else if(loadError()){<p class="error" role="alert">{{loadError()}}</p>}
    @else if(assessment();as current){
      <header><div><p class="eyebrow">{{report()?.status==='APPROVED'?'PARECER APROVADO':'MINUTA DE PARECER'}}</p><h1>{{current.title}}</h1><span>{{current.personName}}</span></div>
      @if(report()&&report()?.status==='DRAFT'){<span class="save-state" [class.failed]="!!saveError()">{{saveLabel()}}</span>}</header>
      @if(!report()){
        <section class="empty-card"><h2>Gerar minuta com o Ollama local</h2><p>A minuta usa somente os registros desta avaliação. Ela deverá ser revisada e não contém a sua conclusão.</p>
        @if(actionError()){<p class="error" role="alert">{{actionError()}}</p>}
        <button type="button" (click)="generate()" [disabled]="working()||!collection()?.generationEligible">{{working()?'Gerando…':'Gerar minuta'}}</button></section>
      } @else if(report();as currentReport){
        @if(actionError()){<p class="error" role="alert">{{actionError()}}</p>}
        @if(saveError()){<p class="error save-error" role="alert"><span>{{saveError()}}</span><button type="button" (click)="retrySave()">Tentar novamente</button></p>}
        <p class="notice">{{currentReport.status==='APPROVED'?'Este é o parecer final aprovado e não pode mais ser alterado.':'Revise todas as seções. O conteúdo abaixo continua sendo uma minuta até sua aprovação.'}}</p>
        <div class="sections">
          @for(section of sectionDefinitions;track section.key){<article><label>{{section.label}}<textarea [ngModel]="draft()?.[section.key]?.text" (ngModelChange)="updateSection(section.key,$event)" [readonly]="readOnly()" rows="6"></textarea></label>
          <small>@if(draft()?.[section.key]?.insufficientData){Dados insuficientes declarados · }{{draft()?.[section.key]?.evidenceReferences?.length||0}} referência(s) de evidência</small></article>}
        </div>
        <section class="conclusion"><label>Conclusão do entrevistador<textarea [ngModel]="humanConclusion()" (ngModelChange)="updateConclusion($event)" [readonly]="readOnly()" rows="7" placeholder="Escreva sua conclusão com base na avaliação e na revisão da minuta."></textarea></label><p>Esta conclusão é exclusivamente humana e nunca é enviada ao modelo.</p></section>
        @if(!readOnly()){
          <label class="confirmation"><input type="checkbox" [ngModel]="reviewConfirmed()" (ngModelChange)="updateConfirmation($event)"> Revisei a minuta e confirmo que o parecer final corresponde aos registros da avaliação.</label>
          <footer><span>{{saveLabel()}}</span><button type="button" (click)="approve()" [disabled]="!canApprove()">{{working()?'Aprovando…':'Aprovar parecer'}}</button></footer>
        } @else {<div class="export"><button type="button" (click)="exportPdf()">Baixar parecer em PDF</button><span>O download não altera a aprovação.</span></div>}
      }
    }
  </section>`,
  styles: [`:host{display:block}.page{max-width:960px;margin:auto;padding:34px 24px 90px}.back{color:#176b66;text-decoration:none;font-size:12px;font-weight:800}header{display:flex;justify-content:space-between;gap:20px;margin:28px 0}header h1{margin:5px 0;font-size:33px}header span,.notice,.conclusion p{color:#66767e}.eyebrow{margin:0;color:#278d83;font-size:10px;font-weight:900;letter-spacing:.14em}.save-state{align-self:start;padding:7px 11px;border-radius:14px;background:#e8f4ef;color:#23704d;font-size:11px;font-weight:800}.save-state.failed{background:#fff0ee;color:#a44035}.empty-card,.sections article,.conclusion{padding:22px;border:1px solid #dbe5e8;border-radius:12px;background:#fff}.empty-card button,footer button,.export button{padding:11px 15px;border:0;border-radius:8px;background:#176b66;color:#fff;font-weight:800}.sections{display:grid;gap:14px}.sections article{display:grid;gap:8px}label{display:grid;gap:7px;color:#41535b;font-size:12px;font-weight:800}textarea{box-sizing:border-box;width:100%;padding:12px;border:1px solid #ccd9de;border-radius:8px;font:inherit;line-height:1.45}textarea[readonly]{background:#f5f7f8;color:#46575f}.sections small{color:#718089}.conclusion{margin-top:18px}.conclusion p{margin-bottom:0;font-size:12px}.confirmation{grid-template-columns:auto 1fr;align-items:start;margin:20px 0;padding:16px;border-radius:9px;background:#eef7f4}.confirmation input{margin-top:2px}.notice{padding:13px;border-left:3px solid #2c718f;background:#f3f8fa}.error{padding:12px;border-radius:8px;background:#fff0ee;color:#a44035}.save-error{display:flex;justify-content:space-between}.save-error button{border:0;background:transparent;color:#8f352c;font-weight:800}footer{position:sticky;bottom:0;display:flex;align-items:center;justify-content:space-between;gap:18px;padding:15px 18px;border:1px solid #d4e1e3;border-radius:10px;background:#fff;box-shadow:0 10px 35px #193b4718}footer span{color:#66767e;font-size:12px}.export{display:flex;align-items:center;gap:14px;margin-top:20px}.export span{color:#66767e;font-size:12px}button:disabled{opacity:.45}.state{text-align:center;padding:35px;color:#66767e}@media(max-width:650px){.page{padding:24px 14px 80px}header,footer,.export{align-items:stretch;flex-direction:column}}`]
})
export class PersonalAssessmentReportComponent implements OnDestroy {
  private readonly service=inject(PersonalAssessmentService); private readonly route=inject(ActivatedRoute);
  readonly id=this.route.snapshot.paramMap.get('id')||''; private readonly saves=new Subject<SaveRequest>(); private readonly subscription:Subscription;
  private revision=0; private approvalKey=crypto.randomUUID();
  readonly assessment=signal<PersonalAssessmentSummary|null>(null); readonly collection=signal<PersonalAssessmentCollection|null>(null); readonly report=signal<PersonalAssessmentReport|null>(null); readonly draft=signal<ReportDraft|null>(null);
  readonly humanConclusion=signal(''); readonly reviewConfirmed=signal(false); readonly loading=signal(true); readonly loadError=signal(''); readonly actionError=signal(''); readonly saveError=signal(''); readonly dirty=signal(false); readonly pending=signal(false); readonly working=signal(false);
  readonly sectionDefinitions:{key:SectionKey;label:string}[]=[{key:'context',label:'Contexto da avaliação'},{key:'observedKnowledge',label:'Conhecimentos observados'},{key:'strengths',label:'Pontos positivos'},{key:'concerns',label:'Pontos de atenção'},{key:'synthesis',label:'Síntese da minuta'}];
  constructor(){this.subscription=this.saves.pipe(debounceTime(500),concatMap(request=>{this.pending.set(true);this.saveError.set('');return this.service.saveReport(this.id,this.report()!.version,request.draft,request.conclusion,request.confirmed).pipe(tap(saved=>{if(request.revision===this.revision){this.applyReport(saved);this.dirty.set(false)}else{this.report.set({...saved,draft:this.draft()!,humanConclusion:this.humanConclusion(),reviewConfirmed:this.reviewConfirmed()})}}),catchError(error=>{this.dirty.set(true);this.saveError.set(error.status===409?'A minuta mudou em outra sessão. Recarregue antes de continuar.':'Falha ao salvar. Suas alterações continuam nesta tela.');return EMPTY}),finalize(()=>this.pending.set(false)))})).subscribe();this.load();}
  ngOnDestroy(){this.subscription.unsubscribe();this.saves.complete()}
  readOnly(){return this.report()?.status==='APPROVED'}
  saveLabel(){return this.saveError()?'Falha ao salvar':this.pending()?'Salvando…':this.dirty()?'Alterações pendentes':'Salvo'}
  updateSection(key:SectionKey,text:string){const current=this.draft();if(!current||this.readOnly())return;this.change({...current,[key]:{...current[key],text}})}
  updateConclusion(value:string){if(this.readOnly())return;this.humanConclusion.set(value);this.queueSave()}
  updateConfirmation(value:boolean){if(this.readOnly())return;this.reviewConfirmed.set(value);this.queueSave()}
  retrySave(){const current=this.draft();if(current)this.saves.next({revision:this.revision,draft:current,conclusion:this.humanConclusion(),confirmed:this.reviewConfirmed()})}
  generate(){const assessment=this.assessment(),collection=this.collection();if(!assessment||!collection?.generationEligible||this.working())return;this.working.set(true);this.actionError.set('');this.service.generateReport(this.id,assessment.version).subscribe({next:value=>{this.applyReport(value);this.working.set(false)},error:error=>{this.working.set(false);this.actionError.set(error.error?.detail||'Não foi possível gerar a minuta com o Ollama local. Seus registros foram preservados.')}})}
  canApprove(){return !!this.report()&&!this.readOnly()&&!this.dirty()&&!this.pending()&&!this.saveError()&&!this.working()&&this.reviewConfirmed()&&!!this.humanConclusion().trim()}
  approve(){const current=this.report();if(!current||!this.canApprove())return;this.working.set(true);this.actionError.set('');this.service.approveReport(this.id,current.version,this.approvalKey).subscribe({next:value=>{this.applyReport(value);this.working.set(false)},error:error=>{this.working.set(false);this.actionError.set(error.error?.detail||'Não foi possível aprovar o parecer.')}})}
  async exportPdf(download=true){const current=this.report(),assessment=this.assessment(),draft=this.draft();if(current?.status!=='APPROVED'||!assessment||!draft)return null;const {jsPDF}=await import('jspdf');const pdf=new jsPDF({unit:'mm',format:'a4'});const margin=18,maxWidth=174;let y=20;const page=()=>{pdf.addPage();y=20};const ensure=(height:number)=>{if(y+height>280)page()};const write=(text:string)=>{const lines=pdf.splitTextToSize(text,maxWidth) as string[];for(const line of lines){if(y+5>280)page();pdf.text(line,margin,y);y+=5}y+=5};pdf.setFont('helvetica','bold');pdf.setFontSize(16);pdf.text('Parecer técnico de avaliação',margin,y);y+=9;pdf.setFontSize(10);pdf.setFont('helvetica','normal');write(`Avaliação: ${assessment.title}\nPessoa avaliada: ${assessment.personName}\nAprovado em: ${new Date(current.approvedAt||current.updatedAt).toLocaleString('pt-BR')}`);for(const section of this.sectionDefinitions){ensure(12);pdf.setFont('helvetica','bold');pdf.setFontSize(12);pdf.text(section.label,margin,y);y+=6;pdf.setFont('helvetica','normal');pdf.setFontSize(10);write(draft[section.key].text)}ensure(12);pdf.setFont('helvetica','bold');pdf.setFontSize(12);pdf.text('Conclusão do entrevistador',margin,y);y+=6;pdf.setFont('helvetica','normal');pdf.setFontSize(10);write(this.humanConclusion());const pages=pdf.getNumberOfPages();for(let index=1;index<=pages;index++){pdf.setPage(index);pdf.setFontSize(8);pdf.setTextColor(110);pdf.text(`Página ${index} de ${pages}`,105,290,{align:'center'})}if(download){const name=assessment.personName.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'')||'avaliacao';pdf.save(`parecer-tecnico-${name}.pdf`)}return pdf}
  private load(){forkJoin({assessment:this.service.get(this.id),collection:this.service.collection(this.id)}).subscribe({next:value=>{this.assessment.set(value.assessment);this.collection.set(value.collection);this.service.report(this.id).subscribe({next:report=>{this.applyReport(report);this.loading.set(false)},error:error=>{this.loading.set(false);if(error.status!==404)this.loadError.set('Não foi possível carregar a minuta.')}})},error:()=>{this.loading.set(false);this.loadError.set('Não foi possível carregar esta avaliação.')}})}
  private change(draft:ReportDraft){this.draft.set(draft);this.queueSave()}
  private queueSave(){const draft=this.draft();if(!draft)return;this.dirty.set(true);this.saveError.set('');this.saves.next({revision:++this.revision,draft,conclusion:this.humanConclusion(),confirmed:this.reviewConfirmed()})}
  private applyReport(value:PersonalAssessmentReport){this.report.set(value);this.draft.set(structuredClone(value.draft));this.humanConclusion.set(value.humanConclusion||'');this.reviewConfirmed.set(value.reviewConfirmed)}
}
