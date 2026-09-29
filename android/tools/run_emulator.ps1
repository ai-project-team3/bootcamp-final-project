# 에뮬레이터를 **컴퓨터가 멈추지 않게** 띄운다.
#
# 왜 이 스크립트가 있나
#   이 기기는 하이브리드 그래픽 노트북(Intel UHD + NVIDIA RTX 4060 Laptop)이다.
#   ComfyUI가 모델을 올려 VRAM을 잡고 있는데 에뮬레이터를 `-gpu host` 로 띄우면
#   둘이 같은 NVIDIA GPU를 두고 부딪혀 **드라이버가 멈추고 데스크톱 전체가 언다.**
#   되돌릴 방법이 전원 버튼밖에 없다 (트러블슈팅 §5-1 · §6-10).
#
#   화면을 캡처해서 확인하는 용도에는 GPU 가속이 필요 없다. 그래서 여기서는
#   **소프트웨어 렌더링(swiftshader)** 으로 띄운다 — 조금 느리지만 드라이버를 아예 건드리지 않는다.
#
# 쓰는 법
#   powershell -ExecutionPolicy Bypass -File tools\run_emulator.ps1            # 켜고 앱 설치 · 실행
#   powershell -ExecutionPolicy Bypass -File tools\run_emulator.ps1 -Stop      # 정상 종료
#   powershell -ExecutionPolicy Bypass -File tools\run_emulator.ps1 -NoInstall # 설치 없이 켜기만
#   powershell -ExecutionPolicy Bypass -File tools\run_emulator.ps1 -Headless  # 창 없이 (캡처만 할 때)

param(
    [switch]$Stop,
    [switch]$NoInstall,
    [switch]$Headless,
    # 기본값을 android-34 로 바꿨다 (9/21).
    # 전에는 `Pixel_3a`(android-36 이미지)였는데, 에뮬레이터가 35.5.10 이라 **이미지가 에뮬레이터보다 새로웠다.**
    # 그 조합으로 하루에 네 번 기계가 얼었다(트러블슈팅 6-12). android-34 는 훨씬 오래 검증된 조합이다.
    # 예전 것으로 돌리려면:  run_emulator.ps1 -Avd Pixel_3a
    [string]$Avd = "Pixel_3a_api34"
)

$ErrorActionPreference = "Stop"
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$adb = "$sdk\platform-tools\adb.exe"
$emu = "$sdk\emulator\emulator.exe"
$proj = Split-Path -Parent $PSScriptRoot
$apk = "$proj\app\build\outputs\apk\debug\app-debug.apk"

function Wait-EmulatorGone {
    for ($i = 0; $i -lt 60; $i++) {
        $d = & $adb devices 2>$null | Select-String "emulator"
        if (-not $d) { return $true }
        Start-Sleep -Seconds 2
    }
    return $false
}

# ── 끄기 ────────────────────────────────────────────────────────
# ⚠️ 프로세스를 강제로 죽이지 않는다. 죽이면 사용자 데이터가 깨져 다음에 `offline` 로 올라온다 (§6-8)
if ($Stop) {
    Write-Host "에뮬레이터를 정상 종료합니다 (adb emu kill)..."
    & $adb emu kill 2>$null | Out-Null
    if (Wait-EmulatorGone) { Write-Host "종료됨" } else { Write-Warning "아직 남아 있습니다. 창의 X로 닫아 주세요" }
    exit 0
}

# ── 1. ComfyUI가 떠 있으면 먼저 내린다 ──────────────────────────
# 이 한 줄이 강제 재부팅을 막는다. 둘은 같은 GPU를 두고 부딪힌다
try {
    $r = Invoke-WebRequest -Uri "http://127.0.0.1:8188/system_stats" -TimeoutSec 3 -UseBasicParsing
    if ($r.StatusCode -eq 200) {
        Write-Warning "ComfyUI가 떠 있습니다. 에뮬레이터와 같이 돌리면 화면이 얼 수 있습니다 (트러블슈팅 5-1)."
        Write-Host "  ComfyUI를 먼저 끄고 다시 실행하세요. 그림을 다 뽑았다면 창을 닫으면 됩니다."
        exit 1
    }
} catch { }   # 안 떠 있으면 정상

# ── 2. Gradle 데몬을 내려 메모리를 돌려받는다 (2~3GB) ────────────
if (Test-Path "$proj\gradlew.bat") {
    Write-Host "Gradle 데몬 정리 중..."
    & "$proj\gradlew.bat" --stop 2>$null | Out-Null
}

# ── 3. 에뮬레이터 — 소프트웨어 렌더링 · 자원 아껴서 ─────────────
$args = @(
    "-avd", $Avd,
    "-no-snapshot-load",     # 깨진 스냅샷으로 올라오는 것을 막는다
    "-no-boot-anim",
    "-gpu", "swiftshader_indirect",   # ⭐ 핵심 — NVIDIA 드라이버를 건드리지 않는다
    "-memory", "2048",
    "-cores", "2"            # 사용자가 쓸 CPU를 남긴다
)
if ($Headless) { $args += "-no-window" }

Write-Host "에뮬레이터를 켭니다 (소프트웨어 렌더링)..."
Start-Process -FilePath $emu -ArgumentList $args -WindowStyle Minimized

# ── 4. 부팅 기다리기 ────────────────────────────────────────────
& $adb wait-for-device
$t0 = Get-Date
while ((& $adb shell getprop sys.boot_completed 2>$null).Trim() -ne "1") {
    if (((Get-Date) - $t0).TotalSeconds -gt 300) { Write-Error "부팅이 5분을 넘겼습니다"; exit 1 }
    Start-Sleep -Seconds 3
}
Write-Host ("부팅 완료 ({0:N0}초)" -f ((Get-Date) - $t0).TotalSeconds)

# 가로 고정 — 이 앱은 가로 전용이다 (트러블슈팅 3-1)
& $adb shell settings put system accelerometer_rotation 0 | Out-Null
& $adb shell settings put system user_rotation 1 | Out-Null

# ── 5. 앱 설치 · 실행 ───────────────────────────────────────────
if (-not $NoInstall) {
    if (-not (Test-Path $apk)) {
        Write-Warning "APK가 없습니다. 먼저 gradlew assembleDebug 를 돌리세요: $apk"
    } else {
        Write-Host "앱 설치 중..."
        & $adb install -r $apk | Select-Object -Last 1
        # installDebug 만으로는 실행 중인 프로세스가 안 바뀐다 (트러블슈팅 6-3)
        & $adb shell am force-stop com.example.finalproject_demo | Out-Null
        & $adb shell am start -n com.example.finalproject_demo/.MainActivity | Out-Null
    }
}

Write-Host ""
Write-Host "준비됐습니다. 끌 때는:  powershell -ExecutionPolicy Bypass -File tools\run_emulator.ps1 -Stop"
