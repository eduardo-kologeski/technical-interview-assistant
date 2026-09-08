# SaaS B2B Roadmap

Este roadmap trata da evolução comercial e organizacional. O uso de IA segue uma trilha acadêmica própria, descrita em [`academic-objectives.md`](academic-objectives.md): gerar minutas de parecer a partir das informações registradas, com revisão humana obrigatória. Ranking automático, previsão de desempenho e decisão de aprovação ou reprovação permanecem fora do escopo.

## Current state

Baseline auditada em 30/08/2026: `origin/develop` (`c4270aa`) e o trabalho ainda não integrado da Issue #99 / PR #100 (`codex/saas-multitenancy-invitations`). O código, migrations, testes e workflows atuais são a fonte de verdade; os relatórios antigos de production readiness são evidência histórica.

| Capability | Estado | Evidência | Risco / gap | Prioridade |
|---|---|---|---|---|
| Autenticação, logout e sessão | PARTIAL | JWT curto, refresh hash/rotação/revogação, rate limit | Sem verificação de e-mail, reset, reuse detection ou gestão de devices | P1 (#105) |
| Company, membership e contexto ativo | PARTIAL | V12, `CompanyContext`, seletor frontend | Offboarding e lifecycle incompletos | P0 (#102) |
| Isolamento de vagas e entrevistas | PARTIAL | repositories por `company_id` e teste A/B | Matriz não cobre PDF, search, update/delete e todos os recursos | P0 (#104) |
| Convites | PARTIAL | token aleatório com hash, expiração e uso único | Sem revogar/reenviar/provider; token retorna à UI administrativa | P0 (#103) |
| Autorização | PARTIAL | `COMPANY_ADMIN`/`INTERVIEWER` em services | Policies dispersas; ausência de capabilities | P1 (#106) |
| Usuários, membros e administração | PARTIAL | listagem e criação de convite | Sem editar empresa, papéis e revogar membership | P1 (#110) |
| Vagas | DONE para MVP atual | CRUD, paginação, optimistic lock, tenant scope no PR #100 | Falta ligação com kits/versionamento | P1 (#109) |
| Candidatos | MISSING | Apenas `candidateName` em Interview | Sem entidade, timeline, retenção/export próprios | P1 (#108) |
| Entrevistas e disponibilidade | PARTIAL | persistência, autosave, participantes, perfil e agenda semanal | Sem workflow completo recruiter/hiring manager | P1 |
| Templates, kits e scorecards | MISSING | Requisitos são snapshot simples | Falta versão, rubrica, critérios e editor | P1 (#109) |
| Feedback, parecer e PDF | PARTIAL | avaliação persistida, revisão/PDF frontend | Risco de exposição entre participantes antes do debrief | P0 (#101) |
| Auditoria | PARTIAL | autoria e logs operacionais | Sem trilha append-only consultável tenant-scoped | P1 (#107) |
| Busca e dashboard | PARTIAL | paginação e busca pontual | Sem busca global e analytics operacional | P2 (#112) |
| Billing, planos e quotas | MISSING | Nenhum domínio de entitlement | Comercialização sem limites/modelo de plano | P2 (#111) |
| Notificações e e-mail | MISSING | Link de convite retornado na API | Falta provider abstrato e entrega segura | P0 (#103) |
| API, webhooks e integrações | PARTIAL | REST interna | Sem API pública, idempotency ou webhooks | P2 futuro |
| Observabilidade | PARTIAL | correlation ID, Prometheus, dashboards, alertas | Audit e métricas de produto ainda ausentes | P1 (#107) |
| Backup/restore | DONE tecnicamente | scripts, guardas e runbook | Restore periódico continua responsabilidade operacional | Gate operacional |
| CI/CD e release | DONE para baseline | verify/test/build/scans/imagens imutáveis | PostgreSQL/Testcontainers não executou localmente sem Docker | P0 (#104) |
| Privacidade/LGPD | PARTIAL | data governance e logs minimizados | Retenção e base legal exigem decisão externa | EXTERNAL GOVERNANCE GATE |
| Acessibilidade/performance | PARTIAL | testes a11y, budgets e load scripts | Budgets CSS em warning no PR #100 | P1 hardening |

## Target architecture

Preservar o monólito modular: Angular → reverse proxy → Spring Boot → PostgreSQL. Evoluir módulos internos `identity`, `organization`, `membership`, `invitation`, `vacancy`, `candidate`, `interview`, `assessment`, `report`, `notification`, `billing`, `audit`, `analytics` e `integration`, sem microserviços prematuros.

Entidades tenant-owned carregam `company_id`, constraints e índices tenant-scoped. O tenant ativo é aceito como seleção do cliente, nunca como autorização: toda operação resolve a identidade autenticada e comprova membership ativa server-side.

## Benchmark summary

Produtos adjacentes convergem em entrevistas estruturadas, kits por papel, scorecards/rubricas, permissões robustas, feedback independente e integração com ATS. Para este produto, o maior valor está em padronização, rastreabilidade e redução de trabalho manual. IDE colaborativa, proctoring, gravação e IA decisória permanecem fora da prioridade inicial.

Pontuação relativa (1 baixo, 5 alto; custo/complexidade altos são desfavoráveis):

| Candidato | Customer Value | Security Impact | Production Necessity | Revenue Potential | Cost | Operational Complexity |
|---|---:|---:|---:|---:|---:|---:|
| Tenant isolation regression | 5 | 5 | 5 | 5 | 3 | 2 |
| Membership/invitation lifecycle | 5 | 5 | 5 | 5 | 3 | 3 |
| Kits/scorecards versionados | 5 | 2 | 4 | 5 | 4 | 3 |
| Candidate timeline | 4 | 3 | 4 | 4 | 4 | 3 |
| Analytics operacional | 4 | 2 | 3 | 4 | 3 | 3 |
| SSO/SCIM | 4 | 5 | 2 | 5 | 5 | 5 |

## Threat model summary

Ativos: credenciais, memberships, dados de candidatos, avaliações independentes, pareceres/PDFs, convites, audit trail e backups.

Atores: atacante externo, usuário legítimo de outro tenant, interviewer tentando ação de admin, admin comprometido e operadores da plataforma.

Trust boundaries: navegador/API, JWT/filter/security context, seleção de empresa/membership, service/repository, banco e provider futuro de notificações.

Principais cenários:

- BOLA/IDOR com UUID legítimo de outra empresa → bloquear em repository/service e cobrir em #104;
- `X-Company-Id` manipulado → tratar apenas como seleção e validar membership a cada operação;
- interviewer lendo scorecard alheio → DTO/policy de visibilidade em #101;
- convite roubado/reutilizado → expiração, uso único, revogação e rotação em #103;
- usuário removido usando JWT antigo → membership ativa obrigatória em #102;
- conta admin comprometida extraindo candidatos → capabilities, audit, paginação e futuro rate limit/export control;
- refresh token roubado/replay → rotação existente, com hardening de identidade em #105;
- logs/backups expondo conteúdo → minimização, acesso operacional e gates de governança.

## P0

- #99 — épico reutilizado para fundação SaaS multiempresa; PR #100 em andamento.
- #101 — impedir exposição cruzada de avaliações e pareceres.
- #102 — revogar membership e invalidar sessões antigas no tenant.
- #103 — completar ciclo seguro de convites.
- #104 — ampliar regressão multi-tenant e validar PostgreSQL/Flyway.

## P1

- #105 — verificação de e-mail e recuperação de senha.
- #106 — RBAC centralizado por capabilities.
- #107 — audit trail tenant-scoped append-only.
- #108 — candidatos e timeline.
- #109 — interview kits e scorecards versionados.
- #110 — administração completa da organização.

## P2

- #111 — planos, subscriptions, usage e entitlements sem cobrança real.
- #112 — analytics operacional tenant-scoped sem PII.
- API pública, webhooks idempotentes, calendário/ATS e busca global somente após a fundação P0/P1.

## P3

- #113 — SSO OIDC/SAML e SCIM, bloqueado por decisões externas e IdP de teste.
- Audit export, retenção customizável, service accounts, advanced RBAC, data residency e SLA/SLO enterprise.

## Dependency graph

```text
#99 tenant foundation
  ├─> #101 feedback visibility
  ├─> #102 membership revocation ─┐
  ├─> #103 invitation lifecycle ─┼─> #105 identity ─> #106 capabilities ─> #107 audit
  └─> #104 tenant regression ────┘                      │
                                                       ├─> #108 candidates ─> #112 analytics
                                                       ├─> #109 kits/scorecards
                                                       └─> #110 company admin
                                                            └─> #111 entitlements
```

Bloqueio em uma cadeia não impede Issues independentes de mesma prioridade.

## Production readiness

Antes de qualquer PR `develop → main`: backend verify; frontend test/build; E2E; PostgreSQL/Flyway; matriz multi-tenant; regressão de autorização; dependency/container scans; smoke; backup/restore; acessibilidade; configuração prod; documentação e revisão de migrations. O PR de release deve ser marcado `REQUIRES HUMAN REVIEW` e não pode ser mergeado automaticamente.

## Enterprise readiness

SSO/SCIM, MFA enforcement, domain verification, audit export, retenção customizável, service accounts, advanced RBAC, data residency e SLA são uma trilha separada e não bloqueiam o MVP B2B.

## Completed

- #101 — respostas gerais sem avaliações de participantes, mutações legadas protegidas e regressões de peeking/tenant escape; integrado no PR #100.
- #102 — revogação tenant-scoped de membership, proteção do último administrador e invalidação imediata de sessões antigas; integrado no PR #115.
- #103 — revogação, reenvio com rotação, entrega abstrata e token oculto por padrão; integrado no PR #116.
- #104 — matriz de regressão tenant A/B e PostgreSQL/Flyway obrigatório no CI; integrado no PR #117.
- Autenticação interna com BCrypt, JWT e refresh rotation.
- Persistência PostgreSQL/Flyway, optimistic locking e autosave.
- Backup/restore com validação, observabilidade básica, CI e supply-chain scans.
- Fundação inicial Company/Membership/Invitation e participantes independentes integrada em `develop` pelo PR #100.
- Issue #96 / PR #97 reutilizados para mobile e revisão/PDF; permanecem em andamento e não são duplicados.

## Blocked

- Execução local de Testcontainers/PostgreSQL: Docker indisponível nesta máquina; o teste foi honestamente reportado como ignorado. #104 exige evidência não ignorada em CI/ambiente com Docker.
- #113: `BLOCKED — EXTERNAL DECISION` para IdP, domínio e políticas enterprise.

## External governance gates

- base legal, avisos de privacidade e prazos finais de retenção LGPD;
- DPA/suboperadores e política corporativa de incident response;
- fornecedor de e-mail, domínio/DNS e credenciais de produção;
- política de SSO/break-glass e SLA comercial.

Esses gates permanecem abertos e não autorizam inventar aprovação jurídica, contratar serviço ou usar dados reais.
