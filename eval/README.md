# eval — 평가 하네스

**오늘(9/18) 세 명이 여기를 채운다.** 지시서와 역할별 워크시트는 **노션**에 있다.

| 파일 | 누가 | 무엇 |
|---|---|---|
| `judge_prompt.md` · `judge_schema.json` | 박진웅 | 판정 프롬프트·스키마. **확정 후 손대지 않는다** |
| `story_prompt.md` · `story_schema.json` | 박진웅 | 생성 프롬프트·스키마 |
| `fixtures_judge.jsonl` | 안치영 추출 · 박진웅 라벨 | 100개. 나중에 **분류 모델 학습 데이터로 두 번 쓴다** |
| `fixtures_story.jsonl` | 안치영 | 칸이 다 찬 상태 3벌 |
| `run_judge.py` · `score.py` · `corrupt.py` | 최민우 | 러너·채점기·자모 손상 |
| `results.md` | 전원 | **날짜·모델·조건·값 누적. 이 표가 최종 발표 슬라이드가 된다** |
| `최민우_평가_인수인계.md` | 최민우 | 2026-09-20 실모델 실행 결과·429 재현·후속 작업 |
| `tools/` · `평가셋_라벨링_지침.md` | 안치영 | 평가셋을 **다시 뽑고 라벨을 붙이고 대조하는** 도구와 그 기준 |

## tools/ — 평가셋을 만들고 라벨을 붙이는 도구

`fixtures_*.jsonl` 은 손으로 쓴 파일이 아니라 **뽑아낸 것**이다. 앱 질문·더미답이 바뀌면 다시 뽑는다.

| 도구 | 무엇 |
|---|---|
| `make_fixtures.py` | 앱 더미답 덤프 → `fixtures_judge.jsonl` 100문항 **층화 추출**. 씨앗이 고정이라 같은 덤프에서 같은 100문항이 나온다 |
| `sheet.py` | 라벨을 **빈칸 채우기 파일**로 붙인다. `--new` 로 만들고 `--read` 로 `.jsonl` 로 바꾼다. 잘못 적으면 저장하지 않는다 |
| `label.py` | 같은 일을 대화형으로. 문항마다 질문에 하나씩 답한다 |
| `kappa.py` | 두 사람 라벨의 **일치도(Cohen's κ)**. 불일치가 3개를 넘으면 경고한다 |
| `preview.py` | 픽스처를 사람이 읽는 표로. **생성물이라 커밋하지 않는다** |

```
python tools/make_fixtures.py --dump <app/build/bank_dump.jsonl>   # 평가셋 다시 뽑기
python tools/sheet.py --new --all --by 진웅                        # 100문항 라벨 시트
python tools/sheet.py --read --all --by 진웅                       # → labels_진웅_전체100.jsonl
python tools/kappa.py labels_진웅_전체100.jsonl labels_치영_검수20.jsonl
```

- **덤프는 레포 밖**(안드로이드 빌드 산출물)이라 `--dump` 나 `BANK_DUMP` 로 준다. 경로를 안 주면 멈춘다
- **다시 뽑으면 확정한 `gold` 가 지워진다.** 확정 뒤에는 `labels_*.jsonl` 을 따로 보관한다
- 라벨 기준과 경계 사례는 `평가셋_라벨링_지침.md` §2. 칸 이름의 출처는 `../guidelines/2_공통_데이터_모델.md`

## 돌리는 법

**레포 루트에서** 모듈로 실행한다. 스크립트가 `eval/` 안에 있고 서로를 패키지로 임포트하므로,
`python eval/preflight.py`처럼 파일로 직접 부르면 임포트가 깨진다.

```bash
pip install -r eval/requirements.txt

python -m eval.preflight          # 팀 파일과 키가 갖춰졌는지 · 평가셋 형식·gold 검사
python -m eval.run_demo           # API 없이 러너·채점기 동작 확인 (과금 0)
python -m eval.run_team_eval --yes-spend   # 후보 전체. 비용이 든다
python -m eval.run_team_eval --models gpt-5.6-luna --yes-spend             # 한 종만
python -m eval.run_team_eval --models mistral-small-4 --request-delay 1 --max-retries 4 --yes-spend
python -m eval.score_existing     # gold가 늦게 왔을 때 API 재호출 없이 재채점
python -m eval.corrupt --append-results    # 실제 Android THEMES 프리셋 손상 실험
```

키는 레포 루트 `.env`에 넣는다 — `OPENAI_API_KEY` · `ANTHROPIC_API_KEY` · `MISTRAL_API_KEY`.
`.env`는 gitignore다. **키를 채팅·카톡·디스코드로 주고받지 않는다.** 조직 계정이므로 **각자 본인 것을 발급**한다.
`*_demo.*` 파일은 API 없이 돌려보는 모의 세트다. 실제 팀 파일과 섞이지 않는다.

## 크레딧은 돌아가면서 낸다

`--yes-spend`는 **개인 카드에서 나가는 돈**이다. 정산은 **팀원당 30만원**이라 개인 부담이 아니지만,
**한 사람이 계속 충전하면 그 사람 카드에만 쌓인다.** 다음 충전이 필요하면 **아직 안 낸 사람이 낸다.**

| 날짜 | 낸 사람 | 무엇 | 금액 |
|---|---|---|---|
| 2026-09-20 | 이종훈 | OpenAI $5 + Anthropic $5 (세금 포함) | **$11** |

충전했으면 **이 표에 한 줄 추가한다.** 영수증은 각자 보관한다.

**돌리기 전에 원가를 먼저 계산한다.** `model_catalog.json`의 단가 × `results.md`의 실측 토큰이면
소수점까지 나온다. 100문항 기준 대략 — `gpt-5.6-luna` 22원 · `gpt-5-nano` 6원 · `claude-haiku-4-5` 750원.
**모델마다 35배까지 차이 난다.** 비싼 쪽을 여러 번 돌리기 전에 한 번 더 생각한다.

## 규칙

- **모델마다 프롬프트를 손보지 않는다.** 고치는 순간 뭘 비교한 건지 알 수 없다
- **스키마도 손보지 않는다.** 단, 업체마다 받는 *표기*가 달라서 어댑터가 옮겨 적는 건 있다
  (`anthropic_adapter._to_anthropic_schema`). **허용되는 값 집합이 바뀌면 그건 옮겨 적기가 아니다.**
  Anthropic이 거부한 두 가지 — 최상위 `name`(OpenAI 래퍼 필드라 JSON Schema가 아니다),
  그리고 `type: ["string","null"] + enum에 null`(같은 뜻인 `anyOf` 표기로 바꾼다)
- **TTFT는 스트리밍으로 잰다.** 논스트리밍이면 총 소요를 잰 것이다
- `next_slot`은 **정답 하나가 아니라 `next_slot_ok` 허용 집합**으로 센다
- `value_1`은 완전 일치가 아니라 **자모 편집거리 ≤ 0.3**으로 센다
- 생성 금칙어는 별도 복사본 없이 **`../guidelines/8_금칙어.md` §0·§1·§2를 직접 읽는다**
- HTTP 429는 `Retry-After` 또는 지수 backoff로 재시도하고, 소진되면 실패 row를 남긴 뒤 계속한다
- 실제 결과는 `results.md` 아래에 누적하며 동일 결과 섹션은 중복 기록하지 않는다
- `raw/`는 gitignore. **픽스처와 결과는 커밋한다**
