# Technical Interview Assistant

Aplicação para conduzir entrevistas técnicas, registrar avaliações com rapidez e gerar um parecer estruturado e revisável.

## Arquitetura

O sistema é um monólito modular com um único backend Spring Boot e um único banco PostgreSQL:

```text
Angular -> Spring Boot -> PostgreSQL
```

O backend contém módulos internos para autenticação, usuários, vagas, entrevistas, avaliações e auditoria. O projeto não depende do AuthCore em runtime. Consulte [`docs/modular-monolith.md`](docs/modular-monolith.md).

### Execução

```powershell
docker compose up --build
```

Serviços: frontend em `http://localhost:4200`, API em `http://localhost:8081` e PostgreSQL interno ao Compose. Configure `JWT_SECRET` em ambientes reais; o valor padrão existe apenas para desenvolvimento local.

## Status

O projeto está em construção. O desenho do produto, a arquitetura, o domínio e o backlog estão em [`docs/architecture.md`](docs/architecture.md).

## Princípios

- experiência desktop-first e uso eficiente por teclado;
- templates configuráveis para diferentes perfis e stacks;
- decisão final sempre pertencente ao entrevistador;
- rastreabilidade: textos automáticos usam apenas dados registrados;
- autosave e proteção contra perda de dados durante a entrevista.

## Branches

- `main`: versão estável;
- `develop`: integração do desenvolvimento;
- `feature/*`: unidades de trabalho revisáveis.
