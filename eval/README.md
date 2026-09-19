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

## 규칙

- **모델마다 프롬프트를 손보지 않는다.** 고치는 순간 뭘 비교한 건지 알 수 없다
- **TTFT는 스트리밍으로 잰다.** 논스트리밍이면 총 소요를 잰 것이다
- `next_slot`은 **정답 하나가 아니라 `next_slot_ok` 허용 집합**으로 센다
- `value_1`은 완전 일치가 아니라 **자모 편집거리 ≤ 0.3**으로 센다
- `raw/`는 gitignore. **픽스처와 결과는 커밋한다**
