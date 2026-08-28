# Deployment e operação

Este runbook define o caminho mínimo para homologação e produção. O `docker-compose.yml` do repositório é destinado exclusivamente a desenvolvimento/homologação local e não substitui uma plataforma produtiva gerenciada.

## Ambientes

- **dev:** execução local; dados sintéticos; Compose permitido.
- **homolog:** PostgreSQL e topologia semelhantes à produção; sem candidatos reais.
- **prod:** secrets externos, HTTPS, backup/PITR, observabilidade, aprovação e rollback.

Produção deve iniciar com o profile Spring `prod`. Não usar H2, `ddl-auto=create`, `create-drop` ou Flyway desabilitado.

## Variáveis necessárias

| Variável | Obrigatória em produção | Observação |
|---|---:|---|
| `SPRING_PROFILES_ACTIVE=prod` | sim | Ativa validações de configuração produtiva |
| `DATABASE_URL` | sim | URL JDBC PostgreSQL com TLS conforme provedor |
| `DATABASE_USER` | sim | Usuário de aplicação com privilégio mínimo |
| `DATABASE_PASSWORD` | sim | Secret manager; nunca arquivo versionado |
| `SERVER_PORT` | não | Padrão interno da imagem: 8080 |
| `APP_VERSION` | sim | SemVer e/ou SHA imutável da imagem |
| `GIT_SHA` | sim | SHA completo do commit contido na imagem |
| `CORS_ALLOWED_ORIGINS` | sim | Lista de origens HTTPS explícitas, separadas por vírgula |
| `JWT_SECRET` | sim | Chave Base64 forte fornecida pelo secret manager |
| `JWT_ACCESS_EXPIRATION` | não | Validade curta do access token; padrão `15m` |
| `JWT_REFRESH_EXPIRATION` | não | Validade do refresh token; padrão `7d` |
| `JWT_REFRESH_CLEANUP_RETENTION` | não | Retenção após expiração/revogação; padrão `P30D` |
| `JWT_REFRESH_CLEANUP_INTERVAL` | não | Intervalo idempotente de limpeza; padrão `PT6H` |
| `AUTH_RATE_LIMIT_WINDOW` | não | Janela local dos limites; padrão `60s` |
| `AUTH_LOGIN_RATE_LIMIT` | não | Tentativas de login por origem/janela; padrão `10` |
| `AUTH_REGISTER_RATE_LIMIT` | não | Cadastros por origem/janela; padrão `5` |
| `AUTH_REFRESH_RATE_LIMIT` | não | Refreshes por origem/janela; padrão `30` |

Não reutilizar secrets entre ambientes. A aplicação com profile `prod` deve falhar na inicialização quando banco, origem CORS ou chave JWT não forem fornecidos.

O rate limiting atual é mantido na memória de cada instância. Ele é adequado para uma única instância e reduz abuso acidental, mas não fornece limite global em escala horizontal. Antes de executar múltiplas réplicas, aplicar limite compartilhado no gateway/ingress ou adotar armazenamento distribuído; não aumentar os valores apenas para ocultar respostas `429`.

## Build e artefatos

```text
backend:  ./mvnw -B verify
frontend: npm ci && npm test -- --watch=false && npm run build
```

O pipeline deve produzir imagens uma única vez, identificadas por SHA. A mesma imagem aprovada em homologação deve ser promovida para produção. Não reconstruir entre ambientes e não usar tags mutáveis como única referência.

As imagens-base dos Dockerfiles e o PostgreSQL do Compose são fixados por digest. Atualizações devem vir por PR revisada, reconstruir e escanear as imagens e atualizar o comentário/tag de referência junto do digest; nunca trocar apenas para uma tag flutuante.

`/actuator/info` expõe `info.app.version` e `info.app.git-sha` apenas a clientes autenticados. A release deve usar SemVer em `APP_VERSION` e o commit imutável em `GIT_SHA`; ambos precisam coincidir com o artefato promovido.

## Banco e migrations

