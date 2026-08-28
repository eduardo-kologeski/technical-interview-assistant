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

Actions de terceiros e oficiais devem ser referenciadas por SHA completo, com comentário da versão correspondente. Atualizações passam por Dependabot/revisão e nunca trocam automaticamente um SHA apenas porque uma tag mutável foi movida.

`main` recebe somente releases aprovadas a partir de `develop`. A configuração é controle externo e deve ser verificada no GitHub antes do go-live.
