# Runbook — incidente de autenticação

1. Identificar aumento de 401/403/429/5xx, origem, janela e versão sem coletar senhas ou tokens.
2. Verificar relógio, chave ativa, expiração, rotação de refresh, rate limit e dependências.
3. Em suspeita de vazamento de JWT, revogar sessões persistidas e rotacionar o secret via secret manager; não publicar o valor.
4. Restringir origem/rede quando houver abuso e preservar evidências de auditoria.
5. Validar cadastro, login, refresh, logout e expiração com conta sintética.
6. Comunicar impacto e exigir nova autenticação quando a revogação afetar usuários.
