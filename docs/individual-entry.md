# IND-02 — Cadastro e entrada individuais

## Comportamento entregue

O cadastro público comum cria somente `User` e, quando solicitado, refresh token. Não cria `Company`, `CompanyMembership`, subscription ou entitlement. Login, refresh, `/api/users/me` e `/api/assessments` funcionam sem membership.

A criação explícita por `POST /api/companies` continua sendo o ponto que cria Company, membership administrativa, subscription e entitlements. O cadastro com `?company=true`, usado em jornadas empresariais existentes, cria primeiro a identidade pessoal e depois chama esse endpoint; não há tenant fictício ou criação implícita no backend de autenticação.

`CompanyContext` continua obrigatório nas APIs empresariais. Um usuário sem memberships recebe 403; múltiplas memberships sem seleção continuam exigindo seleção. A API pessoal ignora contexto empresarial e o interceptor Angular não envia `X-Company-Id` para `/api/assessments`.

O guard considera a autenticação suficiente para entrar. O carregamento de empresas determina apenas o contexto funcional e uma falha nessa consulta não invalida a sessão. Sem empresa ativa, o shell mostra “Meu espaço” e oculta Empresa, Vagas e Entrevistas organizacionais. O dashboard pessoal lista as avaliações existentes, informa quando está vazio e permite iniciar a criação visual entregue pela IND-04.

Usuários com uma membership continuam entrando diretamente no dashboard organizacional. Com várias empresas, o seletor existente oferece “Meu espaço” e as empresas disponíveis, preparando uma futura troca explícita sem criar um novo deployment mode.

## Escopo e compatibilidade

Não houve migration: V21 já permite usuário e avaliação pessoal sem Company. Empresas, memberships, convites, subscriptions, entrevistas e candidatos existentes não foram migrados nem removidos. Setup On-Premises continua criando sua organização inicial e o cadastro público continua bloqueado nesse modo.

Critérios/evidências e o ciclo de coleta foram adicionados na IND-03. Jornada visual completa, parecer, Ollama, LLM, PDF e billing pessoal não fazem parte da IND-02.

## Validação

- Backend: cadastro sem estruturas empresariais; login/refresh/me; criação/listagem/consulta pessoal após cadastro; bloqueio empresarial sem membership; fluxo explícito de criação de Company; isolamento A/B; setup e signup On-Premises.
- Frontend: sessão válida mesmo se o carregamento opcional de empresas falhar; shell pessoal sem links empresariais; dashboard vazio pela API pessoal; ausência de `X-Company-Id` na API pessoal.
- E2E organizacional: jornadas que necessitam de empresa agora usam cadastro empresarial explícito e informam seu nome.

Resultado local em 06/09/2026:

- testes backend focados: 17 executados, zero falhas/erros/ignorados;
- `mvn verify`: a primeira execução completou 73 testes com uma falha no intervalo padrão do dashboard durante a virada UTC; os outros 72 passaram. O teste foi estabilizado com intervalo UTC explícito. A repetição integral foi interrompida ao remover o container de teste por orientação do usuário, portanto não é registrada como execução verde;
- testes frontend: 65 executados em 14 arquivos, todos aprovados;
- build Angular e package Maven aprovados; imagens backend/frontend construídas;
- runtime nos serviços Docker existentes: cadastro com refresh, `/api/users/me`, criação/listagem pessoal e bloqueio 403 da API empresarial aprovados. Consultas diretas confirmaram zero Company, membership e subscription para a conta. A conta, refresh token e avaliação sintéticos foram removidos ao final;
- os budgets Angular existentes permanecem como warnings (bundle inicial, `app.scss` e `profile.component.ts`), sem falha de build.

Por orientação atual, as próximas validações devem usar os containers locais existentes. Testes que limpam tabelas nunca podem apontar para o banco de desenvolvimento `interview_assistant`; quando necessários, devem usar um banco de teste separado dentro da infraestrutura existente. O container temporário usado no início desta validação foi removido.
