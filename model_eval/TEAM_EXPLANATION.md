# 팀원 공유용 — 최민우 러너/채점기 최신 상태

## 한 줄 설명

최신 판정 구조에 맞춰 **같은 입력을 여러 후보 모델에 넣고 JSON 안정성·판정 성능·TTFT·토큰/원가를 같은 기준으로 비교하는 러너/채점기**를 준비했습니다.

현재 실제 팀 자료/API 키를 기다리는 부분은 자리만 만들어 두었고, 그 외 코드는 mock으로 실행 검증했습니다.

## 이번 변경 반영

기존의 `slot_match / contradicts / s1 / s2` 중심 채점에서 최신 설계로 변경했습니다.

현재 판정 출력은 다음 구조를 전제로 합니다.

```text
reason
slot_1 / value_1
slot_2 / value_2
contradiction / contradiction_with
s1_reason / s2_addition
emotion
unclear / unclear_of
next_slot / next_reason
no_longer_needed
story_ready
```

특히 이번 설계 변경의 핵심인:

- 한 발화에서 두 슬롯 채우기 (`slot_2`)
- 필요 없어진 필드 해제 (`no_longer_needed`)
- 다음 질문을 LLM이 고르기 (`next_slot`)

를 채점할 수 있게 바꿨습니다.

## 현재 필요한 팀 자료

### 박진웅

```text
eval/judge_prompt.md
eval/judge_schema.json
판정 100개 gold 라벨
```

생성 쪽은 이후:

```text
eval/story_prompt.md
eval/story_schema.json
금지어 목록
```

### 안치영

```text
eval/fixtures_judge.jsonl   # 100개
eval/fixtures_story.jsonl   # 3벌
```

판정 100개는 우선 다음 필드가 있으면 예측 수집 가능합니다.

```text
id / slots / asked / template / utterance
```

Gold는 없어도 됩니다.

### 조장

```text
OPENAI_API_KEY
MISTRAL_API_KEY
ANTHROPIC_API_KEY(실행 환경에 기존 키가 있으면 그대로)
```

## 자료가 늦게 와도 작업 순서가 안 막히는 방식

안치영의 라벨 없는 100개 + 박진웅 최종 프롬프트/스키마 + API 키가 먼저 오면 모델 예측을 수집합니다.

박진웅 gold가 나중에 오면 API를 다시 호출하지 않고:

```bash
python score_existing.py
```

로 기존 raw JSONL만 재채점합니다.

## 최신 채점 지표

```text
JSON 성공률
slot_1 macro Precision / Recall / F1
slot_2(다중채움) macro Precision / Recall / F1
no_longer_needed(필수해제) macro Precision / Recall / F1
s1_reason Precision / Recall / F1
s2_addition Precision / Recall / F1
next_slot 허용 집합 적중률
value_1 자모 편집거리 기반 보조 정확도
TTFT p50 / p95
전체 응답시간
input/output token
한 이야기 16회 판정 기준 비용
```

`next_slot`은 단일 정답이 아니라 gold의 `next_slot_ok` 목록 안에 들어가면 정답으로 계산합니다.

`value_1`은 자유 서술이라 완전 일치가 아니라 자모 편집거리 정규화 값이 0.30 이하이면 맞은 것으로 계산합니다.

## 러너 입력

Gold는 모델에 보내지 않습니다.
모델에는:

```text
현재 slots 전체
asked
template
utterance
```

만 전달합니다.

## 실패 처리

JSON 파싱 또는 스키마 검증이 실패하면 전체 100개 실행을 중단하지 않습니다.

해당 항목에:

```text
ok:false
error
raw
TTFT/total/token(가능한 범위)
```

을 남기고 다음 항목으로 진행합니다.

## TTFT

논스트리밍 총 응답시간이 아니라, 스트리밍에서 **첫 content token/delta가 들어온 시점**을 TTFT로 기록합니다.

## 생성 자동 채점

기존대로 다음 5개를 자동 검사합니다.

```text
-어요체
15어절 이하
금지 표현 0건
{주인공}/{친구1} 자리표시자 무결성
정확히 6장면
```

## 자모 방어 테스트

```text
seed 20260918
손상 0 / 10 / 25%
문자열 exact match 전
자모 편집거리 match 후
```

을 비교합니다.

## 현재 상태

`python run_demo.py`는 실제 API 0건으로 최신 구조 전체를 통과했습니다.

실제 팀 자료가 도착하면:

```bash
python preflight.py
python run_team_eval.py --yes-spend
```

순서로 진행하면 됩니다.

`--yes-spend`가 없으면 실제 유료 API는 호출되지 않습니다.

※ `results_demo.md`의 수치는 실제 Luna/Haiku/Mistral 성능이 아니라 mock 파이프라인 검증 결과입니다.
