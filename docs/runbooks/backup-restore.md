# Runbook — backup e restore

## Backup

1. Executar com identidade autorizada e destino criptografado fora do host da aplicação.
2. Gerar dump customizado, checksum SHA-256, versão do PostgreSQL e timestamp UTC.
3. Restringir acesso e aplicar retenção aprovada. Nunca armazenar senha no comando ou no repositório.

## Teste de restore

1. Provisionar PostgreSQL isolado e vazio na versão suportada.
2. Verificar checksum e restaurar com `pg_restore --exit-on-error --no-owner`.
3. Iniciar a mesma imagem por SHA, validar Flyway e executar smoke sintético.
4. Comparar contagens esperadas, constraints e amostras conhecidas; medir duração contra RTO.
5. Registrar operador, backup, RPO observado, duração, evidências e resultado.
6. Destruir com segurança o ambiente temporário.

Restore em produção exige declaração de incidente, aprovação e avaliação explícita da perda de dados entre o ponto restaurado e o incidente.
