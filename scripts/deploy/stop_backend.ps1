$ErrorActionPreference = 'Stop'

# Docker-based deploy: the backend runs as container "otto-backend" (own process tree in
# dockerd, immune to the Actions runner's Windows Job Object — see start_backend.ps1).
# start_backend.ps1 calls this itself right after a successful build; run it by hand only
# to take the server down.
docker rm -f otto-backend 2>$null | Out-Null
Write-Host "removed otto-backend container (or none was running)"
