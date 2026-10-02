# 오또 그림 공방 — 누구나 같은 그림체로 앱 그림 만들기

앱의 배경 · 인물 · 소품 그림은 **ComfyUI + krea2 turbo 모델**로 만들었다. 지금까지는 한 PC에서만 뽑을 수 있어서
디자인 수정이 한 사람에게 몰렸다. 이 문서대로 하면 **누구나 자기 PC에서 같은 결의 그림**을 뽑아 PR로 올릴 수 있다.

| 파일 | 하는 일 |
|---|---|
| `tools/setup_comfyui.ps1` | ComfyUI · torch · 모델 4개를 같은 판으로 설치 (한 번만) |
| `tools/otto_art.py` | 그림 뽑기 · 고르기 · 다시 뽑기. **그림체 문장이 여기 한 곳에 있다** |
| `tools/art_recipes/*.json` | 앱에 넣은 그림마다 「무슨 문장 · 어떤 시드」로 뽑았는지. 같은 레시피면 같은 그림이 나온다 |
| `tools/workflows/otto_bg.json` · `otto_cut.json` | 브라우저 화면에서 쓸 때 끌어다 놓는 워크플로 |
| `.claude/skills/otto-art/` | Claude Code 에게 "놀이공원 배경 만들어 줘"라고 하면 이 순서대로 해 준다 |

---

## 1. 설치 (한 번만)

필요: NVIDIA 그래픽카드(드라이버 570 이상) · git · Python 3.11/3.12 · 디스크 약 25GB. 팀 PC(RTX 3060 12GB)면 된다.

```powershell
# 레포의 android 폴더에서
powershell -ExecutionPolicy Bypass -File tools\setup_comfyui.ps1
```

- 기본 설치 위치는 `C:\OttoComfyUI` — 바꾸려면 `-Dir D:\OttoComfyUI`
- 모델을 13.6GB 받는다. 끊기면 **다시 돌리면 이어 받는다.** 받은 뒤 해시를 확인해서 깨진 파일은 지운다
- 앱 그림을 만든 PC와 **같은 ComfyUI 판(`783545f6`) · torch 2.11.0(CUDA 12.8) · 같은 모델 파일**이다

## 2. 켜기

`C:\OttoComfyUI\start_otto_comfy.bat` 더블클릭 → 창이 뜬 채로 1분쯤 기다린다 (창을 닫으면 꺼진다).

```powershell
python tools\otto_art.py check
```
`준비 끝`이 나오면 된다. 빠진 게 있으면 무엇이 없는지 알려 준다.

⚠️ **에뮬레이터와 같이 켜지 않는다.** 그래픽카드 메모리를 나눠 써서 둘 다 느려지거나 PC가 멈춘다.

## 3. 그림 뽑기

```powershell
# 배경 (가로 1344×768) — 3장 뽑아서 고른다
python tools\otto_art.py bg bg_aquarium "inside a big aquarium tunnel, glass ceiling with fish swimming above, blue light"

# 오려 낸 물건 · 인물 (1024×1024, 배경을 빼서 투명 PNG)
python tools\otto_art.py cut coop_el_zoo "a cute felt zoo entrance gate with a giraffe sign"
```

