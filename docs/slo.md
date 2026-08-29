# Objetivos de nível de serviço

Estes objetivos são a baseline operacional inicial. Não constituem SLA contratual e devem ser recalibrados com tráfego de homologação e produção.

## Indicadores e objetivos

| Jornada | SLI | Objetivo mensal | Janela de alerta inicial |
|---|---|---:|---|
| API autenticada | respostas sem 5xx | 99,9% | consumo de 10% do error budget em 1h |
| Login | respostas válidas sem 5xx | 99,9% | 5xx > 1% por 10 min |
| Autosave | confirmações 2xx/409 sem 5xx | 99,95% | 5xx > 0,5% por 10 min |
| Leitura da entrevista | latência p95 | < 750 ms | p95 > 1 s por 15 min |
| Autosave | latência p95 | < 1 s | p95 > 1,5 s por 15 min |
| Geração de PDF | sucesso sem 5xx | 99,5% | 5xx > 2% por 15 min |

Disponibilidade mensal de 99,9% equivale a aproximadamente 43 minutos de indisponibilidade em 30 dias. Janelas de manutenção previamente comunicadas só podem ser excluídas após decisão formal.

## Medição

- Fonte: métricas Prometheus do Actuator, ingress e monitor sintético.
- Excluir apenas tráfego de probes; não excluir respostas 5xx por causa conhecida.
- Respostas 4xx de validação, autenticação e conflito não contam como indisponibilidade, mas devem ter painéis próprios.
- Segmentar por rota normalizada para evitar cardinalidade por ID.
- Reter agregados por período compatível com auditoria e política corporativa.

## Error budget e resposta

Ao consumir 50% do orçamento antes da metade do mês, congelar mudanças não essenciais e priorizar confiabilidade. Ao esgotar o orçamento, suspender releases funcionais até correção e validação. Incidentes devem registrar início, impacto, correlation IDs, versão/SHA, decisão e aprendizado, sem conteúdo de entrevistas.
