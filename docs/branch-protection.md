# Proteção de branches

Configuração recomendada para `develop` e `main` no GitHub:

- pull request obrigatório, sem push direto;
- ao menos uma aprovação de pessoa diferente do autor;
- invalidar aprovações após novos commits;
- exigir resolução de conversas;
- exigir branch atualizada quando a política do time permitir;
- exigir checks: backend, frontend, e2e, container-build, npm-audit e OSV;
- bloquear force push e exclusão;
- restringir bypass a responsáveis de incidente, com auditoria;
- não habilitar auto-merge para atualizações de dependência.

`main` recebe somente releases aprovadas a partir de `develop`. A configuração é controle externo e deve ser verificada no GitHub antes do go-live.
