$ErrorActionPreference = 'Stop'

$conns = Get-NetTCPConnection -LocalPort 8010 -State Listen -ErrorAction SilentlyContinue
if (-not $conns) {
    Write-Host "no process on port 8010 - nothing to stop"
    exit 0
}

$targetPids = $conns | Select-Object -ExpandProperty OwningProcess -Unique
foreach ($targetPid in $targetPids) {
    Write-Host "stopping pid $targetPid (port 8010)"
    Stop-Process -Id $targetPid -Force -ErrorAction SilentlyContinue
}

Start-Sleep -Seconds 1
