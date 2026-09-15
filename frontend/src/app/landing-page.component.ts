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
        <div>
          <a class="login team-link" href="#empresas">Uso em equipe</a
          ><a class="login" routerLink="/auth/login">Entrar</a>
          @if (deployment.setupRequired()) {
            <a class="primary compact" routerLink="/setup">Configurar instalação</a>
          } @else if (deployment.publicSignupEnabled()) {
            <a class="primary compact" routerLink="/auth/register">Criar conta</a>
          }
        </div>
      </nav>

      <section class="hero">
        <div>
          <p class="eyebrow">AVALIAÇÃO TÉCNICA COM APOIO LOCAL</p>
          <h1>Registre evidências com rapidez e transforme-as em uma minuta revisável.</h1>
          <p class="lead">
            Defina os requisitos antes da conversa, use atalhos para anotar o que observou e gere
            uma minuta de parecer baseada nos seus registros.
          </p>
          <div class="actions">
            @if (deployment.setupRequired()) {
              <a class="primary" routerLink="/setup">Configurar instalação</a>
            } @else if (deployment.publicSignupEnabled()) {
              <a class="primary" routerLink="/auth/register">Criar minha conta</a>
            }
            <a class="secondary" routerLink="/auth/login">Entrar</a>
          </div>
          <small
            >A minuta é gerada localmente com Ollama. A revisão, a conclusão e a aprovação continuam
            sob sua responsabilidade.</small
          >
        </div>
        <aside aria-label="Fluxo da plataforma">
          <span>FLUXO INDIVIDUAL</span>
          <strong>Do requisito ao parecer aprovado por você</strong>
          <ol>
            <li><b>01</b> Defina os requisitos</li>
            <li><b>02</b> Registre evidências com atalhos</li>
            <li><b>03</b> Gere e revise a minuta</li>
            <li><b>04</b> Escreva sua conclusão e aprove</li>
          </ol>
        </aside>
      </section>

      <section class="benefits" aria-label="Benefícios">
        <article>
          <span>RAPIDEZ</span>
          <h2>Atalhos durante a conversa</h2>
          <p>
            Frases de apoio registram experiências, pontos fortes e pontos de atenção junto ao
            requisito avaliado.
          </p>
        </article>
        <article>
          <span>APOIO LOCAL</span>
          <h2>Minuta baseada nos registros</h2>
          <p>
            O backend usa o Ollama local para redigir uma minuta sem enviar os dados da avaliação a
            serviços externos.
          </p>
        </article>
        <article>
          <span>DECISÃO HUMANA</span>
          <h2>Você conclui o parecer</h2>
          <p>
            A minuta permanece identificada para revisão. Somente o entrevistador escreve a
            conclusão e aprova o parecer final.
          </p>
        </article>
      </section>

      <section class="enterprise" id="empresas" aria-labelledby="enterprise-title">
        <div>
          <span>USO EM EQUIPE</span>
          <h2 id="enterprise-title">
            Vagas, kits e entrevistas também podem ser organizados por empresa.
          </h2>
          <p>
            O espaço empresarial preserva requisitos compartilhados, participantes e histórico com
            autorização por organização.
          </p>
        </div>
        @if (deployment.publicSignupEnabled()) {
          <a class="secondary" routerLink="/auth/register" [queryParams]="{ company: true }"
            >Cadastrar empresa</a
          >
        } @else {
          <a class="secondary" routerLink="/auth/login">Acessar empresa</a>
        }
      </section>
    </main>
  `,
  styles: [
    `
      :host {
        display: block;
        min-height: 100vh;
        background: #f6f8f9;
        color: #18252d;
        font-family: Inter, system-ui, sans-serif;
      }
      main {
        max-width: 1180px;
        margin: auto;
        padding: 0 28px 70px;
      }
      nav {
        height: 76px;
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 24px;
      }
      .brand {
        display: flex;
        align-items: center;
        gap: 11px;
        color: #1e3039;
        text-decoration: none;
        font-weight: 850;
      }
      .brand b {
        display: grid;
        place-items: center;
        width: 36px;
        height: 36px;
        border-radius: 9px;
        background: #165d7a;
        color: #fff;
      }
      .brand span {
        white-space: nowrap;
      }
      nav > div {
        display: flex;
        align-items: center;
        gap: 18px;
      }
      .login {
        color: #52636d;
        text-decoration: none;
        font-weight: 750;
      }
      .primary,
      .secondary {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        min-height: 46px;
        padding: 0 18px;
        border-radius: 8px;
        text-decoration: none;
        font-weight: 800;
        white-space: nowrap;
      }
      .primary {
        background: #176b66;
        color: #fff;
      }
      .primary.compact {
        min-height: 38px;
        padding: 0 14px;
        font-size: 13px;
      }
      .secondary {
        border: 1px solid #cdd9dd;
        background: #fff;
        color: #34545a;
      }
      .hero {
        display: grid;
        grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.65fr);
        align-items: center;
        gap: 72px;
        min-height: 570px;
        padding: 70px 0;
      }
      .eyebrow,
      .benefits article > span,
      aside > span,
      .enterprise span {
        color: #278d83;
        font-size: 11px;
        font-weight: 900;
        letter-spacing: 0.14em;
      }
      .hero h1 {
        max-width: 720px;
        margin: 14px 0 20px;
        font-size: clamp(42px, 5vw, 66px);
        line-height: 1.02;
        letter-spacing: -0.045em;
      }
      .lead {
        max-width: 650px;
        color: #5d6d76;
        font-size: 18px;
        line-height: 1.65;
      }
      .actions {
        display: flex;
        flex-wrap: wrap;
        gap: 11px;
        margin: 30px 0 14px;
      }
      .hero small {
        color: #718089;
        line-height: 1.55;
      }
      aside {
        display: grid;
        gap: 20px;
        padding: 30px;
        border: 1px solid #d7e3e4;
        border-radius: 18px;
        background: linear-gradient(145deg, #edf7f5, #fff);
        box-shadow: 0 20px 55px #173e4712;
      }
      aside strong {
        font-size: 24px;
        line-height: 1.25;
      }
      ol {
        display: grid;
        gap: 9px;
        margin: 0;
        padding: 0;
        list-style: none;
      }
      li {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 14px;
        border-radius: 9px;
        background: #fff;
        color: #41545d;
        font-weight: 750;
      }
      li b {
        color: #278d83;
        font-size: 11px;
      }
      .benefits {
        display: grid;
        grid-template-columns: repeat(3, minmax(0, 1fr));
        gap: 16px;
      }
      .benefits article {
        padding: 25px;
        border: 1px solid #dfe6e9;
        border-radius: 12px;
        background: #fff;
      }
      .benefits h2 {
        margin: 9px 0;
        font-size: 19px;
      }
      .benefits p,
      .enterprise p {
        margin: 0;
        color: #677680;
        line-height: 1.55;
      }
      .enterprise {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 36px;
        margin-top: 18px;
        padding: 28px;
        border: 1px solid #d7e3e4;
        border-radius: 14px;
        background: #edf7f5;
      }
      .enterprise h2 {
        max-width: 760px;
        margin: 8px 0;
        font-size: 22px;
      }
      .enterprise .secondary {
        flex: 0 0 auto;
      }
      @media (max-width: 850px) {
        .hero {
          grid-template-columns: 1fr;
          gap: 35px;
          padding: 50px 0;
        }
        .benefits {
          grid-template-columns: 1fr;
        }
        .hero h1 {
          font-size: clamp(38px, 8vw, 56px);
        }
        .enterprise {
          align-items: stretch;
          flex-direction: column;
        }
        .enterprise .secondary {
          align-self: start;
        }
      }
      @media (max-width: 560px) {
        main {
          padding: 0 16px 45px;
        }
        nav {
          height: auto;
          padding: 14px 0;
        }
        .brand span,
        .team-link {
          display: none;
        }
        nav > div {
          gap: 8px;
        }
        .login {
          font-size: 13px;
        }
        .primary.compact {
          padding: 0 10px;
        }
        .hero {
          min-height: auto;
          padding: 55px 0 42px;
        }
        .hero h1 {
          font-size: 39px;
        }
        .lead {
          font-size: 16px;
        }
        .actions {
          display: grid;
        }
        .primary,
        .secondary {
          width: 100%;
        }
        aside {
          padding: 22px;
        }
        .benefits article,
        .enterprise {
          padding: 20px;
        }
        .enterprise .secondary {
          align-self: stretch;
        }
      }
    `,
  ],
})
export class LandingPageComponent {
  readonly deployment = inject(DeploymentConfigService);
}
