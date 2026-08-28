# Observabilidade

## Endpoints

- `/actuator/health`: público, sem detalhes; usado por probes.
- `/actuator/health/liveness`: público por meio do grupo de health.
- `/actuator/health/readiness`: público por meio do grupo de health.
- `/actuator/info`: exige autenticação.
- `/actuator/prometheus`: exige autenticação e fornece métricas HTTP, JVM, processo e pool de conexões.

Não exponha todo o Actuator e não torne métricas públicas no ingress. O coletor deve usar uma identidade técnica e rede restrita.

## Correlation ID

O backend aceita `X-Correlation-ID` contendo de 1 a 64 caracteres alfanuméricos, ponto, sublinhado ou hífen. Valores ausentes ou inseguros são substituídos por UUID. O mesmo identificador é retornado no response e inserido no MDC durante a requisição.

O proxy deve encaminhar esse header. Ao investigar um erro, procure primeiro pelo correlation ID, não pelo conteúdo da entrevista.

## Eventos

Eventos atuais incluem cadastro, login bem-sucedido/falho, refresh, criação/edição/desativação de vaga, início/conclusão de entrevista, autosave confirmado/conflitante, finalização da avaliação, geração de PDF e limpeza de refresh tokens. Eles registram somente IDs técnicos e metadados necessários.

Nunca adicionar aos logs:

- senha ou hash de senha;
- JWT, refresh token ou header `Authorization`;
- nome do candidato;
- anotações, feedback ou parecer;
- documento JSON da avaliação.

## Alertas recomendados

Os limites precisam ser calibrados em homologação. Como ponto inicial de observação, criar painéis para taxa de respostas 5xx, latência p95/p99, falhas de autenticação, conflitos de autosave, saturação do Hikari e uso de heap. Esses valores não constituem SLO ou SLA.

Os artefatos iniciais versionados ficam em `monitoring/`: regras Prometheus para error budget, latência, disponibilidade, Hikari e heap, além de um dashboard Grafana. A instalação e o roteamento de alertas pertencem ao ambiente e precisam ser validados com `promtool` antes do go-live.
