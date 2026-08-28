# Production Readiness

Auditoria realizada em 27/08/2026 sobre `origin/develop` (`85e0de6`). Pull requests e branches de feature ainda não integrados não foram considerados parte da versão implantável.

## Resumo executivo

**Status: NO-GO para uso com entrevistas reais.**

A aplicação em `develop` é um protótipo funcional de interface. Os dados da entrevista existem apenas em signals Angular, o indicador de autosave é um temporizador sem operação de rede e o backend contém somente o domínio de cálculo de score. Não existem autenticação, APIs de negócio, persistência de entrevistas, migrations de schema, autorização ou trilha de auditoria na versão auditada.

O build e os testes atuais passam, mas cobrem apenas uma fração pequena do comportamento necessário. Se essa versão for usada amanhã, recarregar ou fechar a página elimina a entrevista em andamento. Qualquer pessoa com acesso ao endereço pode usar a aplicação e gerar um PDF contendo dados de candidato. Não há mecanismo verificável de backup ou restauração porque os dados de negócio ainda não são persistidos.

## Estado atual

| Área | Evidência observada | Estado |
|---|---|---|
| Arquitetura | Angular e Spring Boot existem, mas não estão integrados por API de negócio | Parcial |
| Autenticação e sessão | Não existem endpoints, usuário, JWT, refresh token, login ou `/users/me` | Ausente |
| Autorização | Não há controle de acesso | Ausente |
| Persistência | Não existem entidades ou repositories JPA de negócio | Ausente |
| Autosave | `markSaving()` muda o texto para “Salvo” após 450 ms sem persistir | Falso positivo crítico |
| PostgreSQL | Dependência e Compose existem; nenhuma tabela de negócio é criada | Estrutura somente |
| Flyway | Dependência habilitada; diretório de migrations ausente em `develop` | Ausente |
| Auditoria | Não existem `createdBy`, `updatedBy`, autoria de entrevista ou eventos | Ausente |
| Snapshot de vaga | Apenas descrito em documentação, sem implementação ou teste | Ausente |
| PDF | Gerado sob demanda no navegador e baixado localmente | Parcial |
| Health | Actuator expõe `health` e `info`; integração com PostgreSQL não foi validada em container | Parcial |
| Observabilidade | Apenas logs padrão do Spring; sem eventos, correlation ID ou métricas operacionais definidas | Insuficiente |
| CI | Backend verify, frontend test e build em PR/push | Presente |
| Docker | Backend multi-stage e non-root; frontend Nginx usa configuração padrão e HTTP | Parcial |
| Backup/restore | Não existe procedimento nem evidência de restore testado | Ausente |

### Coerência com o monólito modular

A arquitetura desejada `Angular → Spring Boot → PostgreSQL` está documentada, mas ainda não corresponde ao código de `develop`. No backend existe apenas o pacote `interview.domain` para score. Auth, Users, Vagas, Perfil, Disponibilidade, Entrevistas persistidas, Auditoria, Pareceres e PDF backend não existem nessa baseline. Portanto, ainda não é possível avaliar dependências circulares ou N+1 desses módulos; eles são **não verificados**, não aprovados.

Há diversas branches de feature remotas com implementações posteriores. Enquanto não forem revisadas e integradas em `develop`, elas não compõem um artefato implantável e não reduzem os bloqueios desta auditoria.

## P0 — Bloqueia produção

### P0-01 — Dados de entrevista não são persistidos

- **Problema:** candidato, avaliações, comentários, anotações e parecer vivem somente na memória do navegador.
- **Risco:** perda total em F5, fechamento da aba, crash ou troca de máquina.
- **Evidência:** `frontend/src/app/app.ts` mantém todo o estado em signals; não há cliente HTTP de negócio.
- **Solução requerida:** API persistente, transações, entidades, migrations e recuperação da entrevista por ID.
- **Teste de saída:** iniciar, preencher, recarregar, reabrir o navegador e confirmar todos os dados no PostgreSQL.

### P0-02 — Autosave informa “Salvo” sem salvar

