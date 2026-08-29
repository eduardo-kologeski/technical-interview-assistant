# Publicação temporária com Cloudflare Quick Tunnel

Este procedimento publica o frontend local temporariamente em uma URL HTTPS `*.trycloudflare.com`. Ele é destinado somente a testes e demonstrações. Não substitui um deployment de produção.

O Quick Tunnel encaminha apenas o frontend na porta `4200`. O Angular usa o proxy local para `/api`, o backend permanece em `127.0.0.1:8081` e o banco não deve ser exposto pelo túnel ou pelo roteador.

## Requisitos

- Windows, Java e Node.js;
- frontend e backend funcionando localmente;
- arquivo de variáveis local ignorado pelo Git;
- nenhuma regra de port forwarding no roteador.

Nunca publique `JWT_SECRET`, `DATABASE_PASSWORD`, tokens ou o conteúdo de arquivos `.env`.

## Instalar o cloudflared

Use o pacote oficial da Cloudflare disponível no WinGet:

```powershell
winget install --exact --id Cloudflare.cloudflared --source winget --scope user --accept-package-agreements --accept-source-agreements
```

Abra um novo PowerShell e confirme:

```powershell
cloudflared --version
```

## Validar localmente

Antes de publicar, confirme:

```powershell
Invoke-WebRequest -UseBasicParsing http://localhost:4200/
Invoke-RestMethod http://127.0.0.1:8081/actuator/health
```

O frontend deve retornar `200` e o health do backend deve indicar `UP`.

## Criar o Quick Tunnel

Com o frontend ativo, execute em outro PowerShell:

```powershell
cloudflared tunnel --url http://localhost:4200 --no-autoupdate
```

Copie somente a URL realmente exibida pelo processo, por exemplo:

```text
https://nome-aleatorio.trycloudflare.com
```

A URL muda quando um novo Quick Tunnel é criado.

## Permitir somente o hostname temporário no Angular

O servidor de desenvolvimento rejeita hostnames desconhecidos. Pare o frontend e reinicie-o definindo o hostname retornado pelo Cloudflare:

```powershell
$env:NG_CLI_ANALYTICS='false'
$env:__VITE_ADDITIONAL_SERVER_ALLOWED_HOSTS='nome-aleatorio.trycloudflare.com'
npm start -- --host 0.0.0.0 --port 4200
```

Não use uma lista irrestrita de hosts quando é possível informar o hostname exato.

## Autorizar a origem HTTPS no backend

Reinicie o backend incluindo a URL HTTPS exata em `CORS_ALLOWED_ORIGINS`, junto das origens locais necessárias:

```powershell
$env:CORS_ALLOWED_ORIGINS='http://localhost:4200,https://nome-aleatorio.trycloudflare.com'
```

Em seguida, inicie o backend usando o mesmo arquivo local de secrets e banco já utilizado pelo ambiente. Não grave a URL temporária nem os secrets em arquivo versionado.

Não use `*` para CORS e não desative o Spring Security.

## Testes mínimos

Verifique pela URL pública:

1. página inicial e rota `/auth/login`;
2. carregamento de `main.js` e `styles.css`;
3. refresh direto em uma rota Angular;
4. preflight CORS para `/api/auth/login`;
5. cadastro ou login com conta sintética;
6. consulta autenticada a `/api/users/me`;
7. criação e leitura de um registro sintético persistido no banco.

Faça também um teste manual em celular usando 4G/5G, fora do Wi-Fi do servidor.

## Encerrar a publicação

Pressione `Ctrl+C` no terminal do `cloudflared`. A URL temporária deixa de encaminhar tráfego imediatamente. Encerrar o frontend ou desligar/suspender o computador também torna o sistema indisponível.

## Limitações

- `ng serve` é um servidor de desenvolvimento;
- Quick Tunnels não possuem garantia de disponibilidade;
- a URL é temporária;
- não há domínio próprio;
- o computador precisa permanecer ligado e conectado;
- este fluxo não implementa backup, restart automático ou observabilidade de produção.

O próximo passo recomendado é usar a imagem imutável aprovada com Nginx, Spring Boot e PostgreSQL em uma composição persistente, além de um Named Tunnel gerenciado e secrets externos ao Git.