- 결과는 `tools\art_out\` 에 `이름_시드.png` 로 쌓인다 (깃에 안 올라간다). 오려 낸 것은 `_cut.png`
- 한 장에 **약 50초** (RTX 4060 노트북 기준. 3060에서는 직접 재서 여기 적어 주세요)
- 옵션: `--count 5` 더 많이 · `--seed 1234` 한 장만 · `--size 768x1152` 크기 · `--style room` 오또의 방 그림체

### 고르기 → 앱에 넣기

```powershell
python tools\otto_art.py pick bg_aquarium 1838472910     # 고른 시드
python tools\shrink_assets.py                            # 앱에 넣기 전에 크기 줄이기
```

`pick` 은 두 가지를 한다: `app\src\main\res\drawable\이름.png` 로 복사하고, `tools\art_recipes\이름.json` 에 레시피를 남긴다.
이미 있는 그림을 바꿀 때는 `--replace`.

### 같은 그림 다시 뽑기

```powershell
python tools\otto_art.py again bg_aquarium
```
레시피의 문장 · 시드 · 크기 그대로 다시 뽑는다. 같은 PC면 **픽셀까지 같다**(10-02 확인). 다른 그래픽카드에서는 거의 같다.

## 4. 브라우저 화면으로 쓰기

ComfyUI 를 켠 뒤 `http://127.0.0.1:8188` 을 열고 `tools\workflows\otto_bg.json`(또는 `otto_cut.json`)을 화면에 끌어다 놓는다.
**CLIP Text Encode 상자의 맨 앞 설명만** 바꾸고 Queue 를 누른다. 뒤에 붙은 그림체 문장은 지우지 않는다.
결과는 `C:\OttoComfyUI\output\otto_art\` 에 저장된다. 이렇게 만든 그림은 레시피가 안 남으니, 앱에 넣을 그림은 3번 방법을 쓴다.

## 5. 그림체 규칙

**그림체 문장(`otto_art.py` 의 `STYLES`)은 혼자 바꾸지 않는다.** 바꾸면 새 그림만 결이 달라 앱이 어수선해진다. 바꾸고 싶으면 팀에 먼저 말한다.

설명 쓰는 요령:
- **영어로, 무엇이 있는지만** 쓴다. "felt", "cute", "pastel" 같은 그림체 말은 자동으로 붙는다
- 배경에는 사람 · 동물을 넣지 않는다 (자동으로 `no characters` 가 붙는다). 인물은 `cut` 으로 따로 뽑아 얹는다
- 글자가 들어가면 안 된다 — 간판 · 책 표지에 글자가 생기면 다른 시드를 고른다
- 아이 앱이다: 무섭거나 어두운 것(불길, 다친 모습, 무기)은 피한다. **사람이 눈으로 검수한다** — 모델에 안전 필터가 없다
- 오려 낼 그림은 **배경이 하얗게 나와야** 깨끗하게 잘린다. 배경에 색이 깔리면 다른 시드를 고른다
- 이름은 영어 소문자 · 숫자 · `_` 만 (안드로이드 규칙). 앞머리로 종류를 알린다: `bg_` 배경 · `coop_el_` 협업 요소 · `hero_` 주인공 …

## 6. 다른 PC 의 ComfyUI 쓰기

내 PC에 그래픽카드가 없으면 켜져 있는 다른 PC의 ComfyUI 를 쓸 수 있다.

```powershell
$env:COMFY_URL = "http://192.168.0.xx:8188"
python tools\otto_art.py check
```
그 PC는 `start_otto_comfy.bat` 의 `--listen 127.0.0.1` 을 `--listen 0.0.0.0` 으로 바꿔 켜고, 방화벽 8188 을 **팀원 IP 에만** 연다
(ComfyUI 는 비밀번호가 없다). 시연용 그림 서버(PC2)는 시연 중 느려지므로 쓰지 않는다. 옛 `gen_*.py` 들도 `COMFY_URL` 을 읽는다.

## 7. 올리기

1. 자기 브랜치(`feature/<이름>`)에서 `res/drawable/이름.png` 와 `tools/art_recipes/이름.json` 을 **같이** 커밋한다
2. 그림을 쓰는 코드가 있으면 같은 PR에 넣는다
3. main 으로 PR → 조장 리뷰 후 합친다

## 8. 막힐 때

| 증상 | 할 일 |
|---|---|
| `그림 서버에 닿지 않는다` | `start_otto_comfy.bat` 창이 떠 있나, 1분 기다렸나 |
| `check` 에서 모델이 `없음` | `setup_comfyui.ps1` 을 다시 돌린다 (받은 것은 건너뛴다) |
| `실패: ... out of memory` | 에뮬레이터 · 게임 · 다른 그림 작업을 끄고 다시 |
| 오려 낸 그림이 덩어리째 남는다 | 배경이 하얗지 않은 시드다 — 다른 시드를 고른다 |
| 설치 중 `드라이버 ... 570 이상` | NVIDIA 앱에서 드라이버 업데이트 후 다시 |

## 참고 — 옛 스크립트

`gen_assets.py` · `gen_heroes.py` · `gen_room.py` 같은 `gen_*.py` 는 지금 앱 그림을 처음 만든 스크립트다. 그림체 문장은 `otto_art.py` 와 같지만
**시드를 그때그때 뽑아서 레시피가 없다** — 같은 그림을 다시 뽑을 수는 없다. 새 그림은 `otto_art.py` 로 만든다.
`gen_faces.py` 만 다른 모델(FLUX Kontext · ComfyUI-GGUF 확장)을 써서 이 설치로는 돌지 않는다.
