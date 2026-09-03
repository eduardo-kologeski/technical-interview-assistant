# Deploy gratuito do TIA

## Arquitetura escolhida

```text
GitHub (main)
   |\
   | \-- Cloudflare Pages -- HTTPS --> Angular
   |                                  |
   |                                  | HTTPS / CORS
   \---- Render Free -----------------v
          Spring Boot --------------> Neon PostgreSQL Free
                                      JDBC + TLS
```

O computador do desenvolvedor não participa do runtime. O fluxo local com Docker continua suportado e é separado do deploy público.

## Serviços e custo

| Camada | Serviço | Custo recorrente obrigatório | Motivo |
|---|---|---:|---|
| Frontend | Cloudflare Pages | R$ 0 | Angular é estático; HTTPS, CDN, deploy por Git e domínio próprio |
| Backend | Render Free Web Service | R$ 0 | Executa Spring Boot/Docker e integra com GitHub |
| Banco | Neon Free | R$ 0 | PostgreSQL persistente, TLS e compute que escala a zero |
| DNS | `pages.dev` + `onrender.com` | R$ 0 | domínio próprio é opcional |

### Por que Neon e não PostgreSQL Free do Render

O banco gratuito do Render expira após 30 dias e, após o período de carência informado pelo provedor, pode ser apagado. Isso é incompatível com dados persistentes do TIA. Neon não possui esse prazo fixo no plano Free e separa armazenamento persistente de compute, que pode escalar a zero.

### Neon x Supabase

Ambos são PostgreSQL compatíveis. Para o TIA, Neon é preferido porque a aplicação usa somente JDBC/JPA/Flyway: não precisa de Auth, Storage, Realtime ou APIs adicionais do Supabase. Neon mantém a solução menor e permite usar uma connection string PostgreSQL padrão. Supabase continua sendo uma alternativa válida se suas políticas Free forem aceitáveis no momento do cadastro.

### Railway

Não é adotado como camada principal porque sua oferta atual é baseada em créditos/trial e planos pagos, não em um web service Spring Boot gratuito permanente equivalente ao Render Free.

## Limitações do free tier

- Render Free entra em idle após período sem tráfego e a primeira requisição pode sofrer cold start. Não é adequado para SLA de produção comercial.
- Render Free possui cota mensal de horas por workspace e recursos limitados; acompanhar o painel para não ultrapassar a política gratuita vigente.
- Neon Free escala compute a zero após inatividade; a primeira conexão pode ter latência adicional. Os dados permanecem no armazenamento do projeto enquanto a conta/projeto respeitar a política do plano.
- Cloudflare Pages possui limites de builds e arquivos por deploy; o bundle Angular do TIA está muito abaixo do cenário típico que justificaria sair do plano gratuito.
- Nenhum free tier deve ser tratado como backup. Para dados importantes, mantenha exportações/backup fora do provedor.

## Variáveis de ambiente

### Render

Obrigatórias:

```text
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=jdbc:postgresql://<host>/<database>?sslmode=require
DATABASE_USER=<user>
DATABASE_PASSWORD=<secret>
JWT_SECRET=<secret forte e exclusivo>
CORS_ALLOWED_ORIGINS=https://<projeto>.pages.dev
```

Opcionais já suportadas:

```text
DB_POOL_MAX_SIZE=5
DB_POOL_MIN_IDLE=0
DB_KEEPALIVE_TIME_MS=120000
JWT_ACCESS_EXPIRATION=15m
JWT_REFRESH_EXPIRATION=7d
```

`PORT` é fornecida pelo Render. O profile `prod` usa `${PORT}` e mantém `8081` como fallback fora do Render.

### Cloudflare Pages

```text
API_URL=https://<servico>.onrender.com
```

`API_URL` é pública (não é segredo). O build gera `config.js` com `${API_URL}/api`. Não coloque credenciais no frontend.

## Banco Neon

1. Crie um projeto Free no Neon.
2. Crie/identifique database e role para o TIA.
3. Copie host, database, user e password do painel.
4. No Render, configure `DATABASE_URL` no formato JDBC com `sslmode=require`; configure user/password separadamente.
5. O Spring Boot executará Flyway no startup. `ddl-auto=validate` impede Hibernate de alterar o schema implicitamente.
6. Para um único Render Free, mantenha pool pequeno. O profile usa máximo 5, mínimo 0 e keepalive configurável.

Não execute manualmente as migrations já versionadas em `backend/src/main/resources/db/migration`; Flyway é a fonte de verdade.

## Backend no Render

O repositório contém `render.yaml` para um Web Service Docker Free.

