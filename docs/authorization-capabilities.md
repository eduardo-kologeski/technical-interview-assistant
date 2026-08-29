# Evolução de autorização por capacidades

O modelo atual autentica usuários e preserva autoria/auditoria, sem diferenciar permissões funcionais. Esta decisão é intencional até que os papéis do negócio sejam aprovados; não se deve inferir que todo usuário terá acesso irrestrito em produção.

## Modelo proposto

Capacidades independentes, atribuídas por política externa ou papel:

- `VACANCY_READ`, `VACANCY_WRITE`, `VACANCY_DEACTIVATE`;
- `INTERVIEW_READ`, `INTERVIEW_CONDUCT`, `INTERVIEW_REVIEW`;
- `REPORT_GENERATE`;
- `PROFILE_READ`, `PROFILE_WRITE`;
- `AUDIT_READ` para acesso operacional excepcional.

Aplicar autorização no backend por recurso e contexto, nunca apenas escondendo controles no frontend. Preservar criador, último editor e entrevistador real. Mudanças de capacidade precisam de auditoria, menor privilégio, revisão e testes positivos/negativos.

## Decisões pendentes

- papéis organizacionais e responsáveis por concessão/revogação;
- escopo por equipe, vaga e entrevista;
- acesso de suporte e prazo de expiração;
- tratamento de desligamento e mudança de função;
- retenção e acesso ao log de auditoria.

Até essas decisões, a aplicação não deve declarar isolamento multiempresa nem RBAC completo.