- **Problema:** `markSaving()` apenas aguarda 450 ms e muda o estado visual.
- **Risco:** o entrevistador acredita que os dados estão seguros quando não estão.
- **Solução requerida:** estado derivado de resposta confirmada do backend, erro persistente, retry explícito e proteção contra respostas fora de ordem.
- **Teste de saída:** simular backend indisponível e comprovar que a UI mostra “Falha ao salvar”, nunca “Salvo”.

### P0-03 — Ausência completa de autenticação, sessão e autorização

- **Problema:** não existem cadastro, login, hash de senha, JWT, refresh, logout, revogação ou restauração de sessão.
- **Risco:** acesso irrestrito a dados de candidatos e ações de negócio.
- **Solução requerida:** autenticação server-side, senha com algoritmo forte, sessão persistente testada e autorização mínima por usuário/capacidade.
- **Teste de saída:** matriz de testes para F5, reabertura, expiração, refresh expirado, logout e token revogado.

### P0-04 — Schema, integridade e histórico não existem

- **Problema:** Flyway está habilitado, mas não há migrations de negócio, constraints, foreign keys, snapshot ou soft delete.
- **Risco:** não há fonte de verdade, histórico preservado ou caminho seguro de evolução de dados.
- **Solução requerida:** migrations aditivas revisadas em PostgreSQL real, `ddl-auto=validate`, constraints e snapshot imutável da vaga na entrevista.
- **Teste de saída:** Testcontainers executando Flyway desde banco vazio e teste obrigatório de alteração da vaga sem modificar entrevista histórica.

### P0-05 — Autoria e auditoria não são confiáveis

- **Problema:** não existem `createdBy`, `updatedBy`, entrevistador autenticado, `startedAt` ou `completedAt` persistidos.
- **Risco:** ações não atribuíveis e possibilidade futura de falsificação pelo frontend.
- **Solução requerida:** identidade derivada exclusivamente do contexto autenticado e trilha append-only ou campos auditáveis protegidos.
- **Teste de saída:** usuário A cria, usuário B altera, mantendo `createdBy=A` e registrando `updatedBy=B`.

### P0-06 — Backup e restauração não definidos nem testados

- **Problema:** existe apenas volume local no Compose, que não é estratégia de backup.
- **Risco:** perda irrecuperável após falha, exclusão ou corrupção.
- **Solução requerida:** backup PostgreSQL automatizado, criptografado, com retenção e restore periódico em ambiente isolado.
- **Teste de saída:** evidência datada de restore e validação de contagens, chaves e entrevistas de amostra não reais.

### P0-07 — Configuração de produção permite defaults inseguros

- **Problema:** credenciais de banco previsíveis têm fallback no `application.yml`; não há perfil `prod` fail-fast, TLS ou política de headers.
- **Risco:** deploy acidental com credenciais conhecidas e tráfego sem proteção adequada.
- **Solução requerida:** perfil de produção sem defaults de secrets, HTTPS no ingress/load balancer, CORS explícito e headers de segurança.
- **Teste de saída:** aplicação `prod` falha ao iniciar sem secrets e smoke test confirma HTTPS/headers/origem autorizada.

### P0-08 — Governança de dados de candidatos ausente

- **Problema:** nome, anotações, avaliações, feedback e parecer são potencialmente sensíveis, sem política técnica de acesso, retenção ou exclusão.
- **Risco:** exposição indevida e impossibilidade de atender políticas corporativas/LGPD.
- **Solução requerida:** minimização, controle de acesso, retenção aprovada, exclusão governada, logs sem conteúdo e tratamento definido para backups.
- **Teste de saída:** política aprovada e testes de autorização/retention job sem uso de dados reais.

## P1 — Deve ser resolvido antes ou imediatamente após o go-live

