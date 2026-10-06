# 받아쓰기 미세조정 — AI-Hub 3~6세로 `large-v3` LoRA (10-06)

> ⚠️ **이 README 는 임시 작업 지시서다.** 마지막 14번에서 결과 · 기준을 `eval/results.md` 로 옮기고 **이 파일은 지운다**(같은 것을 두 곳에 두지 않는다 · `CLAUDE.md`). 스크립트(`*.py` · `download.sh` · `requirements.txt`)는 남긴다.
> ⚠️ **레포는 공개다.** AI-Hub 데이터 · 푼 wav · 학습한 모델(LoRA 포함)을 커밋 · 릴리스 · 클라우드에 올리지 않는다 — AI-Hub 데이터로 만든 결과물의 공개 배포가 된다.

**왜** — 지금 받아쓰기(`large-v3`)는 3~6세에서 약하다(09-23 · CER 3세 37% · 5세 15% · 어절 보존 53~81%, 합격선 90%에 못 미침). 같은 데이터셋으로 미세조정한 전례(whisper base 120.8% → 19.1%)가 있다. 다만 large-v3 는 출발점이 달라 **얼마나 오를지는 재 봐야 안다.** 하룻밤으로 싸게 확인하고, 아래 기준을 넘을 때만 서버에 쓴다.

**무엇을 하나** — 원래 무게는 얼리고 주의 층 옆에 작은 무게(LoRA)만 학습한 뒤, **원래 모델에 합쳐** faster-whisper 형식으로 바꾼다. 합친 모델은 크기 · 구조가 같아 서버 속도가 그대로다.

## 채택 기준 — 데이터를 보기 전에 고정 (10-06 조장)

평가는 **학습에 안 쓴 아이**(화자 단위로 뗌)와 어른 녹음(`eval/audio/bias` · 짧은 답 「응」 포함 · 깃 밖이라 조장 PC 에만 있다)으로, 원래 모델과 **같은 서버 설정**(`int8_float16` · beam 5)으로 나란히 잰다. 다섯 개 **모두** 통과해야 채택한다.

