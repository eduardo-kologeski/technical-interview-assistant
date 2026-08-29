# Runbook — falha de implantação

1. Pausar promoção e preservar logs, SHA da imagem, migrations aplicadas e horário.
2. Comparar health, 5xx, latência e smoke com a versão anterior.
3. Se o schema for compatível, reimplantar a imagem anterior por SHA.
4. Não reverter migration destrutivamente. Em incompatibilidade, aplicar correção aditiva ou seguir o procedimento aprovado de dados.
5. Validar login, vagas, entrevista, autosave, conclusão e PDF com dados sintéticos.
6. Comunicar resultado e registrar decisão, aprovador e evidência pós-rollback.
