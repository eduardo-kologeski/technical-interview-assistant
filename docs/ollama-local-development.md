# Ollama local para geração de minutas

Ollama é o único provedor de LLM do MVP. O Angular envia comandos apenas ao Spring Boot; o backend monta o contexto minimizado, constrói o prompt, chama o Ollama local, valida a resposta e persiste a minuta. Não existe fallback para OpenAI, Ollama Cloud ou outro serviço externo.

## Configuração

O Ollama continua fora do Docker Compose. No Docker Desktop, o backend containerizado alcança o host pelo nome `host.docker.internal`:

```dotenv
OLLAMA_BASE_URL=http://host.docker.internal:11434
OLLAMA_MODEL=<nome-exato-do-modelo-local-instalado>
OLLAMA_CONNECT_TIMEOUT=3s
OLLAMA_RESPONSE_TIMEOUT=60s
OLLAMA_MAX_INPUT_CHARS=120000
OLLAMA_MAX_RESPONSE_CHARS=100000
```

O nome do modelo não possui default. O adapter aceita somente HTTP para `localhost`, endereços de loopback ou `host.docker.internal` e recusa nomes explicitamente marcados como cloud. Também configure o processo Ollama com `OLLAMA_NO_CLOUD=1` e confirme nos logs do serviço que os recursos cloud estão desabilitados.

O Ollama usa `127.0.0.1:11434` por padrão. Se o Docker Desktop não alcançar esse binding, configure `OLLAMA_HOST=0.0.0.0:11434` no ambiente do usuário do Windows, reinicie o Ollama e limite o acesso no firewall à máquina local e às redes internas do Docker. Não publique a porta na internet.

Validação sem conteúdo de avaliação:

```powershell
ollama list
Invoke-RestMethod http://localhost:11434/api/tags
docker exec technicalinterviewassistant-backend-1 curl --fail http://host.docker.internal:11434/api/tags
```

## Impacto observado no host de desenvolvimento

Em 07/09/2026, o host usado para o desenvolvimento tinha aproximadamente 8 GB de RAM, GPU integrada com 1 GB reportado e 30 GB livres. O Ollama não estava instalado e a porta 11434 não estava acessível pelo host ou pelo backend. Por isso nenhum modelo foi baixado automaticamente e nenhum serviço Ollama foi acrescentado ao Compose.

Antes do smoke real, escolha um modelo local quantizado que caiba na memória disponível, registre nome, licença, tamanho do download, uso de RAM, tempo de primeira carga e latência. Use apenas dados sintéticos. Modelos maiores ou múltiplas gerações paralelas podem exceder esse hardware; a escolha precisa de medição, não de um nome hardcoded no repositório.

## Contrato e falhas

O endpoint `POST /api/assessments/{id}/report/generate` exige uma coleta encerrada, a versão atual e um header UUID `Idempotency-Key`. O resultado persistido tem status `DRAFT`, snapshot do contexto, versão da origem, versão do prompt e modelo utilizado. A conclusão humana não integra o prompt ou a resposta.

Indisponibilidade, timeout, modelo ausente, configuração externa e resposta inválida retornam erros controlados. Nenhuma dessas falhas altera a coleta ou cria uma minuta parcial. Logs operacionais contêm somente IDs técnicos, duração, versão do prompt e tipo do erro; o contexto, o prompt e a resposta completa não são registrados.

O request usa `stream: false` e o `format` JSON Schema aceito pela API `/api/generate`. Limites de entrada e saída são configuráveis; conteúdo acima do limite é recusado sem truncamento silencioso.