1. **Observabilidade:** logs estruturados, correlation ID, eventos de login/autosave/finalização/PDF e métricas HTTP/JVM/Hikari; nunca registrar tokens ou conteúdo integral do candidato.
2. **Concorrência:** optimistic locking para vaga e entrevista; duas abas não podem sobrescrever silenciosamente.
3. **Idempotência:** finalizar entrevista e operações críticas devem tolerar clique duplo/repetição segura.
4. **Testes reais:** integração com PostgreSQL/Flyway via Testcontainers e E2E do fluxo crítico, sessão persistente e falha de autosave.
5. **Tratamento de erros:** Problem Details consistente, sem stack trace/SQL; mensagens compreensíveis no Angular e retry não destrutivo.
6. **Rate limiting:** login, cadastro e refresh com limite/backoff; política de brute force.
7. **Performance:** paginação de entrevistas, vagas e logs; revisão de queries/índices quando os repositories existirem.
8. **PDF:** testes com textos extensos, Unicode, quebra de páginas e volume; falha não pode ser silenciosa.
9. **Deploy:** probes de readiness/liveness, graceful shutdown, timeouts e limites coerentes de pool/payload.
10. **Containers:** Nginx non-root, imagens por versão imutável/digest, healthchecks e scan de imagem.
11. **Acessibilidade:** auditoria de foco, labels, teclado, contraste e leitores de tela.
12. **Dependências backend:** scan CVE automatizado ainda não verificado; não basta compilar a árvore Maven.

## P2 — Melhoria importante

1. Dashboards de métricas/SLO e alertas de tendência.
2. Autorização por capacidades (`MANAGE_VACANCIES`, `RUN_INTERVIEWS`, `VIEW_HISTORY`).
3. Política formal de browsers suportados, inicialmente Chrome e Edge atuais.
4. Testes de carga e orçamento de performance para histórico e PDF.
5. Automação de limpeza de refresh tokens compatível com múltiplas instâncias.
6. Versionamento visível por SemVer e SHA no endpoint `info` e nos logs.
7. Runbooks de incidentes e exercícios periódicos de recuperação.

## Segurança

- Não foram encontrados arquivos `.env`, chaves privadas ou keystores versionados pelos padrões inspecionados.
- As credenciais `interview_app/interview_app` do Compose são aceitáveis apenas para desenvolvimento e não podem ser promovidas.
- Não há SQL dinâmico no pequeno domínio atual; SQL injection não é aplicável à baseline, não “aprovado”.
- XSS é mitigado parcialmente pela interpolação Angular; campos livres e futura renderização/PDF ainda exigem testes. Não há uso de HTML arbitrário observado.
- CSRF depende da futura estratégia: Bearer JWT não usa proteção baseada em cookie; refresh em cookie exigirá SameSite e análise CSRF.
- HTTPS e headers de segurança dependem da infraestrutura e não foram verificados.

## Banco, migrations e backup

- Produção deve usar PostgreSQL; H2 está corretamente com escopo de teste na baseline.
- `ddl-auto=validate` está configurado no arquivo principal, mas não há schema a validar.
- O Compose usa volume, não backup, e não representa arquitetura final de produção.
- Estratégia mínima recomendada: backup gerenciado ou `pg_dump` diário + PITR conforme RPO, criptografia em trânsito/repouso, retenção aprovada e cópia fora do domínio de falha.
- Restore deve ocorrer periodicamente em banco isolado, executar Flyway compatível e validar checksums, contagens, foreign keys e amostras sintéticas.
- RPO/RTO dependem do negócio e precisam ser aprovados antes do go-live.

## Evidências de testes e verificações

Executados na baseline auditada:

- `backend/mvnw -B verify`: **4 testes aprovados**.
- `npm ci`: instalação reproduzível concluída.
- `npm test -- --watch=false`: **8 testes aprovados** após incluir a proteção contra falso autosave.
- `npm run build`: aprovado, com alerta de budget CSS e dependências CommonJS do gerador PDF.
- `npm audit --omit=dev --json`: **0 vulnerabilidades conhecidas em 18 dependências de produção** na data da auditoria.
- Docker/Compose: **não verificado**, pois o runtime Docker não está instalado neste host.
- Scan CVE Maven: **não verificado**.
- PostgreSQL/Flyway real: **não verificado**.
- E2E, restore, carga, acessibilidade e browsers: **não verificados**.

Validação visual no navegador local:

- 1024×768: sem overflow horizontal;
- 600×800: layout estreito sem overflow horizontal;
- o ambiente de navegador disponível limitou a largura efetiva a 1280 px, portanto 1366×768, 1440×900 e 1920×1080 permanecem **não verificados nas dimensões exatas**;
- o aviso “Não salvo — somente nesta aba” foi confirmado durante uma entrevista sintética.

Os testes atuais validam score e renderização básica. Não existem testes de auth, persistência, snapshot, auditoria, autosave real, PostgreSQL, E2E ou falhas operacionais na baseline.

