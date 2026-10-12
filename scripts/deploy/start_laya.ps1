param(
    # the checkpoint folder on this PC (otto-judge-v4 from the deploy bundle, not in git)
    [string]$Ckpt = "D:\laya_deploy\otto-judge-v4"
)
$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
if (-not (Test-Path (Join-Path $Ckpt "model.safetensors"))) { throw "no checkpoint at $Ckpt" }

docker build -f (Join-Path $repoRoot "backend\Dockerfile.laya") -t otto-laya $repoRoot
if ($LASTEXITCODE -ne 0) { throw "docker build failed (exit $LASTEXITCODE)" }

if (-not (docker network ls --filter name=^otto$ --format '{{.Name}}')) {
    docker network create otto | Out-Null
}

# same as stop_backend.ps1: remove the old one, quiet if there was none
docker rm -f otto-laya 2>$null | Out-Null

# No -p: only containers on the `otto` network reach it (the backend at http://otto-laya:8100).
# Nothing on the host or the LAN can, so the child's words stay inside this machine.
docker run -d --name otto-laya --gpus all --restart unless-stopped `
    --network otto `
    -v "${Ckpt}:/ckpt:ro" `
    otto-laya
if ($LASTEXITCODE -ne 0) { throw "docker run failed (exit $LASTEXITCODE)" }

Write-Host "started otto-laya - check: docker exec otto-laya python -c ""import urllib.request;print(urllib.request.urlopen('http://127.0.0.1:8100/health').read().decode())"""
