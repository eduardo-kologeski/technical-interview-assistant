[CmdletBinding()]
param(
    [string]$DemoRepositoryPath,
    [ValidateRange(10, 1800)]
    [int]$StartupTimeoutSeconds = 180,
    [ValidateRange(10, 300)]
    [int]$TunnelTimeoutSeconds = 45
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($DemoRepositoryPath)) {
    $DemoRepositoryPath = Join-Path (Split-Path -Parent $projectRoot) 'tech-interview-assistant-demo'
}
$DemoRepositoryPath = [IO.Path]::GetFullPath($DemoRepositoryPath)
$localUrl = 'http://localhost:4200'
$pagesUrl = 'https://eduardo-kologeski.github.io/tech-interview-assistant-demo/'
$tunnelPattern = 'https://[a-z0-9-]+\.trycloudflare\.com'
$mutex = $null
$hasMutex = $false
$tunnelProcess = $null
$logDirectory = Join-Path ([IO.Path]::GetTempPath()) 'tech-interview-assistant-demo'
$stdoutLog = Join-Path $logDirectory 'cloudflared.stdout.log'
$stderrLog = Join-Path $logDirectory 'cloudflared.stderr.log'

function Assert-CommandAvailable {
    param([Parameter(Mandatory)][string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Comando '$Name' nao encontrado no PATH. Instale-o antes de iniciar a demonstracao."
    }
}

function Invoke-Git {
    param([Parameter(Mandatory)][string[]]$Arguments)

    & git -C $DemoRepositoryPath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Git falhou: git $($Arguments -join ' ')"
    }
}

function Wait-ForHttp {
    param(
        [Parameter(Mandatory)][string]$Url,
        [Parameter(Mandatory)][int]$TimeoutSeconds
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ([int]$response.StatusCode -ge 200 -and [int]$response.StatusCode -lt 500) {
                return
            }
        }
        catch {
            # O frontend ainda pode estar inicializando.
        }
        Start-Sleep -Seconds 2
    } while ([DateTime]::UtcNow -lt $deadline)

    throw "O frontend nao respondeu em $Url dentro de $TimeoutSeconds segundos."
}

