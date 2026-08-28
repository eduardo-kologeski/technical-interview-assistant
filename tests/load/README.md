# Teste de carga das jornadas críticas

Execute somente em ambiente isolado com usuário e entrevista sintéticos:

```text
k6 run tests/load/critical-journeys.js \
  -e BASE_URL=https://homolog.example \
  -e LOAD_USER_EMAIL=<secret> \
  -e LOAD_USER_PASSWORD=<secret> \
  -e LOAD_INTERVIEW_ID=<uuid>
```

O cenário cobre login no setup, listagem de vagas, leitura da entrevista e autosave concorrente. Os thresholds iniciais são menos de 1% de falhas HTTP e p95 inferior a 1 segundo. Credenciais devem vir do secret manager/CI e nunca ser versionadas. O autosave aceita 409 porque a concorrência otimista é o comportamento seguro esperado quando VUs usam a mesma entrevista.
