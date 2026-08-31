# Arquitetura e plano de entrega

## Uma base de código, dois modos de implantação

O backend Spring Boot e o frontend Angular são os mesmos em SaaS e On-Premises. `DEPLOYMENT_MODE`
aceita `SAAS` (default compatível com o produto existente) ou `ON_PREMISE`. O endpoint público
`/api/public/deployment` expõe apenas o modo e capabilities não sensíveis para a UI; autorizações
relevantes continuam sendo aplicadas no backend.

Em `SAAS`, cadastro público, multi-tenancy e billing têm default habilitado. Em `ON_PREMISE`,
os três defaults são desabilitados e a aplicação rejeita configurações que tentem habilitá-los. O
domínio `Company -> Membership -> User` e o isolamento tenant-scoped são preservados nos dois modos.

Uma instalação On-Premises nova expõe `/setup` até concluir o bootstrap transacional. O fluxo
configura a Company placeholder criada pela migration de compatibilidade, cria exatamente um administrador
com senha BCrypt e marca o singleton `installation_state` como concluído sob lock pessimista. Instalações
atualizadas que já possuam usuários são migradas como configuradas e não reabrem o primeiro acesso.

## 1. Escopo e decisões

O roteiro visual fornecido foi usado como referência para identificar dados úteis no parecer: identificação da entrevista, contexto técnico, competências, prós, contras, feedback e recomendação. O produto não replica seções nem regras específicas desse documento; todos esses conceitos são configuráveis.

O MVP será um monólito modular com frontend Angular e API Spring Boot. Essa arquitetura reduz custo operacional e mantém separação suficiente para evolução. O backend é a fonte de verdade; o frontend mantém um rascunho local para resiliência de autosave.

```text
Angular SPA
  | REST/JSON
Spring Boot modular monolith
  |-- identity (preparado para autenticação)
  |-- templates
  |-- interviews
  |-- assessments
  |-- documents
  `-- reporting
  |