try {
    $mutex = [Threading.Mutex]::new($false, 'Local\TechnicalInterviewAssistantQuickTunnel')
    $hasMutex = $mutex.WaitOne(0)
    if (-not $hasMutex) {
        throw 'Ja existe uma execucao de start-demo.ps1 ativa. Encerre-a com Ctrl+C antes de iniciar outra.'
    }

    Assert-CommandAvailable -Name 'docker'
    Assert-CommandAvailable -Name 'cloudflared'
    Assert-CommandAvailable -Name 'git'

    if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'docker-compose.yml') -PathType Leaf)) {
        throw "docker-compose.yml nao encontrado em $projectRoot."
    }
    if (-not (Test-Path -LiteralPath (Join-Path $DemoRepositoryPath '.git'))) {
        throw "Repositorio tech-interview-assistant-demo nao encontrado em '$DemoRepositoryPath'. Clone-o ou use -DemoRepositoryPath."
    }
    $indexPath = Join-Path $DemoRepositoryPath 'index.html'
    if (-not (Test-Path -LiteralPath $indexPath -PathType Leaf)) {
        throw "index.html nao encontrado em '$DemoRepositoryPath'."
    }

    Push-Location $projectRoot
    try {
        & docker info *> $null
        if ($LASTEXITCODE -ne 0) {
            throw 'Docker nao esta disponivel. Abra o Docker Desktop e tente novamente.'
        }

        & docker compose up -d
        if ($LASTEXITCODE -ne 0) {
            throw 'Falha ao iniciar os servicos com docker compose up -d.'
        }
    }
    finally {
        Pop-Location
    }

    Wait-ForHttp -Url $localUrl -TimeoutSeconds $StartupTimeoutSeconds
    Write-Host 'TIA local: OK' -ForegroundColor Green

    $existingTunnels = @(
        Get-CimInstance Win32_Process -Filter "Name = 'cloudflared.exe'" -ErrorAction SilentlyContinue |
            Where-Object {
                $_.CommandLine -match '\btunnel\b' -and
                $_.CommandLine -match '--url\s+["'']?http://localhost:4200'
            }
    )
    if ($existingTunnels.Count -gt 0) {
        throw 'Ja existe um cloudflared apontando para localhost:4200. Encerre esse processo antes de iniciar a demonstracao.'
    }

    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    Remove-Item -LiteralPath $stdoutLog, $stderrLog -Force -ErrorAction SilentlyContinue
    $tunnelProcess = Start-Process -FilePath (Get-Command cloudflared).Source `
        -ArgumentList @('tunnel', '--url', $localUrl, '--no-autoupdate') `
        -RedirectStandardOutput $stdoutLog `
        -RedirectStandardError $stderrLog `
        -WindowStyle Hidden `
        -PassThru

    $tunnelUrl = $null
    $deadline = [DateTime]::UtcNow.AddSeconds($TunnelTimeoutSeconds)
    do {
        if ($tunnelProcess.HasExited) {
            throw "cloudflared encerrou inesperadamente (codigo $($tunnelProcess.ExitCode)). Consulte '$stderrLog'."
        }

        $output = @(
            Get-Content -LiteralPath $stdoutLog -Raw -ErrorAction SilentlyContinue
            Get-Content -LiteralPath $stderrLog -Raw -ErrorAction SilentlyContinue
        ) -join "`n"
        $match = [regex]::Match($output, $tunnelPattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
        if ($match.Success) {
            $tunnelUrl = $match.Value.ToLowerInvariant()
            break
        }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)

    if (-not $tunnelUrl) {
        throw "Nao foi possivel capturar a URL do Quick Tunnel em $TunnelTimeoutSeconds segundos. Consulte '$stderrLog'."
    }
    if ($tunnelProcess.HasExited) {
        throw 'O Quick Tunnel foi criado, mas o processo cloudflared nao permaneceu ativo.'
    }

    Write-Host "`nTunnel criado:" -ForegroundColor Cyan
    Write-Host $tunnelUrl

    $branch = (& git -C $DemoRepositoryPath branch --show-current).Trim()
    if ($LASTEXITCODE -ne 0 -or $branch -ne 'main') {
        throw "O repositorio tech-interview-assistant-demo deve estar na branch main (branch atual: '$branch')."
    }

    $indexContent = Get-Content -LiteralPath $indexPath -Raw
    $urlMatches = [regex]::Matches($indexContent, $tunnelPattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if ($urlMatches.Count -lt 2) {
        throw 'index.html invalido: esperava encontrar a URL trycloudflare.com no meta refresh e no link fallback.'
    }
    $updatedContent = [regex]::Replace(
        $indexContent,
        $tunnelPattern,
        $tunnelUrl,
        [Text.RegularExpressions.RegexOptions]::IgnoreCase
    )

    if ($updatedContent -cne $indexContent) {
        [IO.File]::WriteAllText($indexPath, $updatedContent, [Text.UTF8Encoding]::new($false))
        Invoke-Git -Arguments @('add', '--', 'index.html')
        & git -C $DemoRepositoryPath diff --cached --quiet -- index.html
        if ($LASTEXITCODE -eq 1) {
            Invoke-Git -Arguments @('commit', '-m', 'chore: update demo URL')
            Invoke-Git -Arguments @('push', 'origin', 'main')
            Write-Host "`nGitHub Pages atualizado:" -ForegroundColor Green
            Write-Host $pagesUrl
        }
        elseif ($LASTEXITCODE -ne 0) {
            throw 'Nao foi possivel verificar as alteracoes preparadas no index.html.'
        }
    }
    else {
        Write-Host "`nA URL nao mudou; nenhum commit ou push foi necessario." -ForegroundColor Yellow
        Write-Host "GitHub Pages: $pagesUrl"
    }

    Write-Host "`nTunnel ativo. Pressione Ctrl+C para encerrar somente o cloudflared; os containers continuarao ativos." -ForegroundColor Cyan
    while (-not $tunnelProcess.HasExited) {
        Start-Sleep -Seconds 1
    }
    throw "cloudflared encerrou inesperadamente (codigo $($tunnelProcess.ExitCode))."
}
finally {
    if ($tunnelProcess -and -not $tunnelProcess.HasExited) {
        Stop-Process -Id $tunnelProcess.Id -ErrorAction SilentlyContinue
        $tunnelProcess.WaitForExit(5000) | Out-Null
    }
    if ($hasMutex -and $mutex) {
        $mutex.ReleaseMutex()
    }
    if ($mutex) {
        $mutex.Dispose()
    }
}
