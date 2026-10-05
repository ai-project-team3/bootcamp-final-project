# Append the running otto-backend's usage lines to C:\otto\logs\usage.log (#30 - 10-05).
# Called by start_backend.ps1 before the swap, and by the "Server usage log" workflow.
# Only `llm -` and `tts -` count lines with their timestamps - no text, no client addresses.
# Never fails the caller: a missing container just means there is nothing to keep.
$ErrorActionPreference = 'Continue'

$dir = "C:\otto\logs"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$out = Join-Path $dir "usage.log"

$exists = docker ps -a --filter name=^otto-backend$ --format '{{.Names}}'
if (-not $exists) { Write-Host "no otto-backend container - nothing to save"; return }

# docker writes the app's log to stderr; cmd joins the streams so PowerShell does not treat
# each stderr line as an error record. --timestamps gives each line its time (the app's own
# format has none). Lines already saved (an earlier run of the same container) are skipped.
$seen = @{}
if (Test-Path $out) { Get-Content $out -Encoding UTF8 | ForEach-Object { $seen[$_] = $true } }
$new = cmd /c "docker logs --timestamps otto-backend 2>&1" |
    Where-Object { $_ -match 'INFO: (llm|tts) ' -and -not $seen.ContainsKey($_) }
if ($new) { $new | Add-Content -Path $out -Encoding UTF8 }
Write-Host ("usage lines saved: {0} new -> {1}" -f @($new).Count, $out)
