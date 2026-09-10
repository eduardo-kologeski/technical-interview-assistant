[CmdletBinding()]
param(
    [string]$AccessToken = $env:TIA_SMOKE_ACCESS_TOKEN,
    [string]$ApiBaseUrl = 'http://localhost:8081',
    [string]$OllamaHostUrl = 'http://localhost:11434',
    [string]$ExpectedModel = $env:OLLAMA_MODEL,
    [string]$BackendService = 'backend'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($AccessToken)) {
    throw 'Defina TIA_SMOKE_ACCESS_TOKEN com um token local antes de executar o smoke.'
}
if ([string]::IsNullOrWhiteSpace($ExpectedModel)) {
    throw 'Defina OLLAMA_MODEL com o nome exato de um modelo já instalado. O script não baixa modelos.'
}

$authorization = if ($AccessToken.StartsWith('Bearer ', [StringComparison]::OrdinalIgnoreCase)) {
    $AccessToken
} else {
    "Bearer $AccessToken"
}
$apiHeaders = @{ Authorization = $authorization }
$assessmentId = $null
$startedAt = [DateTime]::UtcNow.ToString('o')
$marker = "SMOKE-OLLAMA-$([Guid]::NewGuid().ToString('N'))"

function Invoke-TiaApi {
    param(
        [Parameter(Mandatory)][ValidateSet('GET','POST','PUT','PATCH','DELETE')][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        [object]$Body,
        [Guid]$IdempotencyKey
    )

    $headers = @{} + $apiHeaders
    if ($IdempotencyKey -ne [Guid]::Empty) {
        $headers['Idempotency-Key'] = $IdempotencyKey.ToString()
    }
    $arguments = @{
        Method = $Method
        Uri = "$($ApiBaseUrl.TrimEnd('/'))$Path"
        Headers = $headers
        TimeoutSec = 120
    }
    if ($null -ne $Body) {
        $arguments.ContentType = 'application/json'
        $arguments.Body = $Body | ConvertTo-Json -Depth 12 -Compress
    }
    try {
        return Invoke-RestMethod @arguments
    }
    catch {
        $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        throw "A chamada $Method $Path falhou com status $status. Consulte o backend sem imprimir o payload sintético."
    }
}

try {
    $health = Invoke-RestMethod -Uri "$($ApiBaseUrl.TrimEnd('/'))/actuator/health" -TimeoutSec 10
    if ($health.status -ne 'UP') { throw 'O backend não está saudável.' }

    $tags = Invoke-RestMethod -Uri "$($OllamaHostUrl.TrimEnd('/'))/api/tags" -TimeoutSec 10
    if (@($tags.models.name) -notcontains $ExpectedModel) {
        throw "O modelo '$ExpectedModel' não está instalado no Ollama local. O script não fará download."
    }

    $containerModel = (& docker compose exec -T $BackendService printenv OLLAMA_MODEL).Trim()
    if ($LASTEXITCODE -ne 0 -or $containerModel -ne $ExpectedModel) {
        throw 'O backend containerizado não está configurado com o OLLAMA_MODEL esperado. Recrie somente o backend após ajustar o .env.'
    }
    $containerBaseUrl = (& docker compose exec -T $BackendService printenv OLLAMA_BASE_URL).Trim().TrimEnd('/')
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($containerBaseUrl)) {
        throw 'OLLAMA_BASE_URL não está configurada no backend containerizado.'
    }
    & docker compose exec -T $BackendService curl --fail --silent --output /dev/null "$containerBaseUrl/api/tags"
    if ($LASTEXITCODE -ne 0) { throw 'O backend containerizado não alcança o Ollama local.' }

    $created = Invoke-TiaApi POST '/api/assessments' @{
        title = "Avaliação sintética $marker"
        personName = 'Participante sintético'
        context = 'Caso exclusivo para validar a integração local, sem dados reais.'
    }
    $assessmentId = $created.id
    $criterionId = [Guid]::NewGuid()
    $evidenceId = [Guid]::NewGuid()

    Invoke-TiaApi PUT "/api/assessments/$assessmentId/collection" @{
        version = 0
        document = @{
            schemaVersion = 1
            criteria = @(@{ id = $criterionId; name = 'Encapsulamento em Java'; score = $null })
            evidence = @(); notes = @(); strengths = @(); concerns = @()
        }
    } | Out-Null
    Invoke-TiaApi PATCH "/api/assessments/$assessmentId/collection/start" @{ version = 1 } | Out-Null
    Invoke-TiaApi PUT "/api/assessments/$assessmentId/collection" @{
        version = 2
        document = @{
            schemaVersion = 1
            criteria = @(@{ id = $criterionId; name = 'Encapsulamento em Java'; score = 5 })
            evidence = @(@{ id = $evidenceId; criterionId = $criterionId; text = "Explicou encapsulamento com exemplo sintético $marker." })
            notes = @(); strengths = @(); concerns = @()
        }
    } | Out-Null
    Invoke-TiaApi PATCH "/api/assessments/$assessmentId/collection/complete" @{ version = 3 } | Out-Null

    $report = Invoke-TiaApi POST "/api/assessments/$assessmentId/report/generate" @{ version = 4 } ([Guid]::NewGuid())
    if ($report.status -ne 'DRAFT') { throw 'A geração não retornou uma minuta em estado DRAFT.' }
    if ($report.model -ne $ExpectedModel) { throw 'A minuta persistida não registra o modelo esperado.' }
    $sectionTexts = @($report.originalDraft.psobject.Properties.Value | ForEach-Object { $_.text })
    if ($sectionTexts.Count -ne 5 -or @($sectionTexts | Where-Object { [string]::IsNullOrWhiteSpace($_) }).Count -gt 0) {
        throw 'A resposta do Ollama não produziu as cinco seções válidas da minuta.'
    }

    $logs = (& docker compose logs --no-color --since $startedAt $BackendService) -join "`n"
    if ($logs.Contains($marker, [StringComparison]::Ordinal)) {
        throw 'O marcador sintético da avaliação apareceu nos logs do backend.'
    }
    foreach ($text in $sectionTexts) {
        if ($text.Length -ge 20 -and $logs.Contains($text, [StringComparison]::Ordinal)) {
            throw 'Uma seção completa da resposta do modelo apareceu nos logs do backend.'
        }
    }

    Write-Host "Smoke Ollama aprovado: minuta DRAFT persistida com o modelo '$ExpectedModel', cinco seções válidas e payload ausente dos logs." -ForegroundColor Green
}
finally {
    if ($assessmentId) {
        try { Invoke-TiaApi DELETE "/api/assessments/$assessmentId" | Out-Null }
        catch { Write-Warning 'Não foi possível excluir automaticamente o caso sintético do smoke.' }
    }
}
