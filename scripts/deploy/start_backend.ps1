$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$backendDir = Join-Path $repoRoot "backend"
$logDir = Join-Path $backendDir "logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$logFile = Join-Path $logDir "backend-$timestamp.log"

# Launched via WMI, not Start-Process: a self-hosted Actions runner puts each step's
# process in a Windows Job Object with kill-on-close. Start-Process children stay in
# that job and die the instant the step's script exits, even when "detached". A
# WMI-created process is spawned by the WMI service (WmiPrvSE.exe) instead, so it
# never joins the runner's job object and survives past the step/job.
$cmd = "cmd.exe /c py -m uvicorn main:app --host 0.0.0.0 --port 8010 > `"$logFile`" 2>&1"
$result = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{
    CommandLine      = $cmd
    CurrentDirectory = $backendDir
}
if ($result.ReturnValue -ne 0) {
    throw "failed to start backend via WMI, return code $($result.ReturnValue)"
}

Write-Host "started backend (pid $($result.ProcessId)), logging to $logFile"
