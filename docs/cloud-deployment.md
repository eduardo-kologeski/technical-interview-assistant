# Deploy — Cloudflare Pages, Koyeb e Neon

Este caminho disponibiliza a aplicação atual para demonstração com dados sintéticos:
GitHub → Angular na Cloudflare Pages → API Spring Boot no Koyeb → PostgreSQL no Neon.
O navegador utiliza HTTPS para frontend e API; a conexão JDBC com Neon utiliza TLS.
Não é uma certificação de prontidão para dados reais: continuam válidas as pendências de
[production readiness](production-readiness.md), entrega de e-mails e limites do rate limiting por instância.

## Estado analisado e decisões

Base: `origin/develop`, commit `a5a4875`. Angular 22.1.x (npm, Node 24), Java 21,
Spring Boot 4.1.1 e Maven Wrapper. Repositório separado em `frontend/` e `backend/`.
As 31 migrations Flyway existentes são preservadas, com Hibernate `ddl-auto=validate`.
Existem configuração padrão local, profile `prod` e configuração exclusiva de testes PostgreSQL;
homologação é descrita operacionalmente, sem um profile Spring próprio.

Já existiam JWT Base64 com access/refresh tokens, BCrypt, autorização por usuário/tenant,
CORS configurável, Actuator, Docker multi-stage com usuário sem privilégios e Compose local.
O Angular usava `/api` via proxy do dev server ou Nginx. CI já compilava/testava ambas as
partes, executava E2E e construía/escaneava imagens. O workflow de release publica imagens
por tag/execução manual; não faz deploy desta feature branch.

Reutilizamos tudo isso. Acrescentamos configuração pública da origem da API no artefato
Cloudflare, fallback SPA e headers; validação antecipada de variáveis produtivas; suporte a
`PORT`; pool pequeno e timeouts para PostgreSQL remoto; healthcheck Docker com porta variável.
O segredo JWT local fixo foi substituído por chave efêmera quando não informado. Em `prod`
o segredo é obrigatório, Base64 de ao menos 32 bytes (recomendação: 64 bytes aleatórios).

Nenhum serviço, container, SDK ou provedor de IA é necessário para build, startup, health ou
persistência. Não configure variáveis `OLLAMA_*` neste deploy. A configuração `prod` deixa o
provedor existente sem URL/modelo. Código e planejamento histórico foram preservados;
funcionalidades antigas que solicitam geração não estarão disponíveis nesta demonstração.
Não adicionamos geração de texto nem alteramos regras de avaliação ou telas.

As alterações locais não commitadas do checkout original foram preservadas em outro diretório;
não fazem parte desta branch nem do PR.

## 1. Neon

1. Crie um projeto no plano **Free**, preferencialmente próximo da região Koyeb disponível.
2. Crie banco e usuário exclusivos para a demonstração. Não use dados reais.
3. Em **Connect**, obtenha host, banco, usuário e senha. Use inicialmente a conexão **direta**
   (host sem `-pooler`), pois o mesmo datasource executa Flyway e suas travas de sessão.
   O Hikari já limita este processo a cinco conexões. Não mude para transaction pooling sem
   validar a estratégia de migrations separadamente.
4. Converta a connection string para JDBC, sem usuário/senha embutidos:
   `jdbc:postgresql://<host-neon>:5432/<banco>?sslmode=require&connectTimeout=15&socketTimeout=60`.
   Os campos entre `<...>` devem ser substituídos pelos valores do painel. Não cole uma URI
   `postgresql://usuario:senha@...` em `DATABASE_URL`.
5. Guarde usuário e senha nos secrets do Koyeb. Preserve TLS (`sslmode=require`, ou
   `verify-full` com a cadeia de certificados configurada no cliente).
6. No primeiro startup, Flyway aplica migrations; nos seguintes, valida/aplica somente pendentes.
   Nunca edite migrations aplicadas. Antes de alterações futuras de schema, planeje backup/rollback.

