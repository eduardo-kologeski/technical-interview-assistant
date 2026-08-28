# Assets de monitoramento

- `prometheus/technical-interview-rules.yml`: recording rules e alertas alinhados aos SLOs.
- `grafana/technical-interview-overview.json`: dashboard inicial de tráfego, erros, latência, pool e heap.

O CI valida as regras com `promtool` de uma imagem Prometheus fixada por digest e valida o JSON do dashboard. Antes da produção, carregue as regras no Prometheus da homologação, importe o dashboard e confirme os nomes das métricas expostas pela versão implantada. Configure o scrape autenticado de `/actuator/prometheus` com o job `technical-interview-assistant` em rede restrita.

Os limites são baseline. Calibre-os com o teste de carga e tráfego sintético, mas não aumente thresholds apenas para silenciar alertas. Direcione alertas críticos ao plantão e warnings ao canal operacional definido pela organização.
