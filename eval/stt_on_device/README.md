# 받아쓰기 온디바이스 측정 (10-16)

미세 조정한 Whisper small을 whisper.cpp(v1.9.5)로 바꿔 안드로이드 폰에서 평가 세트를 돌리고, `eval/whisper_ft/evaluate.py`의 규칙으로 채점한다. 결과는 `eval/results.md` 10-16 절.

| 파일 | 하는 일 |
|---|---|
| `prep_vocab.py` | HF 형식 모델 폴더에 whisper.cpp 변환기가 읽는 `vocab.json`, `added_tokens.json`을 만든다 |
| `patch_cli.py` | whisper-cli에 `-mt`(토큰 상한) 옵션을 더한다. 앱이 쓸 `max_tokens`와 같은 값 |
| `prep_list.py` | 아이 평가 세트와 어른 세트를 `c0000.wav` 형식으로 모으고 `list.txt`(클립별 창 크기)와 `clips.csv`(채점용 정답)를 만든다 |
| `run_eval.sh` | 폰에서 클립마다 whisper-cli를 돌려 `이름, 로딩 ms, 전체 ms, 글자`를 tsv로 남긴다. 중간에 끊겨도 이어서 돈다 |
| `score_phone.py` | 폰 tsv를 채점하고 GPU 기준선 CSV와 비교한다. `--collapse`는 앱의 연속 반복 제거 |
| `score_gpu.py` | GPU 실행 CSV 두 개(원래 small, 미세 조정 small)를 나이별로 비교한다 |

순서: 모델 변환(`convert-h5-to-ggml.py`) → 폰에서 `quantize ... q8_0` → `prep_list.py` → `adb push` → `run_eval.sh <모델> 1 fitcap <out.tsv> -nf` → `adb pull` → `score_phone.py`.

아이 음성(AI-Hub)은 레포와 외부 API에 올리지 않는다. 팀 PC 사이에서만 옮긴다.
