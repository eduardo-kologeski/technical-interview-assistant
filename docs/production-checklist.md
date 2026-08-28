# Checklist de produção

## Governança e dados

- [ ] base legal, retenção, responsáveis e exclusão aprovados;
- [ ] matriz de acesso/capacidades aprovada;
- [ ] dados de homologação sintéticos ou anonimizados;
- [ ] backups criptografados e restore ensaiado dentro de RPO/RTO.

## Release

- [ ] PRs revisadas e checks obrigatórios verdes;
- [ ] imagem única identificada por SemVer e SHA;
- [ ] migrations revisadas, aditivas e compatíveis com rollback;
- [ ] secrets externos, CORS explícito e TLS válidos;
- [ ] proteção de `develop` e `main` verificada;
- [ ] homologação, E2E, browsers suportados e carga aprovados.

## Operação

- [ ] probes, dashboards, SLOs e alertas ativos;
- [ ] runbooks acessíveis e responsáveis de plantão definidos;
- [ ] smoke sintético aprovado após promoção;
- [ ] versão anterior disponível para rollback;
- [ ] logs sem conteúdo de entrevistas, credenciais ou tokens.

Qualquer P0 pendente mantém o resultado `NO-GO`.