Configuração esperada:

```text
branch: main
rootDir: backend
Dockerfile: backend/Dockerfile
health check: /actuator/health
profile: prod
```

Passos:

1. Conecte o repositório GitHub ao Render.
2. Crie o serviço usando o Blueprint `render.yaml` ou replique a configuração manualmente.
3. Preencha `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` e `CORS_ALLOWED_ORIGINS`.
4. Use o `JWT_SECRET` gerado pelo Blueprint ou forneça um secret forte. Não reutilize o secret local.
5. Faça o primeiro deploy e confirme `/actuator/health`.
6. Anote a URL real `https://<servico>.onrender.com`; ela será usada como `API_URL` no Cloudflare Pages.

## Frontend no Cloudflare Pages

Configuração recomendada:

```text
Production branch: main
Root directory: frontend
Build command: npm run build:cloudflare
Build output directory: dist/frontend/browser
Environment variable: API_URL=https://<servico>.onrender.com
```

O arquivo `public/_redirects` aplica fallback para `index.html`, permitindo refresh direto em rotas Angular. HTTPS é fornecido pelo Pages.

O `public/config.js` vazio mantém desenvolvimento local compatível. No Pages, `scripts/build-cloudflare.mjs` valida `API_URL`, exige HTTPS e sobrescreve `dist/frontend/browser/config.js` depois do build.

## CORS

O backend já usa lista explícita em `app.cors.allowed-origins`. Em desenvolvimento o fallback é `http://localhost:4200`. No profile `prod`, `CORS_ALLOWED_ORIGINS` é obrigatório.

Exemplo para produção:

```text
CORS_ALLOWED_ORIGINS=https://<projeto>.pages.dev
```

Se adicionar domínio próprio no futuro, use lista separada por vírgulas:

```text
https://<projeto>.pages.dev,https://app.exemplo.com
```

Não use `*`. O TIA usa Bearer JWT no header e política de origem explícita.

## GitHub e deploy automático

O fluxo existente é preservado:

```text
feature/* -> develop -> Pull Request/checks -> main
                                      |
                                      v
                              deploy automático
                         Cloudflare Pages + Render
```

`.github/workflows/ci.yml` já executa Maven verify, testes/build Angular, E2E, builds Docker, scans e smoke test. Configure proteção da `main` para exigir os checks relevantes antes do merge. Cloudflare Pages e Render devem observar somente `main` para produção.

## Desenvolvimento local e demo antiga

Continue usando:

```powershell
cp .env.example .env
docker compose up --build --wait
```

O frontend local continua chamando `/api`; Nginx/Angular proxy encaminham para o backend local. O deploy gratuito não remove Docker, Compose nem scripts de backup/restore.

Se o script de Cloudflare Tunnel existir em outra branch ou voltar a ser incorporado, trate-o como fluxo `local/legacy`: útil para demos de código não publicado, mas não é mais o caminho principal para manter o TIA online.

## Segurança

- `.env` e `.env.*` permanecem ignorados, exceto `.env.example`.
- `application-prod.yml` não possui defaults para database, CORS ou JWT secret.
- Nunca coloque a connection string real, password ou JWT secret no GitHub ou Cloudflare Pages.
- `API_URL` é configuração pública do browser e deve conter somente a origem HTTPS do backend.
- Logs existentes não interpolam secrets.

## Migração futura

A arquitetura evita lock-in relevante:

- Angular pode migrar de Pages para qualquer host estático/CDN alterando apenas o build/runtime config.
- Spring Boot usa Docker e variáveis de ambiente; pode migrar do Render para container service/Kubernetes/VM sem refatorar domínio.
- Neon expõe PostgreSQL padrão; uma migração futura é dump/restore + troca de `DATABASE_*`.

## Checklist manual final

1. Criar o projeto Neon Free e obter credenciais.
2. Criar/conectar o Render Blueprint à branch `main` e preencher secrets.
3. Aguardar a URL real do Render e testar health.
4. Criar o projeto Cloudflare Pages, branch `main`, root `frontend`, build `npm run build:cloudflare`, output `dist/frontend/browser`.
5. Definir `API_URL` no Pages com a URL real do Render.
6. Definir `CORS_ALLOWED_ORIGINS` no Render com a URL real do Pages e redeployar se necessário.
7. Validar login, refresh, CRUD de vagas, entrevista, autosave e refresh direto de rotas.

URLs esperadas, sem inventar nomes:

```text
Frontend: https://<projeto>.pages.dev
Backend:  https://<servico>.onrender.com
Banco:    PostgreSQL externo Neon via TLS
```
