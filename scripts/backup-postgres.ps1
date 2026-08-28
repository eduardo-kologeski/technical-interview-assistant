param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\backups')
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($env:DATABASE_URL)) {
    throw 'DATABASE_URL must contain the PostgreSQL connection URI.'
}
if (-not (Get-Command pg_dump -ErrorAction SilentlyContinue)) {
    throw 'pg_dump was not found in PATH.'
}

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $resolvedOutput | Out-Null
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupFile = Join-Path $resolvedOutput "interview-assistant-$timestamp.dump"

& pg_dump --format=custom --no-owner --no-acl --file=$backupFile $env:DATABASE_URL
if ($LASTEXITCODE -ne 0) { throw "pg_dump failed with exit code $LASTEXITCODE." }

$checksum = Get-FileHash -Algorithm SHA256 -LiteralPath $backupFile
$checksumLine = "$($checksum.Hash.ToLowerInvariant())  $([System.IO.Path]::GetFileName($backupFile))"
Set-Content -LiteralPath "$backupFile.sha256" -Value $checksumLine -Encoding ascii
Write-Output $backupFile
