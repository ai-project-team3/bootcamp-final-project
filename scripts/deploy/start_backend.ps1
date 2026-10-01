$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")

# Docker owns the process from here: a container started with `docker run -d` lives in
# dockerd's own process tree, not the Actions runner step's, so it survives the step/job
# ending without the WMI workaround the old Start-Process-based script needed.
docker build -f (Join-Path $repoRoot "backend\Dockerfile") -t otto-backend $repoRoot
if ($LASTEXITCODE -ne 0) { throw "docker build failed (exit $LASTEXITCODE)" }

$hfCache = Join-Path $env:USERPROFILE ".cache\huggingface"
New-Item -ItemType Directory -Force -Path $hfCache | Out-Null

docker run -d --name otto-backend --gpus all `
    --env-file C:\otto\.env `
    -v "${hfCache}:/root/.cache/huggingface" `
    -p 8010:8010 `
    otto-backend
if ($LASTEXITCODE -ne 0) { throw "docker run failed (exit $LASTEXITCODE)" }

Write-Host "started otto-backend container"
