# 3. API 명세 🔒

**전부 동결입니다.** 여기 없는 엔드포인트를 새로 만들지 않습니다.
`main.py`에 자기 라우터의 `import` / `include_router` 를 더하는 것만 예외입니다.

**공통 에러 형식** — 모든 에러가 이 모양이어야 합니다.

```json
{ "error": true, "message": "사람이 읽는 설명" }
```

**상태코드** — 상황별로 이 표를 따른다. 다섯 명이 각자 정하면 같은 상황에 다른 번호가 나간다.

| 상황 | 코드 |
|---|--:|
| 정상 조회 · 처리 | 200 |
| 생성 성공 (POST로 리소스 생성) | 201 |
| 요청 스키마 검증 실패 (필수 필드 누락 · 타입 불일치) | **422** — FastAPI/Pydantic 기본 동작 그대로 |
| 그 밖의 요청 형식 오류 (잘못된 JSON 등) | 400 |
| 없는 리소스 조회 (없는 `deal_id` · `meeting_id` · `item_id`) | 404 |
| 템플릿 로드 검증 실패 (2층 12개 초과 등) | 422 |
| 이미 연결된 미팅에 다시 `link` · 종료된 미팅에 재종료 | 409 |
| `joinToken`이 없거나 틀림 (백채널) | 403 |
| 서버 내부 오류 · LLM 호출 실패 · STT 제공자 장애 | 500 |

**401은 쓰지 않는다.** 로그인이 없으므로 인증 실패라는 상태가 존재하지 않는다.

`main.py`가 전역 예외 핸들러 넷을 갖는다 — 어느 경로로 빠져나가든 위 몸통이 나오게 한다.

| 핸들러 | 하는 일 |
|---|---|
| `HTTPException` | `{"error": true, "message": exc.detail}` |
| `RequestValidationError` (422) | Pydantic 상세 리스트를 `message` 한 줄로 요약 |
| `WebSocketException` | 소켓은 바디를 못 실으므로 **close code + `reason`** 으로 대체 |
| 나머지 `Exception` 전부 | 500 + `{"error": true, "message": "서버 내부 오류가 발생했습니다"}` |

🔴 **마지막 catch-all이 없으면** FastAPI 기본 500이 텍스트(`Internal Server Error`)로 새서
공통 형식이 깨진다. 프론트가 `message`를 읽다가 같이 터진다.

라우터가 날것의 예외를 그대로 흘리지 않는다.

**인증** — MVP에는 로그인이 없다. 개인용 단일 사용자 도구이므로 사용자 인증 계층을 만들지 않는다.
백채널 동석자만 `joinToken`으로 접근을 제한한다.

---

## 3-1. 딜 — 역할 5

### `GET /deal`

딜 목록. 화면 ①과 홈이 같이 쓴다.

| 쿼리 | 기본값 | 설명 |
|---|---|---|
| `q` | — | 건명 · 회사 · 상대를 **한 번에** 훑는다 |
| `sort` | `recent` | `recent` \| `open` \| `seq` |
| `order` | `desc` | `asc` \| `desc` |
| `status` | — | `blocked` \| `partial` \| `clear` (홈 전용, W5) |
| `limit` | `50` | |

```json
{
  "deals": [{
    "id": "d_01", "label": "A사 신규 도입",
    "status": "blocked", "seq": 2, "counterpart": "박부장",
    "open_count": 2, "last_meeting_at": "2026-09-03T14:00:00"
  }],
  "total": 12
}
```

`status`는 그 딜의 마지막 미팅 최종 상태에서 파생한다 — 🔴 하나라도 있으면 `blocked`.

### `POST /deal`

```json
{ "label": "A사 신규 도입", "template_id": "insurance_consult",
  "counterpart": "박부장", "company": "A사" }
```

### `PATCH /deal/{deal_id}` · `DELETE /deal/{deal_id}`

---

## 3-2. 미팅 · 실시간 — 역할 1

### `POST /meeting`

미팅을 시작한다. `deal_id`는 **선택**이다 — 없으면 1차처럼 동작한다.

```json
{ "deal_id": "d_01", "template_id": "insurance_consult",
  "recording_notice": true }
```

```json
{ "meeting_id": "m_07", "session_id": "s_a1b2", "seq": 2,
  "room_code": "482913",
  "preload": [ { "item_id": "budget_approval", "state": "raised",
                 "carried": true, "streak": 2, "priority": 10 } ] }
```

**`preload`가 프리로드 규칙(§5-2)의 결과다.** 순서가 여기서 확정되고 미팅 중에 바뀌지 않는다.

