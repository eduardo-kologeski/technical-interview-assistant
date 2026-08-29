# Runbook — banco indisponível

1. Confirmar o alerta, horário, ambiente, versão/SHA e correlation IDs; não registrar conteúdo de entrevista.
2. Verificar health/readiness, pool Hikari, DNS/rede, certificado e status do PostgreSQL.
3. Interromper deploys e comunicar indisponibilidade. Não reiniciar repetidamente a aplicação.
4. Se houver failover gerenciado, seguir o procedimento do provedor e validar que o endpoint permaneceu o mesmo.
5. Após recuperação, confirmar Flyway, conexões, erros 5xx e um smoke com dados sintéticos.
6. Se houver suspeita de perda/corrupção, não gravar; acionar o runbook de backup/restore e aprovação de incidente.
7. Encerrar somente após métricas estáveis e registrar causa, impacto, duração e ações.
