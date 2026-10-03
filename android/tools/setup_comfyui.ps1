# 오또 그림 공방 — 팀원 PC 에 ComfyUI 와 그림 모델을 까는 스크립트 (10-02)
#
# 앱 그림을 만든 PC 와 같은 ComfyUI 판 · 같은 torch · 같은 모델 파일(해시까지)로 맞춘다.
# 그래야 tools/otto_art.py 로 뽑은 그림이 기존 앱 그림과 같은 결로 나온다.
#
#   powershell -ExecutionPolicy Bypass -File tools\setup_comfyui.ps1                 # C:\OttoComfyUI 에 설치
#   powershell -ExecutionPolicy Bypass -File tools\setup_comfyui.ps1 -Dir D:\OttoComfyUI
#
# 필요: NVIDIA 그래픽카드(드라이버 570 이상) · git · Python 3.11 또는 3.12 · 디스크 약 25GB
# 다시 돌려도 된다 — 이미 받은 모델은 해시만 확인하고 넘어간다. 끊긴 다운로드는 이어 받는다.

param([string]$Dir = "C:\OttoComfyUI")

$ErrorActionPreference = "Stop"
$ComfyCommit = "783545f689a0af730065994b46b382ae24844c99"   # 앱 그림을 만든 판 (krea2 · 배경 제거 노드가 들어 있다)
$TorchIndex = "https://download.pytorch.org/whl/cu128"
$Models = @(
    @{ Folder = "diffusion_models";   Name = "krea2_turbo_nvfp4.safetensors";      Size = 7673668448
       Url = "https://huggingface.co/Comfy-Org/Krea-2/resolve/main/diffusion_models/krea2_turbo_nvfp4.safetensors"
       Sha = "61527003b2d537055494d01bc8efe51d6e86e64192ba23e3721a5647231fe394" },
    @{ Folder = "text_encoders";      Name = "qwen3vl_4b_fp8_scaled.safetensors";  Size = 5242467968
       Url = "https://huggingface.co/Comfy-Org/Krea-2/resolve/main/text_encoders/qwen3vl_4b_fp8_scaled.safetensors"
       Sha = "54bd5144df0bbc25dd6ccadfcb826b521445a1b06ae5a42570bdd2974ca87094" },
    @{ Folder = "vae";                Name = "qwen_image_vae.safetensors";         Size = 253806246
       Url = "https://huggingface.co/Comfy-Org/Krea-2/resolve/main/vae/qwen_image_vae.safetensors"
       Sha = "a70580f0213e67967ee9c95f05bb400e8fb08307e017a924bf3441223e023d1f" },
    @{ Folder = "background_removal"; Name = "birefnet.safetensors";               Size = 444473596
       Url = "https://huggingface.co/Comfy-Org/BiRefNet/resolve/main/background_removal/birefnet.safetensors"
       Sha = "9ab37426bf4de0567af6b5d21b16151357149139362e6e8992021b8ce356a154" }
)

function Step($t) { Write-Host ""; Write-Host "== $t" -ForegroundColor Cyan }
function Fail($t) { Write-Host "실패: $t" -ForegroundColor Red; exit 1 }

# ── 1. 준비물 ────────────────────────────────────────────────────
Step "1/5 준비물 확인"
if (-not (Get-Command nvidia-smi -ErrorAction SilentlyContinue)) { Fail "nvidia-smi 가 없다 — NVIDIA 드라이버를 먼저 설치한다" }
$gpu = (& nvidia-smi --query-gpu=name,memory.total,driver_version --format=csv,noheader) -split "`n" | Select-Object -First 1
Write-Host "그래픽카드: $gpu"
$driver = [double](($gpu -split ",")[2].Trim())
if ($driver -lt 570) { Fail "드라이버 $driver — 570 이상이어야 한다 (torch cu128). NVIDIA 앱이나 홈페이지에서 업데이트한다" }
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { Fail "git 이 없다 — https://git-scm.com 에서 설치한다" }
$py = $null
foreach ($v in "3.12", "3.11") {
    try { & py "-$v" -c "import sys" 2>$null; if ($LASTEXITCODE -eq 0) { $py = $v; break } } catch {}
}
if (-not $py) { Fail "Python 3.11 / 3.12 가 없다 — python.org 에서 3.12 를 설치한다 (py 런처 포함)" }
Write-Host "Python $py"
$free = (Get-PSDrive ((Split-Path $Dir -Qualifier).TrimEnd(":"))).Free / 1GB
if ($free -lt 25) { Write-Host ("경고: {0:N0}GB 남았다 — 25GB 쯤 필요하다" -f $free) -ForegroundColor Yellow }

