param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile
)

$ErrorActionPreference = 'Stop'
if ($env:ALLOW_RESTORE -ne 'true') {
    throw 'Set ALLOW_RESTORE=true to acknowledge this restore operation.'
}
if ([string]::IsNullOrWhiteSpace($env:RESTORE_DATABASE_URL)) {
    throw 'RESTORE_DATABASE_URL must target an isolated, empty PostgreSQL database.'
}
$resolvedBackup = (Resolve-Path -LiteralPath $BackupFile).Path
$checksumFile = "$resolvedBackup.sha256"
if (-not (Test-Path -LiteralPath $checksumFile -PathType Leaf)) {
    throw "Checksum file not found: $checksumFile"
}
$checksumParts = ((Get-Content -LiteralPath $checksumFile -Raw).Trim() -split '\s+', 2)
if ($checksumParts.Count -lt 1 -or $checksumParts[0] -notmatch '^[A-Fa-f0-9]{64}$') {
    throw 'Checksum file is invalid.'
}
$actualChecksum = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedBackup).Hash
if (-not $actualChecksum.Equals($checksumParts[0], [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Backup checksum verification failed. Restore was not started.'
}
foreach ($tool in @('psql', 'pg_restore')) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "$tool was not found in PATH." }
}

$tableCount = & psql $env:RESTORE_DATABASE_URL --tuples-only --no-align --command "SELECT count(*) FROM pg_catalog.pg_tables WHERE schemaname NOT IN ('pg_catalog','information_schema');"
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect the restore target.' }
if ([int]($tableCount.Trim()) -ne 0) { throw 'Restore refused: the target database is not empty.' }

& pg_restore --exit-on-error --no-owner --no-acl --dbname=$env:RESTORE_DATABASE_URL $resolvedBackup
if ($LASTEXITCODE -ne 0) { throw "pg_restore failed with exit code $LASTEXITCODE." }
Write-Output 'Restore completed. Run Flyway and the documented integrity checks before use.'
