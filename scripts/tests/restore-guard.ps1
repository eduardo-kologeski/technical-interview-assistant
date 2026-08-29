$ErrorActionPreference = 'Stop'
$testDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("restore-guard-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $testDirectory | Out-Null
try {
    $backupFile = Join-Path $testDirectory 'corrupted.dump'
    Set-Content -LiteralPath $backupFile -Value 'corrupted-backup' -NoNewline
    Set-Content -LiteralPath "$backupFile.sha256" -Value (("0" * 64) + '  corrupted.dump')
    $env:ALLOW_RESTORE = 'true'
    $env:RESTORE_DATABASE_URL = 'postgresql://unused'

    $message = $null
    try {
        & (Join-Path $PSScriptRoot '..\restore-postgres.ps1') -BackupFile $backupFile
    } catch {
        $message = $_.Exception.Message
    }

    if ($message -ne 'Backup checksum verification failed. Restore was not started.') {
        throw "Unexpected restore result: $message"
    }
} finally {
    Remove-Item -LiteralPath $testDirectory -Recurse -Force
}
