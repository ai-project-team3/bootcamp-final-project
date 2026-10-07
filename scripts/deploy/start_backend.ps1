$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")

# Build BEFORE touching the running container: the build takes minutes, and the old
# otto-backend keeps serving the phones the whole time. Retagging otto-backend does not
# affect a running container (it keeps the image it was started from). A failed build
# throws here, so the old container is never removed and the server stays up.
docker build -f (Join-Path $repoRoot "backend\Dockerfile") -t otto-backend $repoRoot
if ($LASTEXITCODE -ne 0) { throw "docker build failed (exit $LASTEXITCODE) - old container left running" }

$hfCache = Join-Path $env:USERPROFILE ".cache\huggingface"
New-Item -ItemType Directory -Force -Path $hfCache | Out-Null

# The daily spend cap (backend/app/limits.py) keeps the day's total in a file. Inside the container
# that file died with every deploy, so a redeploy reset the day to 0 won (10-06). Keep it on the host.
# Problem reports (#283) live there too (/state/reports), so a deploy never loses an unhandled one.
$stateDir = "C:\otto\state"
New-Item -ItemType Directory -Force -Path $stateDir | Out-Null

# Shared network so the monitor on port 80 can reach this container by name
# (monitoring/nginx.conf proxies /stats to http://otto-backend:8010). Checked rather than
# created-and-ignored: `docker network create` on an existing network writes to stderr, which
# Windows PowerShell turns into a terminating NativeCommandError under $ErrorActionPreference.
if (-not (docker network ls --filter name=^otto$ --format '{{.Name}}')) {
    docker network create otto | Out-Null
}

# Keep the old container's usage lines before it is removed (#30 - 10-05): every deploy
# deleted the only record of how many turns a session took. Only our own count lines -
# llm/tts count lines - never a child's
# words, and not the access log (it carries client IPs).
& (Join-Path $PSScriptRoot "save_usage.ps1")

# Only now swap: remove the old container and start the new one back to back, so the
# downtime is the container restart plus model load, not the image build.
& (Join-Path $PSScriptRoot "stop_backend.ps1")

# Docker owns the process from here: a container started with `docker run -d` lives in
# dockerd's own process tree, not the Actions runner step's, so it survives the step/job
# ending without the WMI workaround the old Start-Process-based script needed.
docker run -d --name otto-backend --gpus all `
    --network otto `
    --env-file C:\otto\.env `
    -v "${hfCache}:/root/.cache/huggingface" `
    -v "${stateDir}:/state" `
    -e DAILY_CAP_STATE=/state/daily_cap.json `
    -e REPORT_DIR=/state/reports `
    -p 8000:8010 `
    otto-backend
if ($LASTEXITCODE -ne 0) { throw "docker run failed (exit $LASTEXITCODE)" }

Write-Host "started otto-backend container"

# Whisper loads at container start, so /health can take a while to answer. Only report how
# long it took; the workflow's own health check step decides pass/fail.
$sw = [Diagnostics.Stopwatch]::StartNew()
$up = $false
while ($sw.Elapsed.TotalSeconds -lt 90) {
    try {
        Invoke-RestMethod -Uri http://localhost:8000/health -TimeoutSec 3 | Out-Null
        $up = $true
        break
    } catch {
        Start-Sleep -Seconds 2
    }
}
if ($up) {
    Write-Host ("backend answering /health after {0:N0}s" -f $sw.Elapsed.TotalSeconds)
} else {
    Write-Warning "backend not answering /health after 90s - still loading? check: docker logs otto-backend"
}
