# SaaS B2B Roadmap

Este roadmap trata da evolução comercial e organizacional. O uso de IA segue uma trilha acadêmica própria, descrita em [`academic-objectives.md`](academic-objectives.md): gerar minutas de parecer a partir das informações registradas, com revisão humana obrigatória. Ranking automático, previsão de desempenho e decisão de aprovação ou reprovação permanecem fora do escopo.

## Current state

Baseline auditada em 10/09/2026: a fundação SaaS B2B da Issue #99 e os incrementos #101–#112 estão integrados. O código, as migrations, os testes e os workflows atuais são a fonte de verdade; os relatórios antigos de production readiness são evidência histórica.

| Capability | Estado | Evidência | Risco / gap | Prioridade |
|---|---|---|---|---|
| Autenticação, logout e sessão | DONE para o escopo atual | JWT curto, refresh hash/rotação/revogação, rate limit, verificação de e-mail e reset de uso único | Gestão de devices permanece futura | Concluído (#105) |
| Company, membership e contexto ativo | DONE | V12, `CompanyContext`, seletor frontend, offboarding e invalidação imediata | Hardening enterprise permanece em #113 | Concluído (#102) |
| Isolamento de vagas e entrevistas | DONE | repositories por `company_id`, matriz A/B para CRUD, busca, workspace, parecer e PDF | Novos recursos tenant-owned devem entrar na mesma matriz | Concluído (#104) |
| Convites | DONE | token aleatório com hash, expiração, uso único, revogação, reenvio e `InvitationDelivery` | Provider e credenciais de produção são gate operacional | Concluído (#103) |
| Autorização | DONE | `CompanyAuthorizationPolicy` e capabilities tenant-scoped | Papéis enterprise avançados permanecem futuros | Concluído (#106) |
| Usuários, membros e administração | DONE | edição da empresa, listagem paginada, papéis, revogação e proteção do último admin | Sem gap do escopo MVP | Concluído (#110) |
| Vagas | DONE para MVP atual | CRUD, paginação, optimistic lock, tenant scope e ligação com kits versionados | Sem gap do escopo MVP | Concluído (#109) |
| Candidatos | DONE | entidade e timeline tenant-scoped, deduplicação e vínculo com vaga | Retenção final depende de governança | Concluído (#108) |
| Entrevistas e disponibilidade | PARTIAL | persistência, autosave, participantes, perfil e agenda semanal | Sem workflow completo recruiter/hiring manager | P1 |
| Templates, kits e scorecards | DONE | kits versionados por vaga, requisitos e rubrica preservados como snapshot | Evolução editorial permanece incremental | Concluído (#109) |
| Feedback, parecer e PDF | DONE para o escopo atual | avaliação persistida, revisão/PDF e proteção contra leitura de feedback alheio | Políticas de debrief mais amplas permanecem futuras | Concluído (#101) |
| Auditoria | DONE | trilha append-only consultável e tenant-scoped | Export e retenção customizável permanecem enterprise | Concluído (#107) |
| Busca e dashboard | DONE para o escopo atual | busca paginada e analytics operacional sem PII | Busca global permanece futura | Concluído (#112) |
| Billing, planos e quotas | DONE para o escopo sem cobrança | planos, subscriptions, usage e entitlements | Cobrança real permanece fora desta fase | Concluído (#111) |
| Notificações e e-mail | DONE na fronteira da aplicação | `InvitationDelivery`, token oculto por padrão e configuração externa | Provider de produção depende de decisão operacional | Concluído (#103) |
| API, webhooks e integrações | PARTIAL | REST interna | Sem API pública, idempotency ou webhooks | P2 futuro |
| Observabilidade | DONE para a baseline | correlation ID, Prometheus, dashboards, alertas, audit trail e métricas operacionais | SLO comercial depende de definição externa | Concluído (#107/#112) |
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

- #99 — fundação SaaS multiempresa concluída.
- #101–#104 — segurança de feedback, memberships, convites e regressão multi-tenant concluídas.

## P1

- #105–#110 — identidade, capabilities, auditoria, candidatos, kits e administração concluídos.

## P2

- #111–#112 — entitlements sem cobrança e analytics operacional sem PII concluídos.
- API pública, webhooks idempotentes, calendário/ATS e busca global permanecem futuros.

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

- #99 — fundação SaaS B2B multiempresa, contexto ativo, landing page, migração e acesso por convite.
- #101 — respostas gerais sem avaliações de participantes, mutações legadas protegidas e regressões de peeking/tenant escape; integrado no PR #100.
- #102 — revogação tenant-scoped de membership, proteção do último administrador e invalidação imediata de sessões antigas; integrado no PR #115.
- #103 — revogação, reenvio com rotação, entrega abstrata e token oculto por padrão; integrado no PR #116.
- #104 — matriz de regressão tenant A/B e PostgreSQL/Flyway obrigatório no CI; integrado no PR #117.
- Autenticação interna com BCrypt, JWT e refresh rotation.
- Persistência PostgreSQL/Flyway, optimistic locking e autosave.
- Backup/restore com validação, observabilidade básica, CI e supply-chain scans.
- Fundação inicial Company/Membership/Invitation e participantes independentes integrada pelo PR #100 e completada nos incrementos #101–#112.
- Issue #96 / PR #97 reutilizados para mobile e revisão/PDF; permanecem em andamento e não são duplicados.

## Blocked

- #113: `BLOCKED — EXTERNAL DECISION` para IdP, domínio e políticas enterprise.

## External governance gates

- base legal, avisos de privacidade e prazos finais de retenção LGPD;
- DPA/suboperadores e política corporativa de incident response;
- fornecedor de e-mail, domínio/DNS e credenciais de produção;
- política de SSO/break-glass e SLA comercial.

Esses gates permanecem abertos e não autorizam inventar aprovação jurídica, contratar serviço ou usar dados reais.
