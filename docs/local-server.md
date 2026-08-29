# Servidor local no Windows

Este guia transforma o computador em um servidor de piloto acessível na rede local. O computador precisa permanecer ligado, sem suspensão, conectado à rede e com o Docker Desktop em execução.

Este procedimento não publica senhas no Git. O arquivo `.env` e os dados do banco são ignorados pelo `.gitignore` e devem permanecer somente no computador servidor.

## Preparação inicial

Pré-requisitos:

- Docker Desktop instalado e iniciado;
- portas TCP `4200` e `8081` disponíveis;
- PowerShell aberto na raiz do projeto.

Crie o arquivo local de configuração:

```powershell
Copy-Item .env.example .env
notepad .env
```

Preencha `DATABASE_PASSWORD` e `JWT_SECRET` com valores exclusivos. Não copie o conteúdo do `.env` para commits, issues, logs ou mensagens públicas.

Uma chave JWT Base64 forte pode ser gerada no PowerShell:

```powershell
$bytes = New-Object byte[] 64
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

## Ligar o servidor

Na raiz do projeto, execute:

```powershell
docker compose up -d --build --wait
```

Esse é o comando principal para iniciar PostgreSQL, backend e frontend. A opção `-d` mantém os serviços ativos sem depender de um terminal aberto.

Confira o estado:

```powershell
docker compose ps
docker compose logs --tail 100
```

Acesse neste computador:

```text
http://localhost:4200
```

Em outro aparelho conectado à mesma rede, descubra o IPv4 do servidor com:

```powershell
Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' }
```

Depois acesse `http://IP_DO_COMPUTADOR:4200`. O endereço pode mudar após reiniciar o roteador; uma reserva DHCP evita essa mudança.

## Liberar o Firewall do Windows

Execute uma única vez em um PowerShell aberto **como administrador**:

```powershell
New-NetFirewallRule -DisplayName "Technical Interview Assistant LAN" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 4200 -Profile Public,Private -RemoteAddress LocalSubnet
```

A regra aceita somente dispositivos da rede local. Não abra a porta `8081` no roteador; o frontend encaminha as chamadas para o backend dentro da composição.

## Desligar e reiniciar

Desligar os serviços sem apagar os dados:

```powershell
docker compose stop
```

Ligar novamente, depois da preparação inicial:

```powershell
docker compose up -d --wait
```

Reiniciar:

```powershell
docker compose restart
```

Remover os containers sem apagar o volume PostgreSQL:

```powershell
docker compose down
```

Não use `docker compose down -v` no uso normal: a opção `-v` remove o volume que contém o banco de dados.

## Inicialização após reiniciar o Windows

Configure o Docker Desktop para iniciar com o Windows. Os serviços do Compose possuem persistência de dados, mas o arquivo atual não define política de reinício automático. Até essa política ser revisada, execute após entrar no Windows:

```powershell
Set-Location "C:\Users\eduar\OneDrive\Documentos\ChatGPT\Technical interview assistant"
docker compose up -d --wait
```

## Segurança e limitações

- Não versione `.env`, senhas, tokens, backups ou arquivos do banco.
- Use dados sintéticos neste piloto local até os controles de produção estarem aprovados.
- O acesso por IP local não possui HTTPS e não deve ser exposto diretamente à internet.
- Não encaminhe as portas `4200` ou `8081` no roteador.
- Para acesso externo, use HTTPS com autenticação e um túnel ou proxy devidamente configurado.
- Se o computador desligar, suspender ou perder a conexão, o sistema ficará indisponível.
- Para disponibilidade 24/7 independente deste computador, implante as mesmas imagens imutáveis em VPS ou cloud.

Consulte também o [runbook de deployment](deployment.md) antes de qualquer uso em produção.