## Mitigações seguras aplicadas nesta auditoria

Estas mudanças reduzem risco imediato, mas **não encerram os P0 funcionais nem alteram o NO-GO**:

- removido o falso estado “Salvo”; a UI agora informa que o conteúdo não foi persistido;
- bancos H2 locais e renderizações temporárias de PDF foram incluídos no `.gitignore` sem apagar os arquivos existentes;
- criado profile `prod` que exige variáveis de banco, mantém `ddl-auto=validate` e Flyway habilitado;
- inicialização com profile `prod` e sem variáveis foi testada e falhou, como esperado;
- configurados graceful shutdown, health probes sem detalhes e versão via `APP_VERSION`;
- frontend container alterado para Nginx non-root e porta 8080;
- adicionados CSP, `nosniff`, Referrer Policy, anti-framing e Permissions Policy no Nginx;
- criado runbook de deploy, backup, restore, smoke test e rollback.

O Nginx/Compose alterado não pôde ser executado neste host por ausência de Docker e precisa ser validado no CI ou homologação antes do merge.

## Plano recomendado

1. Integrar e revisar as branches que implementam o monólito real; executar nova auditoria sobre uma única baseline candidata.
2. Implementar persistência e autosave honesto antes de qualquer uso real.
3. Implementar autenticação/sessão e autoria server-side, com testes completos.
4. Consolidar schema Flyway e validar do zero e sobre cópia anonimizada em PostgreSQL de homologação.
5. Definir privacidade, retenção, backup, restore e responsáveis operacionais.
6. Endurecer perfil de produção, secrets, TLS, CORS, headers, health e logs.
7. Adicionar integração PostgreSQL, E2E crítico e teste de falha do autosave no CI.
8. Implantar em homologação equivalente à produção, executar smoke/load/restore e somente então reavaliar GO/NO-GO.

## Critérios para go-live

Todos os itens abaixo são obrigatórios:

- Nenhum P0 aberto.
- Baseline única em `develop`, revisada e promovida por PR.
- Auth, sessão persistente, logout/revogação e autorização testados.
- Entrevistas recuperáveis após reload/reabertura e falhas claramente exibidas.
- Flyway validado em PostgreSQL semelhante ao produtivo.
- Snapshot de vaga e auditoria de autoria comprovados por integração.
- Backup automatizado e restore testado com evidência.
- Política de retenção/privacidade e acessos aprovada.
- HTTPS, secrets externos, CORS e headers validados.
- Health/readiness, logs, métricas mínimas e alertas operacionais.
- E2E crítico, testes de falha, builds e scans aprovados.
- Deploy em homologação, smoke test e rollback ensaiado.

## Checklist final

- [ ] PostgreSQL de produção criado com acesso mínimo
- [ ] Backup configurado e criptografado
- [ ] Restore testado em ambiente isolado
- [ ] RPO/RTO aprovados
- [ ] Secrets em secret manager; nenhum fallback produtivo
- [ ] JWT secret forte e rotação definida
- [ ] HTTPS obrigatório
- [ ] CORS restrito
- [ ] Headers de segurança validados
- [ ] Migrations Flyway validadas
- [ ] `ddl-auto=validate` em produção
- [ ] Autosave real com falha e retry visíveis
- [ ] Concorrência e idempotência testadas
- [ ] Auditoria de autoria testada
- [ ] Snapshot histórico testado
- [ ] Health, readiness e liveness
- [ ] Logs estruturados e correlation ID
- [ ] Métricas e alertas mínimos
- [ ] Testes unitários, integração PostgreSQL e E2E
- [ ] Dependências e imagens escaneadas
- [ ] Homologação equivalente à produção
- [ ] Smoke test aprovado sem candidato real
- [ ] Rollback de aplicação e compatibilidade de migration ensaiados
- [ ] Política de retenção/exclusão aprovada
- [ ] Branch protection em `develop` e `main`

## Conclusão GO / NO-GO

**NO-GO.** Os bloqueadores exatos são P0-01 a P0-08. A prioridade imediata não é otimização: é estabelecer persistência confiável, autosave verdadeiro, autenticação/autorização, schema/migrations, auditoria, backup/restore, configuração segura e governança dos dados de candidatos.