### `POST /meeting/{meeting_id}/link`

사후 연결. `seq`가 재계산되고 리포트가 갱신된다.

```json
{ "deal_id": "d_01" }
```

### `DELETE /meeting/{meeting_id}/link`

`unlink`. 잘못 이었을 때 되돌린다. **MVP 필수.**

### `POST /meeting/{meeting_id}/end`

```json
{ "unmentioned_required": ["contract_period", "exclusion_clause"],
  "mentioned_pending_confirm": ["cancellation_refund_rate", "exemption_period"] }
```

미완료 필수 항목이 있어도 **종료를 막지 않는다.** 경고만 하고 리포트 상단에 고정한다. (`#11`)

### `WS /ws/meeting/{meeting_id}?session_id={sid}`

**단일 채널이다.** 전사·상태·카드·백채널이 한 소켓을 탄다 — 소켓 하나면 순서 보장이 공짜다.

**클라이언트 → 서버**

| `type` | 내용 |
|---|---|
| `audio` | PCM 청크 (base64). `channel: "me" \| "them"` |
| `manual_state` | 사용자 수동 상태 변경 |
| `confirm_checklist` | 필수 항목 확인 |
| `backchannel` | 동석자 신호. `joinToken` 필요 |

**서버 → 클라이언트**

| `type` | 내용 |
|---|---|
| `utterance` | `Utterance` |
| `state_change` | `StateChange` |
| `checklist` | `ChecklistState` |
| `cards` | `Card[]` — 매 턴 통째로 교체 |
| `backchannel` | `BackchannelSignal` |
| `reconnected` | 재접속 후 최신 스냅샷 전체 |

**재접속** — 같은 `session_id`로 다시 붙으면 서버가 최신 스냅샷을 통째로 보낸다.
서버가 authoritative source다. 클라이언트는 화면에 `연결 끊김 → 재연결 중 → 복구 완료`를
표시해 **누락 구간이 없는 것처럼 보이지 않게** 한다. (`#12`)

### `POST /room/join`

```json
{ "room_code": "482913" }
```
```json
{ "join_token": "jt_...", "meeting_id": "m_07", "expires_in": 3600 }
```

🔒 **동석자에게 원본 음성을 보내지 않는다.** 상태와 신호만 간다.

---

## 3-3. 템플릿 — 역할 5

### `GET /template` · `GET /template/{template_id}`

### `GET /catalog` · `PUT /catalog/{item_id}`

저작 도구가 쓴다. `PUT`은 YAML 파일을 다시 쓰고 검증을 돌린다.
**검증 실패하면 저장하지 않는다.**

```json
{ "error": true, "message": "2층 항목이 13개입니다. 12개 이하로 합쳐 주세요." }
```

---

## 3-4. 리포트 — 역할 4

### `GET /report/{meeting_id}`

```json
{
  "meeting_id": "m_07", "seq": 2, "duration_sec": 2820,
  "kind": "change",
  "unresolved": [
    { "item_id": "budget_approval", "label": "예산 승인",
      "history": [{"seq":1,"final":"raised"},{"seq":2,"final":"raised"}],
      "streak": 2 }
  ],
  "recurrences": [
    { "item_id": "budget_approval", "at": 1880.0,
      "from_st": "resolved", "to_st": "raised",
      "trigger": "근데 위에서 좀…" }
  ],
  "checklist": { "mentioned": 7, "confirmed": 5, "unmentioned": ["contract_period"] },
  "off_list": [{ "t": 1520.0, "raw": "주차는 어디에…" }],
  "backchannel": { "signals": 3, "reflected": 2 },
  "summary": "…"
}
```

**`kind`는 차수에 따라 달라진다** — `discovery`(1차) · `change`(2차) · `pattern`(3차+).

### `POST /report/{meeting_id}/confirm-checklist`

미팅 종료 화면에서 사람이 확인한 결과.

```json
{ "confirmed": ["cancellation_refund_rate"], "rejected": ["exemption_period"] }
```

---

## 3-5. 평가 — 역할 3

### `POST /eval/run`

fixture를 읽어 지표를 낸다. **개발용이며 배포 대상이 아니다.**

```json
{ "fixture_ids": ["f_001", "f_002"] }
```
```json
{ "match_f1": 0.81, "state_accuracy": 0.87,
  "recurrence": { "recall": 0.82, "precision": 0.64, "f1": 0.72, "n_events": 40 },
  "checklist_accuracy": 0.91,
  "latency_p95": { "state": 2.4, "card": 4.1 } }
```

**재발은 사건 단위로 센다.** `trigger`가 `carryover`·`manual`인 변화는 제외한다.
