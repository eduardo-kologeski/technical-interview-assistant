# Technical Interview Assistant

Aplicação para conduzir entrevistas técnicas, registrar avaliações com rapidez e gerar um parecer estruturado e revisável.

## Arquitetura

O sistema é um monólito modular com um único backend Spring Boot e um único banco PostgreSQL:

```text
Angular -> Spring Boot -> PostgreSQL
```

O backend contém módulos internos para autenticação, usuários, vagas, entrevistas, avaliações e auditoria. O projeto não depende do AuthCore em runtime. Consulte [`docs/modular-monolith.md`](docs/modular-monolith.md).

Usuários autenticados também podem manter conhecimentos e disponibilidades em datas específicas, navegando por semana, e consultar a aderência transparente entre perfis e vagas. Consulte [`docs/interviewer-profile.md`](docs/interviewer-profile.md).

### Execução

```powershell
cp .env.example .env
# edite .env com segredos locais antes de continuar
docker compose up --build --wait
```

Serviços: frontend em `http://localhost:4200`, API em `http://localhost:8081` e PostgreSQL interno ao Compose. O Compose recusa iniciar sem `DATABASE_PASSWORD` e `JWT_SECRET`; nunca reutilize os valores locais em produção.

## Demo / Deploy gratuito

A arquitetura de demo pública recomendada mantém o computador do desenvolvedor fora do runtime:

```text
Cloudflare Pages (Angular)
        |
        v
Render Free (Spring Boot)
        |
        v
Neon Free (PostgreSQL)
```

O custo recorrente obrigatório é **R$ 0/mês**, sujeito aos limites, cold starts e políticas dos planos gratuitos. O deploy de produção observa a branch `main`; o Gitflow `feature/* -> develop -> PR -> main` permanece inalterado. Consulte [`docs/deployment-free-tier.md`](docs/deployment-free-tier.md) para configuração, variáveis, limitações e passos manuais.

## Status

O projeto está em construção. O desenho do produto, a arquitetura, o domínio e o backlog estão em [`docs/architecture.md`](docs/architecture.md).

## Produção

O estado atual é **NO-GO para produção comercial**. O free tier documentado é destinado a portfólio, demonstrações e testes com usuários. Consulte a [auditoria de Production Readiness](docs/production-readiness.md), o [progresso de remediação](docs/production-readiness-remediation.md) e o [runbook de deployment, backup e rollback](docs/deployment.md) antes de utilizar dados reais.

## Princípios

- experiência desktop-first e uso eficiente por teclado;
- templates configuráveis para diferentes perfis e stacks;
- decisão final sempre pertencente ao entrevistador;
- rastreabilidade: textos automáticos usam apenas dados registrados;
- autosave e proteção contra perda de dados durante a entrevista.

## Branches

- `main`: versão estável;
- `develop`: integração do desenvolvimento;
- `feature/*`: unidades de trabalho revisáveis.