# ── 2. ComfyUI ───────────────────────────────────────────────────
Step "2/5 ComfyUI ($ComfyCommit 판) → $Dir"
if (-not (Test-Path "$Dir\.git")) {
    git clone https://github.com/comfyanonymous/ComfyUI.git $Dir
    if ($LASTEXITCODE -ne 0) { Fail "ComfyUI 를 받지 못했다" }
}
git -C $Dir fetch --quiet origin
git -C $Dir checkout --quiet $ComfyCommit
if ($LASTEXITCODE -ne 0) { Fail "ComfyUI 판을 맞추지 못했다 — $Dir 안에서 직접 고친 파일이 있으면 되돌린다" }

# ── 3. 파이썬 패키지 ─────────────────────────────────────────────
Step "3/5 파이썬 패키지 (torch 2.11.0 + CUDA 12.8 — 몇 분 걸린다)"
$vpy = "$Dir\venv\Scripts\python.exe"
if (-not (Test-Path $vpy)) { & py "-$py" -m venv "$Dir\venv" }
& $vpy -m pip install --quiet --upgrade pip
& $vpy -m pip install --quiet torch==2.11.0 torchvision==0.26.0 torchaudio==2.11.0 --index-url $TorchIndex
if ($LASTEXITCODE -ne 0) { Fail "torch 설치 실패" }
& $vpy -m pip install --quiet -r "$Dir\requirements.txt"
if ($LASTEXITCODE -ne 0) { Fail "ComfyUI 패키지 설치 실패" }
& $vpy -c "import torch; assert torch.cuda.is_available(), 'CUDA 안 잡힘'; print('torch', torch.__version__, '·', torch.cuda.get_device_name(0))"
if ($LASTEXITCODE -ne 0) { Fail "torch 가 그래픽카드를 못 잡는다 — 드라이버를 업데이트하고 다시 돌린다" }

# ── 4. 모델 ──────────────────────────────────────────────────────
Step "4/5 그림 모델 4개 (약 13.6GB · 처음엔 오래 걸린다)"
foreach ($m in $Models) {
    $folder = "$Dir\models\$($m.Folder)"
    New-Item -ItemType Directory -Force $folder | Out-Null
    $path = "$folder\$($m.Name)"
    $have = (Test-Path $path) -and ((Get-Item $path).Length -eq $m.Size)
    if (-not $have) {
        Write-Host "받는 중: $($m.Name)"
        curl.exe -L --fail -C - -o $path $m.Url
        if ($LASTEXITCODE -ne 0) { Fail "$($m.Name) 를 받지 못했다 — 다시 돌리면 이어 받는다" }
    }
    Write-Host "확인 중: $($m.Name)"
    $sha = (Get-FileHash $path -Algorithm SHA256).Hash.ToLower()
    if ($sha -ne $m.Sha) {
        Remove-Item $path -Confirm:$false
        Fail "$($m.Name) 해시가 다르다(깨진 파일) — 지웠으니 다시 돌린다"
    }
    Write-Host "  OK" -ForegroundColor Green
}

# ── 5. 켜는 파일 ─────────────────────────────────────────────────
Step "5/5 켜는 파일"
$start = "$Dir\start_otto_comfy.bat"
@"
@echo off
cd /d "$Dir"
echo 오또 그림 서버를 켠다 — 창을 닫으면 꺼진다. 브라우저: http://127.0.0.1:8188
"$vpy" main.py --listen 127.0.0.1 --port 8188
"@ | Set-Content -Encoding Default $start
Write-Host $start

Write-Host ""
Write-Host "설치 끝." -ForegroundColor Green
Write-Host "  1) $start 를 더블클릭해 켠다 (처음엔 1분쯤 걸린다)"
Write-Host "  2) 레포 android 폴더에서:  python tools\otto_art.py check"
Write-Host "  3) 쓰는 법은 android\tools\ART.md"