PostgreSQL + Flyway
```

Decisões principais:

- IDs UUID evitam enumeração e simplificam futura sincronização.
- Templates são copiados para um snapshot da entrevista; mudanças posteriores não alteram avaliações históricas.
- Valores monetários não existem no domínio; notas usam inteiros de 0 a 5 e médias usam `BigDecimal`.
- Avaliações `NOT_EVALUATED` não entram nas médias.
- Parecer automático é determinístico e rastreável no MVP, sem inventar informações.
- PDF é gerado no backend a partir de um modelo de relatório estável.
- Exclusão de entrevistas será lógica para preservar auditoria.

## 2. Modelo de domínio

### Agregados

**InterviewTemplate**

- `id`, `name`, `description`, `track`, `active`, timestamps;
- possui `TemplateCategory` ordenadas;
- cada categoria possui `TemplateSkill` ordenadas;
- uma skill define `name`, `description`, `weight`, `importance`, `expectedScore` e perguntas sugeridas.

**Interview**

- `id`, dados do candidato, vaga, empresa, entrevistador, senioridade, datas, status e duração;
- guarda `templateId` e um snapshot das categorias/skills;
- possui avaliações, notas rápidas, pontos fortes, pontos de atenção, recomendação e parecer editável.

**QuestionBank**

- perguntas reutilizáveis com título, descrição, dificuldade, senioridade esperada e tags;
- relação N:N com skills de template.

**User / identidade autenticada**

- todos os usuários autenticados acessam as mesmas funcionalidades;
- não há autorização por papel para vagas, entrevistas ou pareceres;
- vagas e entrevistas registram autorias e datas para auditoria, nunca para definir propriedade.

### Relacionamentos

```text
InterviewTemplate 1--* TemplateCategory 1--* TemplateSkill *--* Question
Interview         1--* InterviewCategory 1--* SkillEvaluation
Interview         1--* InterviewNote
Interview         1--* Strength
Interview         1--* Concern
Interview         1--1 Assessment
```

`InterviewCategory` e `SkillEvaluation` formam o snapshot. Uma avaliação contém `score` anulável, `evaluationStatus`, comentário, peso, importância e nota esperada.

### Regras centrais

- somente o nome do candidato é obrigatório para iniciar;
- score aceita apenas 0..5 ou N/A;
- média simples considera avaliações pontuadas;
- média ponderada é `sum(score * weight) / sum(weight)`;
- média por categoria segue a mesma regra;
- ausência de avaliação resulta em média indisponível, nunca zero;
- recomendação começa como `NOT_DEFINED` e nunca é inferida pela média;
- finalizar exige confirmação, mas não exige avaliar todas as skills.

## 3. API inicial

```text
GET/POST        /api/templates
GET/PUT/DELETE  /api/templates/{id}
POST            /api/interviews
GET             /api/interviews
GET/PUT/DELETE  /api/interviews/{id}
PATCH           /api/interviews/{id}/status
PUT             /api/interviews/{id}/evaluations/{evaluationId}
POST            /api/interviews/{id}/notes
PUT             /api/interviews/{id}/assessment
GET             /api/interviews/{id}/summary
GET             /api/interviews/{id}/pdf
```

Escritas de autosave são idempotentes. Erros seguem Problem Details (RFC 9457), com validação por campo e correlation ID.

## 4. Telas

1. Login obrigatório, sem diferenciação funcional por perfil.
2. Dashboard enxuto.
3. Histórico de entrevistas.
4. Nova entrevista: escolha de template e cadastro mínimo.
5. Sessão de entrevista: cabeçalho, cronômetro, categorias, rating, comentário e notas rápidas.
6. Revisão: médias, comparação com esperado, forças, atenções e parecer editável.
7. Pré-visualização/exportação do PDF.
8. Templates: listagem e editor.
9. Banco de perguntas (V2).
10. Administração de usuários (V2).

## 5. Fluxo principal

O entrevistador escolhe um template, informa ao menos o candidato e inicia a sessão. Na tela de foco, navega pelas skills, atribui 0–5 ou N/A, registra comentários e notas rápidas. O sistema salva após cada alteração e atualiza as médias sem tomar uma decisão. Ao finalizar, o entrevistador revisa os dados, edita o parecer fundamentado, escolhe a recomendação e exporta o PDF. A entrevista permanece disponível no histórico.

## 6. Backlog

### MVP

1. Estrutura, banco, migrations e observabilidade básica.
2. Templates configuráveis e seeds DEV/QA.
3. Cadastro mínimo e criação da entrevista com snapshot.
4. Tela de sessão, rating 0–5/N/A e atalhos seguros.
5. Comentários, notas rápidas e autosave.
6. Médias simples, ponderadas e por categoria.
7. Pontos fortes e pontos de atenção.
8. Revisão, parecer determinístico editável e recomendação manual.
9. PDF A4.
10. Histórico com abrir, editar, duplicar, exportar e excluir logicamente.
11. Testes das regras críticas, Docker Compose e documentação operacional.

### V2

- JWT, usuários, papéis e auditoria completa;
- editor e banco reutilizável de perguntas;
- comparação visual com expectativas;
- logo/identidade visual configurável;
- dashboard por período e recomendações;
- acessibilidade e atalhos personalizáveis;
- recuperação e versionamento de rascunhos.

### V3

- organizações e isolamento multi-tenant;
- colaboração e revisão por mais de um entrevistador;
- integrações com ATS/calendário;
- analytics avançado com controles de privacidade;
- assistência de IA opcional, explícita e sempre revisável.

## 7. Estrutura proposta

```text
.
|-- backend/
|   |-- src/main/java/.../
|   |   |-- common/
|   |   |-- template/
|   |   |-- interview/
|   |   |-- assessment/
|   |   `-- document/
|   |-- src/main/resources/db/migration/
|   `-- src/test/
|-- frontend/
|   `-- src/app/
|       |-- core/
|       |-- shared/
|       `-- features/
|           |-- dashboard/
|           |-- interviews/
|           |-- session/
|           |-- assessment/
|           `-- templates/
|-- docs/
|-- docker-compose.yml
`-- README.md
```

## 8. Qualidade e segurança

- validações também no servidor;
- migrations somente aditivas depois de publicadas;
- nenhum segredo versionado;
- logs sem conteúdo sensível de entrevistas;
- testes unitários para cálculo/parecer e integração para persistência/API;
- testes de componentes e fluxos críticos no frontend;
- dependências fixadas por lockfiles e verificadas no CI.
