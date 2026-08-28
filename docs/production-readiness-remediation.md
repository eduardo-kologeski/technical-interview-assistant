# Production Readiness — progresso de remediação

Baseline inicial: `develop` em `85e0de6`, avaliada como **NO-GO** em 27/08/2026. O diagnóstico original permanece em [production-readiness.md](production-readiness.md).

Esta evidência corresponde à pilha de PRs #8 a #26 e aos incrementos P2 subsequentes. Enquanto essas PRs não forem revisadas e integradas, `develop` continua com o estado descrito no diagnóstico original.

## Gate P0

| Item | Estado | Evidência | Pendência |
|---|---|---|---|
| P0-01 Persistência | DONE | Entidades JPA para usuários, vagas, entrevistas, perfil e disponibilidade; avaliação versionada na entrevista; migrations V1–V11 | Validação sobre cópia anonimizada segue externa |
| P0-02 Autosave | DONE | Debounce, fila sequencial, confirmação backend, erro/retry, reload e teste Angular | Exercício E2E com falha real segue no P1 |
| P0-03 Auth e sessão | DONE | Cadastro/login interno, BCrypt, JWT, rotação/revogação de refresh, logout, `/users/me`, rate limiting e testes de sessão | Política distribuída segue como evolução |
| P0-04 Schema e histórico | DONE | Flyway V1–V11, constraints, `ddl-auto=validate`, snapshot imutável, soft delete e teste Testcontainers em PostgreSQL 17.6 | CI valida todas as migrations sem skips |
| P0-05 Auditoria | DONE | Autoria derivada do `SecurityContext`; teste A cria/B edita preserva criador e entrevistador; eventos operacionais estruturados | Centralização/retenção de logs depende do ambiente |
| P0-06 Backup/restore | PARTIAL | Scripts PowerShell/shell, dump customizado, checksum e recusa de destino não vazio | Restore isolado: NOT VERIFIED — ENVIRONMENT REQUIRED; RPO/RTO externos |
| P0-07 Produção segura | PARTIAL | Profile `prod` fail-fast, DB/JWT/CORS externos, graceful shutdown, probes, Nginx non-root, headers e proxy same-origin | TLS/ingress, container e homologação: NOT VERIFIED — ENVIRONMENT REQUIRED |
| P0-08 Governança | PARTIAL | Inventário, minimização, acesso atual, exclusão, retenção e backups documentados em `data-governance.md` | Retenção, base legal e responsáveis exigem aprovação externa |

**Resultado do gate P0: FAIL / NO-GO**, pois P0-06, P0-07 e P0-08 ainda dependem de verificação ou decisão externa. P0-04 foi validado em PostgreSQL real pelo CI do PR #20. Não há outro P0 de implementação local claramente executável identificado nesta revisão; o trabalho pode avançar para P1 sem declarar o sistema pronto para produção.

## Progresso P1

| Item | Estado | Evidência | Pendência |
|---|---|---|---|
| Observabilidade | DONE | Correlation ID, logs operacionais seguros e métricas Prometheus HTTP/JVM/Hikari | Dashboards e alertas são P2/ambiente |
| Concorrência | DONE | Versionamento otimista em entrevistas e vagas; autosave/edição obsoletos retornam 409 | — |
| Idempotência | DONE | Conclusão/finalização toleram repetição e o frontend bloqueia clique duplo | — |
| PostgreSQL/Flyway | DONE | Testcontainers executado no CI com PostgreSQL 17.6, migrations V1–V11 e zero skips | Cópia anonimizada segue externa |
| Tratamento de erros | DONE | Problem Details consistente, `correlationId`, mensagens seguras e UX de conflito | Revisão contínua por novo endpoint |
| Rate limiting | DONE | Limites e `Retry-After` para login, cadastro e refresh, com proxy confiável | Armazenamento distribuído é P2 |
| Performance | DONE | Vagas, histórico e entrevistadores paginados; filtros de entrevistadores no banco; associações carregadas em lote; índices V9/V10 alinhados às buscas | Monitorar planos reais e volume em homologação |
| PDF | DONE | Paginação de seções extensas, truncamento seguro no cabeçalho, nomes de arquivo normalizados e teste com acentos/alto volume; PDF QA de 9 páginas inspecionado visualmente e por extração de texto | Manter regressão visual quando o layout do parecer mudar |
| Deploy/containers | PARTIAL | Probes, graceful shutdown, imagens multi-stage/non-root com healthcheck, limite de payload no proxy, Compose sem segredos padrão, build e scan bloqueante HIGH/CRITICAL das imagens no CI | Smoke da stack completa e TLS ainda exigem Docker/homologação externa |
| Acessibilidade | DONE | Foco visível global, link de salto, estados ARIA, anúncios de autosave/progresso, movimento reduzido, contraste do fluxo principal e regressão axe-core WCAG A/AA em entrevista/revisão | Manter validação manual com leitor de tela nas releases de UI |
| Dependências | DONE | `npm audit` bloqueante no CI, OSV-Scanner para Maven/npm em PRs e varredura semanal, Dependabot sem auto-merge | Triar alertas novos e atualizar supressões somente com justificativa documentada |
| E2E crítico | DONE | Playwright cobre cadastro, sessão após reload, vaga, entrevista, requisitos, complementar, comportamental, falha/retry de autosave, reload, finalização, feedback e PDF | Manter o cenário no CI Chromium e ampliar somente quando surgir regressão real |

