# Production Readiness — progresso de remediação

Baseline inicial: `develop` em `85e0de6`, avaliada como **NO-GO** em 27/08/2026. O diagnóstico original permanece em [production-readiness.md](production-readiness.md).

Esta evidência corresponde à pilha de PRs #8 a #13. Enquanto essas PRs não forem revisadas e integradas, `develop` continua com o estado descrito no diagnóstico original.

## Gate P0

| Item | Estado | Evidência | Pendência |
|---|---|---|---|
| P0-01 Persistência | DONE | Entidades JPA para usuários, vagas, entrevistas, perfil e disponibilidade; avaliação versionada na entrevista; migrations V1–V7 | Validação PostgreSQL real segue no P1 |
| P0-02 Autosave | DONE | Debounce, fila sequencial, confirmação backend, erro/retry, reload e teste Angular | Exercício E2E com falha real segue no P1 |
| P0-03 Auth e sessão | DONE | Cadastro/login interno, BCrypt, JWT, rotação/revogação de refresh, logout, `/users/me` e testes de sessão | Rate limit/brute force segue no P1 |
| P0-04 Schema e histórico | PARTIAL | Flyway V1–V7, constraints, `ddl-auto=validate`, snapshot imutável e soft delete de vaga | Flyway em PostgreSQL/Testcontainers: NOT VERIFIED |
| P0-05 Auditoria | DONE | Autoria derivada do `SecurityContext`; teste A cria/B edita preserva criador e entrevistador | Eventos estruturados seguem no P1 |
| P0-06 Backup/restore | PARTIAL | Scripts PowerShell/shell, dump customizado, checksum e recusa de destino não vazio | Restore isolado: NOT VERIFIED — ENVIRONMENT REQUIRED; RPO/RTO externos |
| P0-07 Produção segura | PARTIAL | Profile `prod` fail-fast, DB/JWT/CORS externos, graceful shutdown, probes, Nginx non-root, headers e proxy same-origin | TLS/ingress, container e homologação: NOT VERIFIED — ENVIRONMENT REQUIRED |
| P0-08 Governança | PARTIAL | Inventário, minimização, acesso atual, exclusão, retenção e backups documentados em `data-governance.md` | Retenção, base legal e responsáveis exigem aprovação externa |

**Resultado do gate P0: FAIL / NO-GO**, pois P0-04, P0-06, P0-07 e P0-08 ainda dependem de verificação ou decisão externa. Não há outro P0 de implementação local claramente executável identificado nesta revisão; o trabalho pode avançar para P1 sem declarar o sistema pronto para produção.

## Evidências automatizadas

- backend: 25 testes aprovados após persistência/autosave;
- frontend: 38 testes aprovados após finalização coordenada;
- build Angular: aprovado, com warnings de budget/CommonJS já registrados;
- secrets audit: nenhum secret produtivo identificado; valores do Compose e profile padrão são exclusivamente locais e o profile `prod` exige valores externos;
- Docker/Nginx: NOT VERIFIED neste host;
- PostgreSQL/Flyway real: NOT VERIFIED neste host;
- restore: NOT VERIFIED neste host.

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

1. Disponibilizar Docker ou PostgreSQL isolado e executar Flyway desde banco vazio e sobre uma cópia anonimizada compatível.
2. Executar backup e restore, registrar checksum, duração, contagens e integridade referencial.
3. Validar imagem Nginx/backend, TLS, headers, CORS e probes em homologação.
4. Aprovar retenção, acesso, exclusão, RPO/RTO e tratamento de PDFs/backups.

Essas ações não autorizam deploy nem uso de candidatos reais.