Referências: [Java/JDBC](https://neon.com/docs/guides/java),
[connection pooling](https://neon.com/docs/connect/connection-pooling).

## 2. Koyeb

1. Conecte o repositório GitHub e selecione `develop` **após** a revisão e merge manual do PR.
   Desabilite autodeploy inicialmente; um deploy aprovado deve usar um commit com CI verde.
   Não selecione a feature branch como produção.
2. Crie um **Web Service**, builder **Dockerfile**, work directory `backend`, Dockerfile `Dockerfile`
   relativo a esse diretório. Não use o Compose na nuvem. A imagem final executa o JAR como usuário
   sem privilégios e não recebe secrets durante o build.
3. Selecione explicitamente a instância **Free**. Não aceite um tamanho pago como alternativa automática.
4. Configure as variáveis da tabela abaixo. Exponha a porta HTTP `8081`, rota `/`; use `PORT=8081`.
   Outra porta funciona se `PORT`, porta exposta e healthcheck do painel forem iguais.
   `EXPOSE 8081` é metadata; `PORT` controla o listener e o healthcheck da imagem.
5. Configure healthcheck **HTTP**, caminho `/actuator/health`, na porta exposta, com período de
   inicialização suficiente para JVM/Flyway e cold start do banco (comece com 180 segundos).
   HTTP 200/`UP` inclui disponibilidade do datasource. O endpoint é público e retorna status e nomes
   dos grupos de probes, sem detalhes dos componentes;
   `/actuator/info` e `/actuator/prometheus` continuam protegidos por autenticação.
6. Use o domínio HTTPS fornecido pelo Koyeb como `API_URL` na Cloudflare. TLS é terminado pelo
   provedor; o listener interno permanece HTTP. Não exponha PostgreSQL pelo Koyeb.
7. Valide memória e startup no tamanho Free. Sugestão inicial de `JAVA_TOOL_OPTIONS`:
   `-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -Xss512k`.
   Isso deixa espaço para memória nativa; não garante que toda carga caiba em 512 MB.

Referências: [monorepos](https://www.koyeb.com/docs/build-and-deploy/monorepo),
[health checks](https://www.koyeb.com/docs/run-and-scale/health-checks).

## 3. Cloudflare Pages

1. Em Workers & Pages, crie um projeto **Pages** conectado ao GitHub. Use `develop` como branch
   de produção somente depois do merge aprovado. Desabilite previews automáticos de feature branches
   ou isole-os com API/banco de homologação e origens CORS explícitas.
2. Root directory: `frontend`. Node: `24` (`NODE_VERSION=24`).
3. Build command: `npm ci && npm run build:cloudflare`.
4. Build output directory: `dist/frontend/browser`, relativo a `frontend`.
5. Defina `API_URL` como a **origem HTTPS real** do backend, sem `/api`, usuário, senha, query ou fragmento.
   O build falha se estiver ausente ou inválida. Não há URL fictícia de produção versionada.
6. O build gera `runtime-config.js` público com somente a origem da API, `_redirects` com
   `/* /index.html 200` e `_headers` com CSP restrita à origem configurada.
   O script é carregado antes do Angular; `/api/...` passa a usar a origem Koyeb.
   Nenhum secret de banco/JWT deve ser criado no ambiente de build da Cloudflare.
7. Copie a origem HTTPS real da página para `CORS_ALLOWED_ORIGINS` no Koyeb e reimplante a API.
   Como alternativa para o primeiro deploy, reserve antes o nome do projeto Pages e confirme sua origem.
   Não use `*` nem `https://*.pages.dev`. Para domínios adicionais, use lista separada por vírgulas,
   sem barra final. Atualize também o CORS ao trocar de domínio.
8. Abra uma rota Angular diretamente (por exemplo `/auth/login`) e atualize a página.
   Confirme no navegador que chamadas API vão para Koyeb e nunca recebem o `index.html` da SPA.

Mudar `API_URL` exige novo build/deploy Cloudflare. `npm run build` e `npm start` continuam
usando `/api` no mesmo host; o proxy local e Nginx permanecem intactos.
Não publique o resultado de `npm run build` sozinho na Cloudflare: use `build:cloudflare`.

Referências: [build](https://developers.cloudflare.com/pages/configuration/build-configuration/),
[SPA routing](https://developers.cloudflare.com/pages/configuration/serving-pages/).

## Variáveis

| Local | Variável | Obrigatória | Valor/contrato |
|---|---|---|---|
| Koyeb | `SPRING_PROFILES_ACTIVE` | Sim | `prod` |
| Koyeb | `DATABASE_URL` | Sim | JDBC PostgreSQL Neon com TLS, sem credenciais embutidas |
| Koyeb secret | `DATABASE_USER` | Sim | Usuário exclusivo do banco |
| Koyeb secret | `DATABASE_PASSWORD` | Sim | Senha exclusiva do banco |
| Koyeb secret | `JWT_SECRET` | Sim | Base64 de 64 bytes aleatórios; mínimo validado: 32 bytes |
| Koyeb | `CORS_ALLOWED_ORIGINS` | Sim | Origens HTTPS exatas Cloudflare, separadas por vírgula |
| Koyeb | `PORT` | Recomendado | `8081`; precede `SERVER_PORT` (fallback local `8081`) |
| Cloudflare | `API_URL` | Sim | Origem HTTPS real do Koyeb, pública |
| Cloudflare | `NODE_VERSION` | Recomendado | `24` |
| Koyeb | `DB_POOL_MAX_SIZE` / `DB_POOL_MIN_IDLE` | Não | `5` / `0` |
| Koyeb | `DB_CONNECTION_TIMEOUT_MS` / `DB_VALIDATION_TIMEOUT_MS` | Não | `30000` / `3000` |
| Koyeb | `DB_IDLE_TIMEOUT_MS` / `DB_MAX_LIFETIME_MS` | Não | `60000` / `600000` |
| Koyeb | `JAVA_TOOL_OPTIONS` | Não | Ajuste de JVM descrito acima; medir consumo |
| Koyeb | `APP_VERSION` / `GIT_SHA` | Não | Identificação do commit implantado; defaults `dev` / `local` |
| Koyeb | `DEPLOYMENT_MODE` | Não | `SAAS` por padrão |

Mantenha `IDENTITY_EXPOSE_DEVELOPMENT_LINK=false` e `INVITATION_EXPOSE_DEVELOPMENT_LINK=false`.
As demais opções JWT/rate limiting existentes estão em [deployment.md](deployment.md).

Para gerar uma chave sem fixá-la no código, execute localmente PowerShell e transfira o resultado
diretamente ao secret manager (não salve no histórico do repositório):

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(64))
```

## Ambiente local e CI

Compose: copie `.env.example` para `.env`, preencha senha e chave Base64 e execute
`docker compose up --build --wait`. Não ative `prod` no Compose de desenvolvimento.
Sem Docker: configure `DATABASE_PASSWORD` do PostgreSQL local no ambiente (e `DATABASE_URL` /
`DATABASE_USER` se diferentes dos defaults locais), entre em `backend` e execute
`./mvnw.cmd spring-boot:run` (ou `./mvnw` no Linux); em `frontend`, execute `npm ci` + `npm start`.
O Spring não carrega `.env` automaticamente; esse arquivo é usado pelo Compose.
Sem `JWT_SECRET`, a chave local é efêmera e access tokens
anteriores deixam de validar após reiniciar. Para sessões persistentes, configure uma chave local.

O workflow CI existente continua verificando backend, frontend, E2E, migrations, imagens e scans;
agora valida também o artefato Cloudflare e os casos de configuração inválida. A origem
`https://api.example.test` usada pelo CI é apenas fixture de teste, nunca endereço de deploy.
Nenhum workflow ou integração de produção deve publicar esta feature branch automaticamente.

## Validação desta alteração

- `npm ci`: concluído; `npm run build:cloudflare` com origem de teste: concluído.
- `npm run test:cloudflare`: 2 testes passaram.
- Playwright: os 4 cenários existentes passaram considerando as execuções. Na primeira rodada,
  3 passaram e o download de PDF falhou com `ENOSPC`; após liberar espaço, a repetição isolada
  desse cenário passou, incluindo download e validação do tamanho do PDF.
- Angular/Vitest: 80 testes passaram em 18 arquivos com um worker. A primeira execução paralela
  no Windows encerrou um worker com `SIGTERM`; a repetição serial não apresentou falhas.
  Configuração de runner usada: `{ test: { maxWorkers: 1, fileParallelism: false,
  testTimeout: 15000, hookTimeout: 15000 } }`, passada via `--runner-config` ao `ng test`.
- `mvnw.cmd -B verify`: 103 testes passaram, nenhum ignorado; PostgreSQL 17 e o teste das
  31 migrations via Testcontainers executaram. O JAR foi gerado com sucesso.
- Smoke com `prod`: startup, PostgreSQL, `PORT=18091`, health sem detalhes internos, preflight
  aceito/rejeitado, Actuator protegido, cadastro, access token e refresh passaram, sem provedor de IA.
  Startup sem `JWT_SECRET` falhou claramente antes de abrir o datasource.
- Compose local e pacote On-Premises: `docker compose config --quiet` passou.
- Docker: compilação/empacotamento dentro do estágio de build passou, mas a gravação da imagem
  final falhou: `metadata_v2.db: read-only file system`. O host Windows estava sem espaço em `C:`.
  A validação completa da imagem deve passar no job `container-build` do CI antes do deploy.
- Busca por chaves privadas/tokens conhecidos não encontrou credenciais reais versionadas;
  somente `.env.example` está versionado em cada pacote. Essa busca não substitui auditoria do histórico.

## Limites gratuitos e aceite

Consultado em 2026-10-03; confirme os limites no painel antes de provisionar:

- [Koyeb Free](https://www.koyeb.com/docs/reference/instances): 512 MB RAM, 0,1 vCPU, 2 GB SSD;
  uma demonstração pode ter startup lento, especialmente com migrations. Há scale-to-zero após
  uma hora sem tráfego. Não há garantia de caber na instância nem SLA de produção.
- [Neon Free](https://neon.com/docs/introduction/plans): quotas de armazenamento, compute e
  transferência, suspensão/cold start e retenção limitada de histórico/restore. Monitore o painel;
  não habilite upgrade pago automático. Pool pequeno não elimina limites do plano.
- [Cloudflare Pages](https://developers.cloudflare.com/pages/platform/limits/): quotas de builds
  e arquivos; builds disparados frequentemente podem consumir o plano gratuito.
- E-mail real de verificação/reset/convite continua dependendo da entrega já pendente no projeto.
  Não exponha links de desenvolvimento para contornar isso numa instalação pública.
- Provedores não foram provisionados por este PR. TLS Neon, DNS, cold start e memória no Koyeb
  precisam ser confirmados na implantação real. Infraestrutura preparada não equivale a site publicado.

Checklist manual após deploy:

- [ ] CI verde no commit escolhido; secrets apenas no Koyeb.
- [ ] Flyway validado, health HTTP 200 sem detalhes internos dos componentes.
- [ ] HTTPS frontend/backend, sem mixed content.
- [ ] Preflight aceita origem Pages e rejeita origem não autorizada.
- [ ] Cadastro/login, refresh e persistência após recarregar funcionam com dados sintéticos.
- [ ] Refresh e acesso direto em rota Angular funcionam.
- [ ] Aplicação inicia e persiste sem qualquer serviço de IA.
- [ ] Memória/latência e quotas gratuitas verificadas; nenhuma opção paga habilitada.
- [ ] Backup/rollback definido antes de dados persistentes importantes.
