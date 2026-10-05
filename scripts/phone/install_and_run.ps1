# Put the current app on a USB-connected phone and start it in server mode — one command.
# Debug builds are kr.clap.otto.dev (「오또 개발」) and install beside the Play tester app kr.clap.otto (10-05).
#
#   .\scripts\phone\install_and_run.ps1            # install the APK already built, start, show the voice log
#   .\scripts\phone\install_and_run.ps1 -Build     # build from this checkout first
#   .\scripts\phone\install_and_run.ps1 -Lan       # PC1 direct instead of the public address (classroom only)
#
# Why the launch extras: server mode is never saved (MainActivity reads `-e server … -e live …`
# on each start), so launching from the icon gives the script mode — no voice, 🎤 as a toggle.
# `-S` stops the running app first; without it the extras are not read again (10-01).
param(
    [switch]$Build,
    [switch]$Lan,
    [switch]$NoLog
)
$ErrorActionPreference = 'Stop'

$repo = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$adb = "C:\Android\sdk\platform-tools\adb.exe"
$apk = Join-Path $repo "android\app\build\outputs\apk\debug\app-debug.apk"
$server = if ($Lan) { "http://192.168.0.46:8000" } else { "https://otto-back.shelldocs.cloud" }

$devices = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\tdevice$" }
if (-not $devices) { throw "no phone — plug in USB and allow USB debugging on the phone" }
Write-Host "phone: $($devices -join ', ')"

if ($Build -or -not (Test-Path $apk)) {
    $env:JAVA_HOME = "C:\Android\jdk-17.0.20.1+1"
    Push-Location (Join-Path $repo "android")
    try { & .\gradlew.bat -q :app:assembleDebug; if ($LASTEXITCODE -ne 0) { throw "build failed" } }
    finally { Pop-Location }
}
$built = (Get-Item $apk).LastWriteTime
Write-Host "apk: built $built · commit $(git -C $repo log -1 --format='%h %s')"

# 116 MB over USB takes a while; -r keeps the app's data, -g grants the mic permission
& $adb install -r -g $apk
if ($LASTEXITCODE -ne 0) { throw "install failed — a build signed with another key? uninstall kr.clap.otto.dev first (the Play tester app kr.clap.otto is never touched)" }

& $adb logcat -c
& $adb shell am start -S -n kr.clap.otto.dev/com.example.finalproject_demo.MainActivity -e server $server -e live all
Write-Host "started in server mode → $server"

if (-not $NoLog) {
    # recording length · what ended it · baked or live voice · server failures (Ctrl+C to stop)
    Write-Host "voice log (Ctrl+C to stop):"
    & $adb logcat -v time -s Voice:* Server:*
}
