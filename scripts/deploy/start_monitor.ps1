$ErrorActionPreference = 'Stop'

# The endpoint monitor: nginx on port 80 serving monitoring/index.html and proxying /stats to
# the backend container. Started once by hand — it is not part of the deploy workflow, and it
# keeps working across backend redeploys because Docker resolves the name on the shared network.
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$monitorDir = Join-Path $repoRoot "monitoring"

if (-not (docker network ls --filter name=^otto$ --format '{{.Name}}')) {
    docker network create otto | Out-Null
}
docker rm -f otto-monitor | Out-Null

docker run -d --name otto-monitor `
    --network otto `
    -v "$(Join-Path $monitorDir 'index.html'):/usr/share/nginx/html/index.html:ro" `
    -v "$(Join-Path $monitorDir 'login.html'):/usr/share/nginx/html/login.html:ro" `
    -v "$(Join-Path $monitorDir 'nginx.conf'):/etc/nginx/conf.d/default.conf:ro" `
    -p 80:80 `
    nginx:alpine
if ($LASTEXITCODE -ne 0) { throw "docker run failed (exit $LASTEXITCODE)" }

Write-Host "monitor on http://localhost/ (stats proxied from otto-backend) — admin sign-in: ADMIN_USER / ADMIN_PASSWORD_HASH in .env"
