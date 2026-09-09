# Technical Interview Assistant

Uma única base de código suporta `DEPLOYMENT_MODE=SAAS` e `DEPLOYMENT_MODE=ON_PREMISE`.
O modo SaaS preserva o cadastro público e multi-tenancy existentes; o modo On-Premises aplica
defaults seguros que desabilitam cadastro público, multi-tenancy e billing. Consulte
`docs/architecture.md` para o contrato de capabilities.

Aplicação para conduzir avaliações técnicas, registrar evidências com rapidez e apoiar a elaboração de pareceres técnicos.

O foco acadêmico do TIA é a **geração assistida de minutas de pareceres de avaliações técnicas a partir das informações registradas durante a avaliação**. A minuta deve permanecer vinculada aos dados de entrada, ser revisável e deixar explícita qualquer insuficiência de informação. O parecer final e a decisão sobre a pessoa avaliada continuam sendo responsabilidade do entrevistador.

O projeto não usa IA para aprovar ou reprovar pessoas, classificar candidatos automaticamente, prever desempenho profissional ou produzir conclusões sem suporte nos registros. Os [objetivos acadêmicos e critérios de avaliação](docs/academic-objectives.md) documentam esse recorte e sua rastreabilidade no backlog.

## Arquitetura

O sistema é um monólito modular com um único backend Spring Boot e um único banco PostgreSQL:

```text
Angular -> Spring Boot -> PostgreSQL
                  |
                  +-> Ollama local (somente pelo backend)
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

O Ollama não faz parte do Compose. Para habilitar a geração de minutas, instale-o no host, escolha explicitamente um modelo local e configure `OLLAMA_BASE_URL` e `OLLAMA_MODEL` no `.env`. O Angular nunca acessa o Ollama. Sem essas variáveis ou com o serviço indisponível, o backend responde com erro controlado e preserva a avaliação. Consulte [desenvolvimento local com Ollama](docs/ollama-local-development.md).

## Status

O projeto está em construção. O desenho do produto, a arquitetura, o domínio e o backlog estão em [`docs/architecture.md`](docs/architecture.md).

## Produção

O estado atual é **NO-GO para produção**. Consulte a [auditoria de Production Readiness](docs/production-readiness.md), o [progresso de remediação](docs/production-readiness-remediation.md) e o [runbook de deployment, backup e rollback](docs/deployment.md) antes de utilizar dados reais.

## Princípios

- experiência desktop-first e uso eficiente por teclado;
- templates configuráveis para diferentes perfis e stacks;
- preparação dos requisitos antes da avaliação e frases rápidas para registrar evidências, pontos fortes e pontos de atenção;
- decisão final sempre pertencente ao entrevistador;
- rastreabilidade: minutas assistidas usam apenas informações registradas na avaliação;
- revisão humana obrigatória antes de qualquer parecer final;
- PDF disponível somente para o parecer aprovado, sem alterar o estado da avaliação;
- autosave e proteção contra perda de dados durante a entrevista.

## Branches

- `main`: versão estável;
- `develop`: integração do desenvolvimento;
- `feature/*`: unidades de trabalho revisáveis.