| | 기준 | 왜 |
|---|---|---|
| 1 | 3~5세 어절 보존 **+10%p 이상**, 화자 부트스트랩 95% 구간 아래끝 **> 0** | 가장 약한 나이가 실제로 나아졌나 · 우연이 아닌가 |
| 2 | 6세 CER 악화 **≤ 1%p** | 이미 괜찮은 나이를 망치지 않나 |
| 3 | 어른 세트에서 원래 맞히던 것을 놓친 수 **≤ 1** | 같이 만들기의 부모 · 짧은 답이 나빠지지 않나 |
| 4 | 헛문장으로 버린 아이 말 증가 **≤ 0.5%p** | 지어낸 말이 늘지 않나(#149) |
| 5 | 지연 p50 증가 **≤ 100ms** | 합치기 · 변환을 제대로 했나(정상이면 같다) |

숫자는 `evaluate.py` 맨 위 상수와 같다. **바꾸려면 여기를 먼저 고치고 이유를 적는다.** 통과 못 하면 서버는 그대로 두고 결과만 `eval/results.md` 에 남긴다.

한계(먼저 적어 둔다): AI-Hub 에는 「응 · 몰라」 같은 짧은 답이 없다(평균 5어절) · 우리 앱의 녹음 조건과 다르다 · 3세 평가 화자는 5명뿐이라 3세 숫자는 흔들린다.

## 데이터 나누기 (10-06 실제 값 · seed 20261006)

원천 zip 8(AI챗봇 · 3~6세 260명 · 149,473클립) → 화자를 나이별로 나눔: **학습 185명 · 확인 10명 · 평가 65명**(3세 5 · 4세 10 · 5세 20 · 6세 30). 학습은 나이마다 같은 몫(기본 4만 클립 = 나이당 1만)으로 뽑는다 — 그대로 두면 6세(7만)만 배운다. 안에 글자가 든 태그 「(SP:버)」가 있는 클립(약 5%)은 학습에서 뺀다. 평가 정답은 `stt_child_bench.py` 와 같은 방식(태그를 통째로 뗌).

---

## 남는 PC 에서 하는 순서 (Claude Code 세션에게 이 절을 그대로 주면 된다)

`[사람]` 은 사람이 할 일, `[세션]` 은 Claude 가 할 일이다. `[사람]` 단계에서는 무엇을 해 달라고 말하고 멈춘다.

| # | 누가 | 할 일 | 됐다 |
|---|---|---|---|
| 1 | 세션 | 레포를 받고 이 브랜치로: `git clone https://github.com/ai-project-team3/bootcamp-final-project.git` → `git checkout eval/whisper-lora` | `eval/whisper_ft/` 가 있다 |
| 2 | 세션 | 가상환경: `py -3.12 -m venv .venv-ft` → `.venv-ft/Scripts/python -m pip install torch==2.8.0 --index-url https://download.pytorch.org/whl/cu126` → `.venv-ft/Scripts/python -m pip install -r eval/whisper_ft/requirements.txt` | `.venv-ft/Scripts/python -c "import torch;print(torch.cuda.is_available())"` 가 True |
| 3 | 사람 | **AI-Hub API 키를 환경 변수로** — PowerShell 창에서 `$env:AIHUB_APIKEY = "<키>"`(AI-Hub 마이페이지 · 데이터셋 108 이용 승인된 계정). 키를 채팅에 붙이지 않는다 | 사람이 「넣었다」고 말한다 |
| 4 | 세션 | 받기 — 같은 창에서 `bash eval/whisper_ft/download.sh D:/aihub`(약 17GB · 회선에 따라 30분~2시간). 끝에 찍힌 원천 · 라벨 경로를 아래 명령의 `$R` · `$L` 로 | 두 경로가 찍힌다 |
| 5 | 세션 | 데이터 나누기 — 명령 ①(약 15분) | 학습 · 확인 · 평가 클립 수가 찍힌다(평가 화자 65명 근처) |
| 6 | 세션 | **시험 한 바퀴** — 명령 ②에 `--base openai/whisper-tiny --max-steps 4` 를 붙이고 `--out` 만 `D:/whisper_ft/smoke` 로(1분). 멈추면 고치고 조장에게 알린다 | `D:/whisper_ft/smoke/best` 가 생긴다 |
| 6-1 | 세션 | **small 파일럿**(1~2시간) — 같은 데이터로 `whisper-small` 을 먼저 학습 · 평가한다. 명령 ②-s → ③-s → ④-s. 학습한 small 을 **원래 small** 과 비교한다(large 와 비교하지 않는다). 첫 25걸음 속도와 판정을 조장에게 알린다. **3~5세 어절 보존이 오르지 않았거나(+0%p 이하) 학습 손실(loss)이 줄지 않으면 7번을 시작하지 말고 멈춰서 조장에게 알린다** — large 에 하룻밤을 쓰기 전에 데이터 · 라벨 · 학습률을 먼저 본다 | 「판정: …」(참고용) |
| 7 | 세션 | 진짜 학습 — 명령 ②(6~8시간 추정). 첫 25걸음의 속도를 조장에게 알린다(진행 막대의 `s/it` × 2,500 이 남은 시간) | 끝에 `→ .../best` |
| 8 | 세션 | 합치기 · 변환 — 명령 ③(10분) | `D:/whisper_ft/ct2/model.bin` |
| 9 | 세션 | 평가 — 명령 ④(30분 남짓). 어른 녹음은 이 PC 에 없어 기준 3 은 ⏸ 보류로 나온다. 표와 판정을 그대로 조장에게 | 「판정: …」 |
| 9-1 | 세션 | **추가 비교** — 남이 Unsloth 로 한국어 미세조정해 올린 모델을 같은 평가 세트 · 같은 서버 설정으로 잰다. 명령 ⑤(변환 10분 · 모델 3GB 받기) → ⑥(30분). 아래 「추가 비교」 참고. 학습(7번)과 GPU 를 같이 쓰지 않게 **9번 뒤에** 한다 | 판정 줄이 하나 더 나온다 |
| 10 | 세션 | **1차 중간 기록** — `eval/results.md` 맨 위에 날짜 절(`## 2026-10-0X · 받아쓰기 미세조정 1차 …`)로 small 파일럿 표 · 판정(참고용이라고 적는다), 우리 모델 표 · 판정 · 채택 기준 다섯 줄 · 데이터 나누기 · 걸음 수 · 걸린 시간 · 한계, 그리고 **추가 비교 표 · 판정**을 적어 이 브랜치에 푸시한다(README 는 **아직 지우지 않는다** — 2차가 남았다). 조장에게 커밋 번호와 판정 셋을 알리고 **2차를 할지 묻는다** | `results.md` 에 1차 절이 있다 |
| 11 | 세션 | **2차 데이터** — 조장이 2차를 하라고 하면. 명령 ⑦(약 30분). 1차와 **같은 아이 · 같은 클립**을 뽑고 학습 클립의 소리만 손질한다(아래 「2차 실험」) | 끝에 「2차 손질(학습 클립만): …」 이 찍힌다 |
| 12 | 세션 | **2차 학습 · 변환** — 명령 ⑧ · ⑨(6~8시간). 1차와 설정이 같다 | `ct2_v2/model.bin` |
| 13 | 세션 | **2차 평가** — 명령 ⑩(원래 large-v3 와) · ⑪(1차 모델과) 각 30분 | 판정 줄 둘 |
| 14 | 세션 | **최종 정리** — ① `results.md` 의 날짜 절에 2차 표 · 판정 둘 · 「1차보다 나은가」를 덧붙인다(2차를 안 했으면 「2차 안 함」 한 줄) ② **`git rm eval/whisper_ft/README.md`** ③ 두 변경을 한 커밋으로 푸시(데이터 · 모델은 넣지 않는다) ④ 조장에게 커밋 번호와 판정을 알린다 | README 가 없고 `results.md` 에 1차 · 2차가 있다 |

**명령** — PowerShell. `$R` · `$L` 은 4번에서 찍힌 경로. 경로 구분은 `/` 로 써도 된다

```powershell
$env:PYTHONIOENCODING = "utf-8"
$R = "<4번에 찍힌 원천 zip 경로>"
$L = "<4번에 찍힌 라벨 zip 경로>"
# ① 나누기
.venv-ft/Scripts/python eval/whisper_ft/prepare.py --raw $R --labels $L --out D:/whisper_ft/data
# ②-s · ③-s · ④-s  small 파일럿 (6-1번) — 결과 파일은 _small 로 따로 남는다
.venv-ft/Scripts/python eval/whisper_ft/train_lora.py --base openai/whisper-small --data D:/whisper_ft/data --out D:/whisper_ft/lora_small
.venv-ft/Scripts/python eval/whisper_ft/merge_convert.py --base openai/whisper-small --adapter D:/whisper_ft/lora_small/best --out D:/whisper_ft/ct2_small
.venv-ft/Scripts/python eval/whisper_ft/evaluate.py --base small --data D:/whisper_ft/data --tuned D:/whisper_ft/ct2_small --tag small
# ② 학습 (끊기면 같은 명령에 --resume)
.venv-ft/Scripts/python eval/whisper_ft/train_lora.py --data D:/whisper_ft/data --out D:/whisper_ft/lora
# ③ 합치기 · 변환
.venv-ft/Scripts/python eval/whisper_ft/merge_convert.py --adapter D:/whisper_ft/lora/best --out D:/whisper_ft/ct2
# ④ 평가 — 원래 large-v3 와 나란히
.venv-ft/Scripts/python eval/whisper_ft/evaluate.py --data D:/whisper_ft/data --tuned D:/whisper_ft/ct2 --tag ours
# ⑤ 추가 비교 — 남이 올린 한국어 미세조정 모델을 우리 서버 형식으로 (학습 없이 바꾸기만)
.venv-ft/Scripts/python eval/whisper_ft/merge_convert.py --model sikaro/unsloth_whisper_large_v3_16bit_Zeroth_kor --out D:/whisper_ft/ct2_zeroth
# ⑥ 추가 비교 평가 — 같은 평가 세트 · 같은 기준
.venv-ft/Scripts/python eval/whisper_ft/evaluate.py --data D:/whisper_ft/data --tuned D:/whisper_ft/ct2_zeroth --tag zeroth
```

**추가 비교 — 왜 · 어떻게 읽나** (10-06 조장)
- 대상: [`sikaro/unsloth_whisper_large_v3_16bit_Zeroth_kor`](https://huggingface.co/sikaro/unsloth_whisper_large_v3_16bit_Zeroth_kor) — `large-v3` 를 Unsloth 로 한국어 미세조정한 개인 업로드(Apache-2.0). 이름으로 보아 **Zeroth Korean(어른이 문장을 읽은 음성)** 으로 학습했다. 모델 카드에 데이터 · 점수 · 설정이 없다
- 목적: 「이미 남이 한국어로 다듬은 것을 쓰면 되지 않나」에 숫자로 답한다. 어른 낭독 데이터라 **아이 말에서 나아질 근거는 없다** — 기대는 낮다
- 판정은 같은 기준으로 나오지만 **참고용이다.** 출처 · 학습 데이터를 모르는 모델이라 기준을 넘어도 바로 서버에 쓰지 않고 조장이 정한다
- ⑤ 가 「`vocab_size` 가 다르다」 등으로 멈추면 구조가 달라 같은 잣대로 잴 수 없는 것이다 — 그 사실만 적고 넘어간다
- 같은 계정의 회의 음성판(`sikaro/unsloth_whisper_16bit_model_kor_12000_meeting`)도 같은 방법으로 잴 수 있다. 시간이 남을 때만

**2차 실험 — 학습 클립을 앱에 들어오는 소리처럼 손질** (10-06 조장 · 1차가 기준선이고 2차는 한 가지만 바꾼 비교다)

| 옵션 | 무엇 | 왜 |
|---|---|---|
| `--vad-trim` | 앱과 같은 Silero VAD(faster-whisper 에 든 것)로 말 구간을 찾아 **앞 0.5초 · 뒤 0.3초만** 남긴다 | 앱은 말 끝 0.3초 뒤에 녹음을 끊는다(`net/Voice.kt`). AI-Hub 클립은 앞뒤 무음이 제각각이다. 앞을 다 자르지 않는 건 앱에서도 마이크를 켠 뒤 말을 시작하기까지 무음이 있어서다 |
| `--noise` | **다른 아이 말소리**를 작게 겹친다(학습 클립의 30% · SNR 15~25dB) · **분홍 잡음**(20% · SNR 20~30dB) | 거실의 형제 · 친구 목소리 · 에어컨 · TV. 따로 받을 소음 자료가 없어 학습 데이터 안의 다른 아이 목소리와 만든 잡음으로 대신한다 |

- 평가 · 확인 클립은 **손대지 않는다.** 나누기 rng 와 손질 rng 가 따로라 1차와 **같은 아이 · 같은 클립**이 뽑힌다(10-06 조장 PC 에서 200클립 · 평가 65클립 일치 확인 · 평균 3.29초 → 3.08초 · 겹침 31% · 잡음 19%)
- 평가는 **1차 데이터 폴더(`D:/whisper_ft/data`)의 평가 세트**로 한다 — 같은 아이 · 같은 클립이어야 1차와 비교된다
- **2차를 1차 대신 쓰는 조건**(미리 고정): ⑩ 에서 원래 large-v3 대비 채택 기준 다섯 개를 통과하고, **그리고** ⑪(1차 모델 대비)의 3~5세 어절 보존 95% 구간 아래끝이 **0 보다 크다**(1차보다 확실히 낫다). ⑪ 의 다른 판정 줄은 참고만 한다
- 「짧은 답 만들기」는 넣지 않았다 — 낱말 위치를 맞추지 않고 자르면 정답과 소리가 어긋난다

```powershell
# ⑦ 2차 데이터 — 같은 원천 · 라벨 · 시드, 학습 클립만 손질
.venv-ft/Scripts/python eval/whisper_ft/prepare.py --raw $R --labels $L --out D:/whisper_ft/data2 --vad-trim --noise
# ⑧ 2차 학습
.venv-ft/Scripts/python eval/whisper_ft/train_lora.py --data D:/whisper_ft/data2 --out D:/whisper_ft/lora_v2
# ⑨ 2차 합치기 · 변환
.venv-ft/Scripts/python eval/whisper_ft/merge_convert.py --adapter D:/whisper_ft/lora_v2/best --out D:/whisper_ft/ct2_v2
# ⑩ 2차 평가 — 원래 large-v3 와 (평가 세트는 1차 폴더)
.venv-ft/Scripts/python eval/whisper_ft/evaluate.py --data D:/whisper_ft/data --tuned D:/whisper_ft/ct2_v2 --tag v2
# ⑪ 2차 평가 — 1차 모델과 (--base 에 1차 모델 폴더)
.venv-ft/Scripts/python eval/whisper_ft/evaluate.py --data D:/whisper_ft/data --base D:/whisper_ft/ct2 --tuned D:/whisper_ft/ct2_v2 --tag v2_vs_v1
```

**문제가 나면**
- 받기가 401 · 403 → 키가 틀렸거나 그 계정이 데이터셋 108 이용 승인이 없다 — 사람에게 알린다
- 학습이 메모리 부족(CUDA out of memory)으로 멈춤 → `--batch 2 --accum 8` 로(실효 배치 16 그대로)
- 평가에서 `cublas64_12.dll` 을 못 찾음 → torch 가 CUDA 판인지 확인(2번). `evaluate.py` 가 torch 의 lib 폴더를 PATH 에 올린다
- 아침까지 안 끝날 것 같음 → `--max-steps 1500` 으로 다시(체크포인트는 500걸음마다 남는다)

**판정 뒤** — 탈락이면 끝(서버 그대로). 기준 1·2·4·5 를 통과하면 **어른 확인(기준 3)과 서버 반영은 조장이 정한다** — 모델은 공개 레포로 옮길 수 없으니, 조장 PC 에서 같은 스크립트로 다시 학습하거나 옮길 길을 그때 정한다. 서버 반영은 PC1 `.env` 에 `STT_MODEL=<ct2 폴더>` 한 줄 · 되돌리기는 그 줄을 지운다.

## 확인한 것 (10-06 조장 PC · 시험 한 바퀴)

`whisper-tiny` 로 ①~④ 와 ⑤(합치지 않고 바꾸기만)를 끝까지 돌렸고, ⑦(2차 손질)은 200클립으로 돌려 1차와 같은 클립이 뽑히는 것을 확인했다 — 나누기 35초 · 학습 4걸음 · 합치기 · 변환 · 평가 · 판정까지 오류 없음(숫자는 tiny 4걸음이라 의미 없다). 어른 녹음이 없을 때 기준 3 이 ⏸ 보류로 나오는 것도 확인했다. `download.sh` 는 받는 곳(`aihubshell`) 주소와 파일 번호(48685 · 48616)만 확인했고 실제로 받아 보지는 않았다. `large-v3` 로는 아직 안 돌렸다 — 12GB 에 들어가는지 · 걸음 속도는 남는 PC 의 7번에서 처음 확인한다.