1. Criar PostgreSQL gerenciado/operado com alta disponibilidade compatível com o RTO.
2. Criar usuário da aplicação com acesso apenas ao schema necessário.
3. Tirar backup pré-deploy para mudanças de schema relevantes.
4. Executar Flyway como job controlado ou durante startup de uma única instância.
5. Confirmar checksum e versão da migration.
6. Iniciar aplicação com `ddl-auto=validate`.
7. Nunca editar migration já aplicada; criar uma nova migration aditiva.

Migrations destrutivas exigem plano expand/contract: primeiro adicionar estrutura compatível, migrar/backfill, promover código, validar e somente em release posterior remover estrutura antiga.

## Backup

A política deve ser aprovada com RPO/RTO explícitos. Baseline recomendada até essa decisão:

- snapshot/backup gerenciado diário;
- PITR/WAL quando suportado;
- retenção diária de 30 dias e mensal conforme política corporativa;
- criptografia em trânsito e repouso;
- cópia em domínio de falha separado;
- acesso restrito e auditado;
- dados expirados também tratados na política de backup.

Exemplo manual de emergência, executado por operador autorizado e sem senha na linha de comando:

```text
pg_dump --format=custom --no-owner --file=<arquivo-seguro>.dump <database-url>
```

O arquivo deve ir imediatamente para storage criptografado com lifecycle e controle de acesso. O comando não substitui backup automatizado/PITR.

## Teste de restore

Executar ao menos trimestralmente e antes do primeiro go-live:

1. Provisionar PostgreSQL isolado, vazio e sem acesso externo.
2. Selecionar backup dentro da retenção.
3. Restaurar com `pg_restore` no banco isolado.
4. Iniciar a versão compatível da aplicação e validar Flyway.
5. Verificar checksums, contagens, foreign keys e amostras sintéticas conhecidas.
6. Executar smoke tests sem candidato real.
7. Registrar data, duração, versão do backup, resultado e responsável.
8. Destruir de forma segura o ambiente temporário.

Produção permanece `NO-GO` sem evidência de restore bem-sucedido.

## Deploy

```text
merge aprovado
→ CI e scans
→ imagem imutável
→ deploy em homologação
→ migrations + smoke/E2E
→ aprovação humana
→ backup pré-deploy quando aplicável
→ deploy gradual em produção
→ health/readiness + smoke + métricas
```

Durante deploy, usar graceful shutdown e remover a instância do balanceador antes de encerrá-la. Não enviar tráfego até readiness confirmar aplicação e banco.

## Health e smoke test

O health público deve revelar somente status, nunca detalhes de banco ou secrets.

```text
GET /actuator/health
```

Smoke mínimo, sempre com dados sintéticos identificados:

1. health/readiness;
2. login e restauração de sessão;
3. listar vagas;
4. criar entrevista sintética;
5. salvar uma avaliação e recarregar;
6. finalizar e gerar PDF;
7. remover/expirar os dados sintéticos conforme política.

Na baseline auditada somente o health genérico existe; os demais passos dependem dos P0 funcionais.

## Rollback

1. Manter a imagem anterior disponível por SHA.
2. Interromper promoção se health, erros ou latência violarem o limite definido.
3. Reimplantar a imagem anterior somente se ela for compatível com o schema atual.
4. Para migration incompatível, seguir o runbook específico; rollback de código não desfaz schema.
5. Restaurar banco apenas em incidente de dados confirmado e com aprovação, considerando perda entre backup e incidente.
6. Registrar incidente, impacto, decisão e validação pós-rollback.

Toda release com migration deve declarar explicitamente compatibilidade backward/forward e seu ponto seguro de rollback.

## Logs e privacidade

Registrar metadata operacional e IDs de correlação, não conteúdo de entrevistas. Nunca registrar senha, token, header `Authorization`, parecer integral ou anotações. Acesso e retenção de logs devem seguir a política de dados da empresa.

## Checklist de release

- [ ] P0 do assessment encerrados
- [ ] CI e scans aprovados
- [ ] Imagem identificada por SHA
- [ ] Variáveis/secrets validados
- [ ] Backup recente e restore previamente ensaiado
- [ ] Migration revisada e compatível com rollback
- [ ] Homologação e E2E aprovados
- [ ] Aprovação humana registrada
- [ ] Health/readiness verdes
- [ ] Smoke test concluído
- [ ] Métricas e logs sem anomalia
- [ ] Imagem anterior disponível

