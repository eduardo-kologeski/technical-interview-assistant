# Operação On-Premises

Este runbook é destinado ao administrador de infraestrutura do cliente. A empresa controla domínio, DNS,
TLS, reverse proxy, PostgreSQL, backups, rede e secret manager. O fornecedor entrega imagens imutáveis,
release manifest, migrations Flyway e notas de compatibilidade.

## Responsabilidades e objetivos

Antes do go-live, o cliente deve aprovar RPO, RTO, retenção e responsáveis por incidentes. Usar PostgreSQL
não cria backup automaticamente. A baseline recomendada, sujeita à política corporativa, é backup diário,
PITR/WAL quando disponível, retenção mínima de 30 dias, criptografia e cópia em domínio de falha separado.

## Preparação do PostgreSQL

1. Provisione banco e usuário exclusivos com privilégios mínimos no schema da aplicação.
2. Exija TLS e restrinja a rede aos hosts/containers da aplicação e à operação de backup.
3. Não publique a porta 5432 na internet.
4. Valide espaço, conexões, timezone UTC, HA e alertas coerentes com RPO/RTO.
5. Preencha `DATABASE_URL`, `DATABASE_USER` e `DATABASE_PASSWORD` no secret manager/arquivo `.env` local.

Flyway roda no startup da API e aplica migrations versionadas antes de o serviço ficar saudável. Nunca edite
uma migration já aplicada. Em implantação com múltiplas réplicas, execute a atualização de forma serializada.

## Instalação e primeiro acesso

1. Confirme os digests no `release-manifest-<versão>` fornecido com a release.
2. Copie `docker-compose.yml` e `.env.example` para um diretório protegido no host.
3. Crie `.env`, preencha secrets e restrinja a leitura ao usuário operacional.
4. Execute `docker login <registry>` com credencial somente leitura individual e revogável.
5. Execute `docker compose config --quiet`, `docker compose pull` e `docker compose up -d --wait`.
6. Configure DNS/TLS no reverse proxy e encaminhe a origem para `127.0.0.1:8080`.
7. Acesse `/setup`, crie a organização e o primeiro administrador. Uma segunda execução deve ser rejeitada.
8. Faça login e execute smoke sintético: listar/criar vaga, iniciar entrevista, salvar, recarregar e finalizar.

## Backup verificável

Execute backup com identidade autorizada, sem senha na linha de comando, e grave o resultado em storage
criptografado. Exemplo conceitual:

```text
pg_dump --format=custom --no-owner --file=<destino-seguro>.dump <database-url>
sha256sum <destino-seguro>.dump > <destino-seguro>.dump.sha256
```

Registre timestamp UTC, versão PostgreSQL, versão da aplicação, checksum, tamanho e operador. Monitore jobs
e retenção. Dados removidos da aplicação também precisam ser tratados na política de expiração dos backups.

## Ensaio de restore

Realize antes do go-live, trimestralmente e após mudanças importantes:

1. Provisione PostgreSQL isolado e vazio.
2. Valide o checksum antes de qualquer conexão ao destino.
3. Restaure com `pg_restore --exit-on-error --no-owner`.
4. Inicie a mesma versão da API; valide Flyway, constraints e health.
5. Compare contagens e amostras sintéticas conhecidas, execute smoke e meça o RTO observado.
6. Registre evidências e destrua o ambiente temporário com segurança.

Restore em produção exige incidente e aprovação explícitos, pois descarta alterações posteriores ao ponto
restaurado.

## Upgrade seguro

Para atualizar de `X.Y.Z` para `A.B.C`:

1. Leia o changelog e a declaração de compatibilidade das migrations.
2. Confirme imagem anterior e novos digests disponíveis no registry.
3. Execute backup e confirme checksum; não prossiga com backup/restore fora do RPO/RTO aprovado.
4. Altere somente `APP_VERSION` no `.env` para a versão publicada.
5. Execute `docker compose pull` e `docker compose up -d --wait`.
6. Verifique `docker compose ps`, health, logs sem secrets e o smoke sintético.
7. Registre versão, digests, migration aplicada, horário e resultado.

Não misture versões de frontend e backend: ambas usam o mesmo `APP_VERSION` e pertencem à mesma release.

## Rollback

Rollback de container só é seguro quando a versão anterior aceita o schema atual. Toda release com migration
deve classificar uma destas condições:

- **compatível:** reimplantar a imagem anterior por digest/versão e repetir health/smoke;
- **somente forward-fix:** manter o schema e publicar correção; não voltar a imagem;
- **restore necessário:** interromper, declarar incidente e restaurar backup aprovado, aceitando o RPO.

Nunca tente desfazer manualmente uma migration em produção sem runbook específico revisado. Se a condição não
estiver declarada no changelog, trate rollback como inseguro e escale ao suporte.

## Health, logs e suporte

- `docker compose ps` deve mostrar ambos os serviços saudáveis.
- A API não publica porta no host; o frontend verifica o backend pela rede interna.
- Não exponha Actuator administrativo nem detalhes de banco publicamente.
- Não envie logs contendo passwords, JWT, headers de autorização, credenciais ou dados pessoais desnecessários.
- Registre versão, horário, correlation ID e operação afetada ao abrir chamado.
