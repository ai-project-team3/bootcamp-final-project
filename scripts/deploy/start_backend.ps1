$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")

# Docker owns the process from here: a container started with `docker run -d` lives in
# dockerd's own process tree, not the Actions runner step's, so it survives the step/job
# ending without the WMI workaround the old Start-Process-based script needed.
docker build -f (Join-Path $repoRoot "backend\Dockerfile") -t otto-backend $repoRoot
if ($LASTEXITCODE -ne 0) { throw "docker build failed (exit $LASTEXITCODE)" }

$hfCache = Join-Path $env:USERPROFILE ".cache\huggingface"
New-Item -ItemType Directory -Force -Path $hfCache | Out-Null

# Shared network so the monitor on port 80 can reach this container by name
# (monitoring/nginx.conf proxies /stats to http://otto-backend:8010). Checked rather than
# created-and-ignored: `docker network create` on an existing network writes to stderr, which
# Windows PowerShell turns into a terminating NativeCommandError under $ErrorActionPreference.
if (-not (docker network ls --filter name=^otto$ --format '{{.Name}}')) {
    docker network create otto | Out-Null
}

docker run -d --name otto-backend --gpus all `
    --network otto `
    --env-file C:\otto\.env `
    -v "${hfCache}:/root/.cache/huggingface" `
    -p 8000:8010 `
    otto-backend
if ($LASTEXITCODE -ne 0) { throw "docker run failed (exit $LASTEXITCODE)" }

Write-Host "started otto-backend container"
