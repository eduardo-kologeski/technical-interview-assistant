# Teste de carga das jornadas críticas

Execute somente em ambiente isolado com usuário e entrevista sintéticos:

```text
k6 run tests/load/critical-journeys.js \
  -e BASE_URL=https://homolog.example \
  -e LOAD_USER_EMAIL=<secret> \
  -e LOAD_USER_PASSWORD=<secret> \
  -e LOAD_INTERVIEW_ID=<uuid>
```

O cenário cobre login no setup, listagem de vagas, histórico paginado, leitura da entrevista, autosave concorrente e registro da geração de PDF. Os thresholds iniciais são menos de 1% de falhas HTTP, p95 global inferior a 1 segundo, histórico inferior a 750 ms e registro de PDF inferior a 1 segundo. Credenciais devem vir do secret manager/CI e nunca ser versionadas. O autosave aceita 409 porque a concorrência otimista é o comportamento seguro esperado quando VUs usam a mesma entrevista.

A geração visual do PDF ocorre no navegador e é coberta pelo orçamento do teste Angular de alto volume. O endpoint medido aqui registra a operação e sua auditoria no backend; ele não deve ser interpretado como benchmark de renderização do jsPDF.
