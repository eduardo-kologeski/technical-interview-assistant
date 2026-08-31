import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DeploymentConfigService } from './deployment-config.service';

@Component({
  selector: 'app-landing-page',
  imports: [RouterLink],
  template: `
    <main>
      <nav>
        <a class="brand" routerLink="/"><b>T</b><span>Technical Interview Assistant</span></a>
        <div><a class="login" routerLink="/auth/login">Entrar</a>@if (deployment.publicSignupEnabled()) { <a class="primary compact" routerLink="/auth/register" [queryParams]="{ company: true }">Cadastrar empresa</a> }</div>
      </nav>

      <section class="hero">
        <div>
          <p class="eyebrow">GESTÃO DE ENTREVISTAS PARA EMPRESAS</p>
          <h1>Decisões técnicas mais consistentes, do briefing ao parecer.</h1>
          <p class="lead">Centralize vagas, conduza avaliações padronizadas e transforme evidências da entrevista em pareceres claros e rastreáveis.</p>
          <div class="actions">@if (deployment.publicSignupEnabled()) { <a class="primary" routerLink="/auth/register" [queryParams]="{ company: true }">Cadastrar minha empresa</a> }<a class="secondary" routerLink="/auth/login">Entrar</a></div>
          <small>Entrevistadores acessam o ambiente da empresa somente após convite do gestor.</small>
        </div>
        <aside aria-label="Fluxo da plataforma">
          <span>AMBIENTE DA EMPRESA</span>
          <strong>Uma operação organizada para cada etapa</strong>
          <ol><li><b>01</b> Vagas e requisitos</li><li><b>02</b> Entrevistas estruturadas</li><li><b>03</b> Pareceres e PDFs</li></ol>
        </aside>
      </section>

      <section class="benefits" aria-label="Benefícios">
        <article><span>PADRONIZAÇÃO</span><h2>Avaliações comparáveis</h2><p>Critérios técnicos, comentários e evidências organizados no mesmo fluxo.</p></article>
        <article><span>COLABORAÇÃO</span><h2>Equipe sob controle</h2><p>Gestores convidam entrevistadores e controlam quem participa do ambiente corporativo.</p></article>
        <article><span>RASTREABILIDADE</span><h2>Histórico confiável</h2><p>Vagas, entrevistas e pareceres permanecem vinculados à empresa responsável.</p></article>
      </section>
    </main>
  `,
  styles: [`
    :host{display:block;min-height:100vh;background:#f6f8f9;color:#18252d;font-family:Inter,system-ui,sans-serif}main{max-width:1180px;margin:auto;padding:0 28px 70px}nav{height:76px;display:flex;align-items:center;justify-content:space-between;gap:24px}.brand{display:flex;align-items:center;gap:11px;color:#1e3039;text-decoration:none;font-weight:850}.brand b{display:grid;place-items:center;width:36px;height:36px;border-radius:9px;background:#165d7a;color:#fff}.brand span{white-space:nowrap}nav>div{display:flex;align-items:center;gap:18px}.login{color:#52636d;text-decoration:none;font-weight:750}.primary,.secondary{display:inline-flex;align-items:center;justify-content:center;min-height:46px;padding:0 18px;border-radius:8px;text-decoration:none;font-weight:800;white-space:nowrap}.primary{background:#176b66;color:#fff}.primary.compact{min-height:38px;padding:0 14px;font-size:13px}.secondary{border:1px solid #cdd9dd;background:#fff;color:#34545a}.hero{display:grid;grid-template-columns:minmax(0,1.35fr) minmax(320px,.65fr);align-items:center;gap:72px;min-height:570px;padding:70px 0}.eyebrow,.benefits article>span,aside>span{color:#278d83;font-size:11px;font-weight:900;letter-spacing:.14em}.hero h1{max-width:720px;margin:14px 0 20px;font-size:clamp(42px,5vw,66px);line-height:1.02;letter-spacing:-.045em}.lead{max-width:650px;color:#5d6d76;font-size:18px;line-height:1.65}.actions{display:flex;flex-wrap:wrap;gap:11px;margin:30px 0 14px}.hero small{color:#718089}aside{display:grid;gap:20px;padding:30px;border:1px solid #d7e3e4;border-radius:18px;background:linear-gradient(145deg,#edf7f5,#fff);box-shadow:0 20px 55px #173e4712}aside strong{font-size:24px;line-height:1.25}ol{display:grid;gap:9px;margin:0;padding:0;list-style:none}li{display:flex;align-items:center;gap:12px;padding:14px;border-radius:9px;background:#fff;color:#41545d;font-weight:750}li b{color:#278d83;font-size:11px}.benefits{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px}.benefits article{padding:25px;border:1px solid #dfe6e9;border-radius:12px;background:#fff}.benefits h2{margin:9px 0;font-size:19px}.benefits p{margin:0;color:#677680;line-height:1.55}@media(max-width:850px){.hero{grid-template-columns:1fr;gap:35px;padding:50px 0}.benefits{grid-template-columns:1fr}.hero h1{font-size:clamp(38px,8vw,56px)}}@media(max-width:560px){main{padding:0 16px 45px}nav{height:auto;padding:14px 0}.brand span{display:none}nav>div{gap:8px}.login{font-size:13px}.primary.compact{padding:0 10px}.hero{min-height:auto;padding:55px 0 42px}.hero h1{font-size:39px}.lead{font-size:16px}.actions{display:grid}.primary,.secondary{width:100%}aside{padding:22px}.benefits article{padding:20px}}
  `],
})
export class LandingPageComponent { readonly deployment = inject(DeploymentConfigService); }
