# IND-01 — API de avaliações pessoais

A API exige a sessão JWT existente. O proprietário é o usuário autenticado; `X-Company-Id` não participa dessa autorização. Cadastro e navegação ainda mantêm o comportamento anterior até a IND-02. Usuários sem membership também podem usar esta API.

## Contrato

- `POST /api/assessments` → 201. Corpo: `{"title":"Avaliação Java","personName":"Pessoa avaliada","context":"Avaliação para desenvolvimento profissional"}`. Título e nome são obrigatórios, até 160 caracteres; contexto opcional, até 5000.
- `GET /api/assessments?page=0&size=20` → página própria, ordenada por atualização e ID; size entre 1 e 100.
- `GET /api/assessments/{id}` → avaliação própria; ID inexistente, alheio ou organizacional retorna 404.

Resposta: `id`, `title`, `personName`, `context`, `status`, `version`, `createdAt`, `updatedAt`. A criação começa em `CREATED`. Nenhum endpoint de coleta, IA, alteração ou aprovação é introduzido pela IND-01.

## Persistência e compatibilidade

V21 adiciona proprietário, título e contexto em `interviews`, com FK para `users` e escopo exclusivo proprietário/empresa. Registros pessoais não têm vaga ou candidato cadastrado; organizacionais continuam exigindo ambos e seus snapshots. Linhas legadas não mudam de proprietário e não são copiadas. Rotas organizacionais continuam filtrando empresa; rotas pessoais filtram proprietário.

Não alterar V1–V20 nem executar rollback destrutivo. Uma versão antiga do backend não conhece registros pessoais: rollback de aplicação após sua criação exige estratégia específica de preservação/isolamento, preferindo correção para frente.

## Validação

`PersonalAssessmentIntegrationTest` cobre usuário sem membership, separação A/B, header empresarial adulterado, listagem, validação, constraints e separação dos registros organizacionais mesmo para o autor. `PostgresqlFlywayMigrationTest` cobre banco vazio e upgrade de V20 para V21 preservando dados anteriores. Executar `mvn verify` com `TEST_DATABASE_URL`, `TEST_DATABASE_USER`, `TEST_DATABASE_PASSWORD` apontando exclusivamente para banco de testes descartável; a suíte limpa dados. Nunca apontar ao banco local de desenvolvimento.

Neste Docker Desktop, o Testcontainers 1.21.3 precisou de `DOCKER_HOST=npipe:////./pipe/docker_engine` e `JAVA_TOOL_OPTIONS=-Dapi.version=1.44` na execução de testes. Sem esse ajuste local, a detecção ignorou o teste de migração. Essas variáveis foram aplicadas somente ao processo de teste; não são mudanças globais ou configuração de produção.

### Evidência de execução — 06/09/2026

- `mvn verify`: 71 testes, zero falhas/erros; 70 executados e um teste de migração inicialmente ignorado pela detecção Docker.
- Reexecução com configuração corrigida e cobertura pessoal final: 5 testes, zero falhas/erros/ignorados (quatro pessoais + migração PostgreSQL 17.6). O teste de migração executou criação vazia e upgrade V20 → V21.
- Integrações da suíte usam PostgreSQL 18 em container descartável; container de testes removido ao concluir.
- Build da imagem backend concluído; V21 aplicada no PostgreSQL 18.6 local. Backup anterior em `backups/tia-before-ind01-20260906.dump`, com checksum SHA256 ao lado, ignorados pelo Git. Catálogo do dump validado com `pg_restore --list`; restauração completa não executada nesta etapa.
- Nenhuma integração de IA, alteração de frontend/cadastro ou serviço Ollama foi implementada. IND-02 e seguintes permanecem pendentes.
