$ErrorActionPreference = 'Stop'

docker rm -f otto-monitor 2>$null | Out-Null
Write-Host "removed otto-monitor container (or none was running)"
