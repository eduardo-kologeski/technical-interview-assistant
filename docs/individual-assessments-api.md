# Avaliações pessoais, coleta estruturada e contrato de geração

A API exige a sessão JWT existente. O proprietário é o usuário autenticado; `X-Company-Id` não participa dessa autorização. Cadastro e navegação ainda mantêm o comportamento anterior até a IND-02. Usuários sem membership também podem usar esta API.

## Contrato

- `POST /api/assessments` → 201. Corpo: `{"title":"Avaliação Java","personName":"Pessoa avaliada","context":"Avaliação para desenvolvimento profissional"}`. Título e nome são obrigatórios, até 160 caracteres; contexto opcional, até 5000.
- `GET /api/assessments?page=0&size=20` → página própria, ordenada por atualização e ID; size entre 1 e 100.
- `GET /api/assessments/{id}` → avaliação própria; ID inexistente, alheio ou organizacional retorna 404.

Resposta: `id`, `title`, `personName`, `context`, `status`, `version`, `createdAt`, `updatedAt`. A criação começa em `CREATED`.

## Coleta estruturada

- `GET /api/assessments/{id}/collection` consulta o documento próprio;
- `PUT /api/assessments/{id}/collection` salva primeiro a preparação e depois os registros, sempre com controle otimista;
- `PATCH /api/assessments/{id}/collection/start` inicia a coleta somente depois de existir ao menos um requisito salvo;
- `PATCH /api/assessments/{id}/collection/complete` encerra a coleta.

Os comandos de início e conclusão recebem `{"version":0}`. O salvamento recebe `version` e `document`. O documento `schemaVersion: 1` contém listas de `criteria`, `evidence`, `notes`, `strengths` e `concerns`. Cada item possui UUID estável; toda evidência deve referenciar um critério existente. Pontuação é opcional e, quando informada, fica entre 0 e 5. Notas gerais não exigem critério; pontos positivos e de atenção podem referenciá-lo opcionalmente.

IDs duplicados, referências inexistentes, schema desconhecido e limites inválidos retornam 400. Versão desatualizada retorna 409 sem sobrescrever o documento atual. Em `CREATED`, o documento aceita somente requisitos sem pontuação ou registros; o início exige ao menos um requisito. Em `IN_PROGRESS`, as definições dos requisitos ficam bloqueadas, enquanto pontuação, evidências, notas e apontamentos podem ser salvos. Depois de `COMPLETED`, todo o documento fica protegido. `generationEligible` só é verdadeiro quando a coleta foi encerrada e existe ao menos um critério com evidência textual. Esse indicador não inicia geração nem avalia a pessoa.

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
- IND-02 e IND-03 foram integradas posteriormente. Nenhuma integração com IA ou serviço Ollama faz parte deste contrato; isso permanece reservado à IND-05/IND-06.

### Evidência de execução — IND-03

- package Maven aprovado;
- 4 testes unitários do documento versionado aprovados;
- teste de integração ampliado para ciclo de coleta, isolamento, conflito sem perda, limites, schema, duplicidade e referências;
- a suíte CI executou integralmente os testes do backend com PostgreSQL isolado e aprovou a cobertura da IND-03.

### Evidência de execução — IND-04

- dashboard individual permite criar e retomar avaliações sem empresa, vaga ou template;
- a tela separa a preparação dos requisitos da avaliação e bloqueia suas definições depois do início;
- durante a avaliação, 17 frases rápidas preenchem a experiência observada; opções positivas também registram ponto forte e opções negativas registram ponto de atenção;
- o autosave serializa alterações, envia a versão esperada e mantém o documento na tela quando ocorre falha ou conflito;
- o encerramento aguarda a confirmação do salvamento e torna a coleta somente leitura;
- 70 testes do frontend e o build de produção foram aprovados;
- o E2E executado no Docker Compose existente confirmou criação, persistência após recarga, falha simulada, retry e conclusão.
- `npm run e2e:local-compose -- --grep "personal assessment"` reutiliza os três serviços locais, sem iniciar outro backend, frontend ou banco.

## Contrato interno de geração — IND-05

A IND-05 não expõe endpoint de geração nem conversa com qualquer provedor. O pacote `report` separa `ReportContextBuilder`, `ReportPromptBuilder`, `ReportGenerationService`, a porta `LlmClient` e o `ReportDraftValidator`. A aplicação só registrará uma implementação dessa porta na IND-06, com `OllamaLlmClient`.

O contexto versão 1 aceita apenas título e contexto da avaliação; critérios com nome, tecnologia e descrição; evidências; notas; pontos positivos e pontos de atenção. A pessoa avaliada, usuário, e-mail, empresa, IDs persistidos, nota numérica, pergunta e rubrica ficam fora. UUIDs são remapeados para referências locais como `criterion-1` e `evidence-1`.

O gate exige coleta encerrada, título, critério e ao menos uma evidência associada. Notas isoladas não tornam a avaliação elegível. O prompt `report-draft-v1` trata todo o contexto como dado não confiável e solicita exatamente cinco seções: contexto, conhecimentos observados, pontos positivos, pontos de atenção e síntese. A conclusão humana não faz parte do schema do modelo.

O validador limita o tamanho das seções, rejeita referências desconhecidas ou duplicadas e exige que sínteses factuais citem evidências ou declarem explicitamente insuficiência. Essa validação é estrutural e não substitui a revisão humana da fidelidade semântica.

### Catálogo e justificativa