## Evidências automatizadas

- backend: suíte Maven `verify` aprovada localmente; CI executa também o teste PostgreSQL sem skip;
- frontend: 44 testes aprovados;
- build Angular: aprovado, com warnings de budget/CommonJS já registrados;
- secrets audit: nenhum secret produtivo identificado; valores do Compose e profile padrão são exclusivamente locais e o profile `prod` exige valores externos;
- Docker/Nginx: NOT VERIFIED neste host;
- PostgreSQL/Flyway real: VERIFIED no CI com PostgreSQL 17.6 e todas as migrations da respectiva pilha; localmente o teste é ignorado quando Docker não está disponível;
- restore: NOT VERIFIED neste host.

## Progresso P2

| Item | Estado | Evidência | Pendência |
|---|---|---|---|
| SLOs e alertas | PARTIAL | SLIs/error budget em `slo.md`, recording/alert rules Prometheus e dashboard Grafana versionados | Validar com promtool, calibrar e instalar no ambiente |
| Runbooks | DONE | Banco indisponível, falha de deploy, autenticação e backup/restore documentados | Exercitar em game day antes do go-live |
| Versionamento | DONE | SemVer em `APP_VERSION` e commit em `GIT_SHA`, incorporados à imagem, Actuator autenticado e todas as linhas de log | Pipeline de release deve fornecer o SemVer aprovado |
| Browsers | PARTIAL | Matriz e critérios documentados; E2E Chromium no CI | Smoke Edge/Firefox/Safari requer ambiente/dispositivos |
| Carga | PARTIAL | Cenário k6 cobre login, vagas, histórico, entrevista, autosave e registro de PDF; renderização volumosa possui orçamento frontend de 5 s | Executar em homologação isolada e calibrar limites |
| Autorização | PARTIAL | Modelo futuro por capacidades e decisões pendentes documentados | Papéis e escopos dependem de aprovação de negócio |
| Proteção de branches | PARTIAL | Checks e regras recomendadas documentados | Configuração no GitHub exige verificação administrativa |
| Checklist de produção | DONE | Gate consolidado em `production-checklist.md` | Preenchimento exige ambiente e aprovações externas |
| Limpeza de sessões | DONE | Refresh tokens expirados/revogados são removidos por job idempotente, transacional e configurável; índices V11 suportam a busca | Monitorar duração/volume em produção |

## Controles entregues na pilha

1. Monólito com autenticação interna, usuário e sessão persistente.
2. Vagas auditadas e desativáveis.
3. Entrevistas persistidas com snapshot de vaga e identidade autenticada.
4. Avaliações, complementares, comportamentais, anotações e feedback persistidos como documento versionado.
5. Autosave honesto, ordenado, recuperável e com retry.
6. Finalização somente após confirmação do último autosave e operação idempotente.
7. Configuração produtiva sem fallback de DB/JWT/CORS.
8. Backup/restore preparado e governança documentada.
9. Frontend produtivo usando rota same-origin `/api`.

## Ações externas necessárias para encerrar P0

1. Executar Flyway sobre uma cópia anonimizada compatível; banco vazio em PostgreSQL 17.6 já foi validado no CI.
2. Executar backup e restore, registrar checksum, duração, contagens e integridade referencial.
3. Validar imagem Nginx/backend, TLS, headers, CORS e probes em homologação.
4. Aprovar retenção, acesso, exclusão, RPO/RTO e tratamento de PDFs/backups.

Essas ações não autorizam deploy nem uso de candidatos reais.
