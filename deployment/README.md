# Deployment On-Premises

Este pacote executa imagens versionadas sem exigir checkout ou acesso ao código-fonte. Ele não inclui
PostgreSQL: o banco é externo e permanece sob controle da empresa.

## Pré-requisitos

- Docker Engine com Compose v2;
- PostgreSQL suportado acessível pelos containers;
- credencial de leitura do registry privado;
- reverse proxy corporativo com DNS e TLS para a origem declarada em `PUBLIC_ORIGIN`.

## Instalação

1. Copie `.env.example` para um arquivo `.env` não versionado e preencha os valores.
2. Use uma versão SemVer publicada em `APP_VERSION`; não use `latest`.
3. Autentique a credencial individual/revogável fornecida ao cliente:

   ```text
   docker login ghcr.io
   ```

4. Valide e inicie:

   ```text
   docker compose config --quiet
   docker compose pull
   docker compose up -d --wait
   ```

5. Configure o reverse proxy para encaminhar a origem HTTPS para `127.0.0.1:8080`. O Nginx da imagem
   entrega Angular em `/` e encaminha `/api/*` ao backend na rede interna; a API não publica porta no host.
6. Acesse `https://seu-dominio/setup`, crie a organização e o primeiro administrador e então faça login.

## Segurança e operação

- Não exponha PostgreSQL nem a porta da API à internet.
- Não grave `.env`, senhas, JWT ou credenciais do registry no controle de versão.
- `/actuator/health` é usado internamente; detalhes de saúde permanecem ocultos.
- Faça backup verificável antes de qualquer upgrade. Consulte `OPERATIONS.md`, incluído neste pacote.

## Diagnóstico mínimo

```text
docker compose ps
docker compose logs --tail=200 backend
docker compose logs --tail=200 frontend
```

Logs não devem ser enviados a terceiros sem revisão de dados pessoais e secrets.
