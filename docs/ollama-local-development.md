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

Em 10/09/2026, o host usado para o desenvolvimento tinha aproximadamente 8 GB de RAM, GPU integrada Intel HD Graphics 620 com 1 GB reportado e 22,7 GB livres. O runtime Ollama 0.34.0 foi instalado no Windows e iniciado com `OLLAMA_NO_CLOUD=1`. A API em `localhost:11434` responde no host e também é alcançada pelo backend existente por `host.docker.internal`; o `.env` local configura essa URL.

Nenhum modelo está instalado ou configurado. O campo `OLLAMA_MODEL` permanece vazio, nenhum peso foi baixado automaticamente e nenhum serviço Ollama foi acrescentado ao Compose. O backend continua saudável e preserva o erro controlado de configuração enquanto o modelo não for escolhido.

Antes do smoke real, escolha um modelo local quantizado que caiba na memória disponível, registre nome, licença, tamanho do download, uso de RAM, tempo de primeira carga e latência. Use apenas dados sintéticos. Modelos maiores ou múltiplas gerações paralelas podem exceder esse hardware; a escolha precisa de medição, não de um nome hardcoded no repositório.

## Contrato e falhas

O endpoint `POST /api/assessments/{id}/report/generate` exige uma coleta encerrada, a versão atual e um header UUID `Idempotency-Key`. O resultado persistido tem status `DRAFT`, snapshot do contexto, versão da origem, versão do prompt e modelo utilizado. A conclusão humana não integra o prompt ou a resposta.

Indisponibilidade, timeout, modelo ausente, configuração externa e resposta inválida retornam erros controlados. Nenhuma dessas falhas altera a coleta ou cria uma minuta parcial. Logs operacionais contêm somente IDs técnicos, duração, versão do prompt e tipo do erro; o contexto, o prompt e a resposta completa não são registrados.

O request usa `stream: false` e o `format` JSON Schema aceito pela API `/api/generate`. Limites de entrada e saída são configuráveis; conteúdo acima do limite é recusado sem truncamento silencioso.

## Smoke sintético reprodutível

Depois de escolher e instalar explicitamente o modelo, recrie somente o backend com as variáveis acima. Obtenha um token de uma conta local, sem registrá-lo em arquivo, e execute:

```powershell
$env:TIA_SMOKE_ACCESS_TOKEN = '<token-local>'
$env:OLLAMA_MODEL = '<nome-exato-do-modelo-local-instalado>'
.\scripts\smoke-ollama.ps1
Remove-Item Env:TIA_SMOKE_ACCESS_TOKEN
```

O script não instala o Ollama nem baixa modelos. Ele confirma que host e backend containerizado alcançam o serviço, cria um caso exclusivamente sintético, gera e valida uma minuta `DRAFT` com cinco seções, verifica o modelo persistido, procura o marcador da entrada e as seções completas da resposta nos logs e remove a avaliação ao final. Uma falha encerra com mensagem sem imprimir token, avaliação, prompt ou resposta do modelo.
