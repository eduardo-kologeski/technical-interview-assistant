# Monólito modular

## Visão geral

```text
Angular
  -> API Spring Boot
       -> auth (cadastro, login, JWT e refresh)
       -> user (usuário local e /api/users/me)
       -> vacancy (vagas e requisitos)
       -> interview (entrevistas e avaliações)
       -> audit (ator autenticado)
  -> PostgreSQL
```

O AuthCore continua sendo um projeto independente, mas não é necessário para compilar, testar ou executar o Technical Interview Assistant.

## Sessões

- O access token dura 15 minutos por padrão e fica no `sessionStorage`.
- Com `rememberMe=false`, nenhum refresh token é emitido. Recarregar a mesma aba preserva a sessão enquanto o access token for válido; fechar o navegador encerra a persistência.
- Com `rememberMe=true`, um refresh token aleatório é entregue ao cliente, armazenado no `localStorage` e persistido no banco somente como SHA-256.
- Ao iniciar ou recarregar o Angular, o guard consulta `/api/users/me`; se necessário, rotaciona o refresh token em `/api/auth/refresh` e tenta novamente.
- O logout revoga o refresh token antes de limpar o estado local.

## Segurança

- Senhas são armazenadas com BCrypt.
- Refresh tokens são revogáveis, expiram e são rotacionados a cada uso.
- Todos os usuários autenticados possuem o mesmo acesso funcional; não há autorização por roles.
- O JWT usa `JWT_SECRET`, `JWT_ACCESS_EXPIRATION` e `JWT_REFRESH_EXPIRATION`.
- CORS aceita somente as origens configuradas no backend.

## Banco e dados existentes

Flyway `V4__add_internal_authentication.sql` adiciona `users` e `refresh_tokens` sem alterar ou excluir vagas e entrevistas. Os identificadores de auditoria permanecem textuais para compatibilidade com registros anteriores.

Usuários de desenvolvimento do AuthCore não são copiados automaticamente porque os projetos podem usar bancos distintos. Se houver necessidade real, os hashes BCrypt podem ser migrados por uma rotina administrativa específica, sem conhecer ou registrar senhas em texto puro.