| Campo autorizado | Obrigatório | Uso na minuta |
|---|---:|---|
| `assessment.title` | sim | identifica o tema técnico avaliado, sem identificar a pessoa |
| `assessment.context` | não | delimita o propósito informado pelo entrevistador |
| `criteria[].reference` | sim | relaciona dados dentro do snapshot sem expor UUID persistido |
| `criteria[].name` | sim | nomeia o conhecimento ou comportamento técnico observado |
| `criteria[].technology` | não | esclarece a tecnologia quando ela faz parte do critério |
| `criteria[].description` | não | registra o recorte esperado para o critério |
| `evidence[]` | ao menos uma | fornece o registro factual e sua ligação ao critério |
| `notes[]` | não | acrescenta observações gerais, sem substituir evidências |
| `strengths[]` | não | preserva pontos positivos explicitamente registrados pelo entrevistador |
| `concerns[]` | não | preserva pontos de atenção explicitamente registrados pelo entrevistador |

### Exemplos sintéticos

Entrada mínima válida depois do remapeamento interno:

```json
{
  "schemaVersion": 1,
  "assessment": { "title": "Fundamentos de Java", "context": null },
  "criteria": [
    { "reference": "criterion-1", "name": "Orientação a objetos", "technology": "Java", "description": null }
  ],
  "evidence": [
    { "reference": "evidence-1", "criterionReference": "criterion-1", "text": "Explicou encapsulamento com um exemplo." }
  ],
  "notes": [],
  "strengths": [],
  "concerns": []
}
```

Entrada incompleta: um documento encerrado com critério e nota geral, mas sem item em `evidence`, é preservado como avaliação e recusado para geração com a orientação “Registre pelo menos uma evidência vinculada a um critério”.

Entrada inválida: uma evidência cujo `criterionId` não existe em `criteria` é recusada no salvamento da coleta com HTTP 400. IDs duplicados, schema desconhecido, texto obrigatório em branco e pontuação fora de 0–5 também são inválidos e nunca chegam ao contexto de geração.

## Geração e persistência da minuta — IND-06

`POST /api/assessments/{id}/report/generate` recebe `{"version":3}` e exige `Idempotency-Key: <uuid>`. A versão deve ser a da coleta encerrada. Uma primeira geração válida retorna 201; repetir a mesma chave para a mesma avaliação retorna 200, o mesmo relatório e `replayed: true`. Reutilizar a chave em outra avaliação ou tentar criar uma segunda minuta com outra chave retorna 409.

A resposta contém `id`, `assessmentId`, `status: "DRAFT"`, `sourceVersion`, `promptVersion`, `model`, as cinco seções em `draft`, `createdAt` e `replayed`. Não contém snapshot do contexto, identidade da pessoa ou conclusão humana.

V22 cria `assessment_reports` com vínculo composto entre avaliação e proprietário, uma minuta por avaliação e unicidade da chave idempotente por usuário. O snapshot minimizado e a saída original validada são imutáveis nesta etapa. A chamada ao Ollama ocorre fora da transação; antes de persistir, o serviço revalida proprietário, existência e versão da avaliação.

Falhas controladas:

| Situação | HTTP | Persistência |
|---|---:|---|
| coleta incompleta ou sem evidência | 422 | avaliação preservada; nenhum relatório |
| configuração ausente, Ollama indisponível ou modelo ausente | 503 | avaliação preservada; nenhum relatório parcial |
| timeout | 504 | avaliação preservada; retry explícito permitido |
| resposta HTTP/schema inválida | 502 | avaliação preservada; nenhum conteúdo inválido salvo |
| versão desatualizada ou minuta existente | 409 | original existente permanece intacto |

O endpoint foi validado localmente sem Ollama configurado: retornou 503, manteve a coleta `COMPLETED` e sua evidência, não criou linha em `assessment_reports` e não registrou título/evidência nos logs. O smoke real com um modelo local permanece pendente.

## Caso acadêmico e ficha de avaliação

`PUT /api/assessments/{id}/study/manual` cria ou salva o parecer manual de controle para uma avaliação encerrada. `POST /api/assessments/{id}/study/manual/finalize` valida todas as seções, persiste a versão e o hash SHA-256 do contexto estruturado e torna o artefato imutável. Enquanto esse caso acadêmico existir sem finalização, a geração da minuta retorna 409. O parecer manual e sua conclusão humana não integram o snapshot nem o prompt.

Depois da geração da minuta original, `PUT /api/assessments/{id}/study/evaluation` salva a ficha humana usando a versão otimista do caso. A ficha contém exatamente as dimensões `PRESENCE`, `CORRESPONDENCE`, `SUPPORT`, `INSUFFICIENCY_HANDLING` e `DECISION_SEPARATION`; cada dimensão aceita nota de 0 a 2, justificativa, referências à entrada e referências ao trecho da minuta. Ela também registra contagens de informações esperadas ausentes, afirmações factuais analisadas, afirmações sem sustentação e seções alteradas.

`POST /api/assessments/{id}/study/evaluation/finalize` exige todas as dimensões completas, ao menos uma referência de cada tipo por dimensão e contagens coerentes. A finalização registra autor e horário e torna a ficha imutável. Todos os endpoints aplicam o proprietário da avaliação; a ficha não é enviada ao Ollama nem incluída em logs ou métricas de conteúdo.

`GET /api/assessment-studies/summary` consolida apenas fichas finalizadas do usuário autenticado. O resultado informa totais e distribuições 0–2 por dimensão, agrupados por versão de protocolo, prompt e modelo. `GET /api/assessment-studies/summary.csv` entrega os mesmos dados para download. Nenhum dos formatos inclui ID, título, pessoa avaliada, texto, justificativa ou referência; valores textuais exportados também são protegidos contra interpretação como fórmula de planilha.
