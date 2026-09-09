# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Language policy

- **Replies to the user: Korean.** Explanations, summaries, questions, status updates.
- **Code and machine-facing artifacts: English.** Code, comments, commit messages, PR titles/bodies, log messages, test names.
- **Documents people read: Korean.** `docs/`, `guidelines/`, `gaps/`, READMEs, retrospectives.
- **Product text stays Korean** — UI copy, mock data, API error messages returned to clients.
- Keep identifiers, file paths, command names and flags in their original form inside Korean sentences.

## Repository state

Only documents exist so far — `guidelines/0`–`6` (frozen specs), `docs/` (product plan), `gaps/`
(the 17 decision records those specs were derived from). **No `backend/` or `frontend/` code yet.**
The folder layout in `guidelines/5_기술스택_폴더구조.md` is the contract for what gets created;
follow it exactly rather than inventing an alternative structure.

Five people build this in parallel on `feature/role{1..5}-*` branches merging into `main`.
Check `git log` / `git branch -a` for what has landed before assuming a file's current content.

## What this project is

**영업 어시스턴트 AI** — a real-time sales-meeting assistant. It tracks, per item, what the
customer is still stuck on (**반대사유**), detects when a resolved item **relapses** mid-meeting
(재발), and carries item state across meeting rounds (차수) so the 2nd and 3rd meeting reports
say things a single-meeting tool cannot.

Two differentiators, and only two:

1. **재발 탐지** — catching the moment something resolved gets blocked again, within one meeting
2. **차수별 누적** — stitching meetings together instead of leaving them as separate documents

## Source-of-truth documents (read before implementing)

- `guidelines/0_목적_사용법.md` — how to use these docs; **`2` (data model), `3` (API), `5`
  (folder/stack) are frozen and may only be changed by 조장.** Do not improvise around them.
- `guidelines/1_시스템_개요.md` — screens, module ownership, and **§1-5's nine cross-cutting
  rules**. Those nine are the ones that break other people when violated. Read them before
  touching anything.
- `guidelines/2_공통_데이터_모델.md` — exact Pydantic models (`Utterance`, `Item`, `Slot`,
  `Template`, `StateChange`, `ChecklistState`, `Deal`, `Meeting`, `Card`, `BackchannelSignal`,
  `TimingMark`) plus the state transition table. Every module exchanges data using these; do not
  rename or add fields.
- `guidelines/3_API_명세.md` — exact endpoints, the common error shape
  `{"error": true, "message": "..."}`, and the single WebSocket channel contract.
- `guidelines/4_프롬프트_브리프.md` — per-role briefs. §4-3 (상태 추적) is the most detailed
  spec for relapse behaviour and evaluation.
- `guidelines/5_기술스택_폴더구조.md` — stack, frozen folder layout, env var names, git branch
  strategy, deployment (Cloudflare Pages + Railway), code-freeze dates.
- `guidelines/6_통합_체크포인트.md` — schedule, the 14-step E2E scenario that must pass, §6-3's
  three W1 blockers, §6-5's early-abort triggers, and §6-7's explicit "not in 1차" list.
- `gaps/*.md` — the 17 decision records. When a spec looks arbitrary, the reasoning is here.

## Architecture (once implemented, per the frozen spec)

**Stack**: FastAPI + SQLAlchemy backend, MySQL, React + Vite frontend, a **single** FastAPI
WebSocket channel carrying transcript / state / cards / backchannel together (one socket gives
ordering for free). `claude-opus-5` for card sentences and report prose. STT provider and
embedding model are **not chosen yet** — W1 spikes decide them; only the interfaces are frozen.

**The pipeline is one direction:**

```
audio → SpeakerSource → Utterance → matching(역할 2) → state(역할 3) → cards/report(역할 4)
                                          ↑ template(역할 5) ↑ carryover(역할 5)
```

**`SpeakerSource` is the seam.** `DualChannelSource` (video calls, two physically separate audio
channels, `confidence` always `1.0`) is the only MVP implementation. `FixtureSource` replays
labelled transcripts for demos and evaluation. `EnrolledVoiceSource` (in-person, speaker
embedding) is W4-conditional. **Everything downstream sees only `Utterance`,** so adding a source
later breaks nothing.

**State is an event log, not a field.** `StateChange` rows are the record; current state is the
last row's `to_st`, and a relapse is any row where `RANK[to_st] < RANK[from_st]`. Never store a
"current state" column — deriving it from the log is what makes the timeline, the cross-round
history, and the relapse metric all fall out of one structure.

**Two layers, two state models.** 1층 (반대사유) has 4 states plus relapse and is driven by slot
fulfilment. 2층 (필수 항목) is a checklist — 2 states, monotonically increasing, no relapse, no
slots. They are separate models and separate columns on screen ②; do not merge them.

## Conventions specific to this repo

- **Field names and types in `schemas/` are frozen by spec** — do not rename or restructure them
  even if a different name reads better.
- **Do not add API endpoints** beyond `guidelines/3_API_명세.md` without flagging it to 조장; the
  one standing exception is adding your own router's `import` / `include_router` lines in `main.py`.
- **`routers/` files are individually owned.** Only touch your own. Conflicts in `schemas/`,
  `main.py` and `theme.css` are resolved by 조장; conflicts inside one owner's folder by that owner.
- **`backend/app/templates/` and `fixtures/` are data, not code.** YAML templates are hand-written
  by 조장 and reviewed in PRs; keep them readable.
- `schemas/` (API shape) and `models/` (DB shape) both stay. Don't collapse them into one.
- Item `id` is English snake_case; `label` is Korean. The id is a machine identifier used across
  fixture JSON, DB rows and module boundaries.

## The nine rules that break other people

These are `guidelines/1_시스템_개요.md` §1-5. Violating one silently breaks a teammate.

1. **Items are selected, never generated.** The LLM does not invent items. Off-list utterances go
   to the single reserved `_other` slot as raw text, unclassified.
2. **A card is not an item.** Cards are regenerated and discarded every turn. **Candidates come
   from state via rules; the LLM writes only the final sentence.**
3. **Required items are "mentioned", not "completed".** Auto-check is allowed but the label must
   stay 언급. Labelling it 완료 lets a false positive reassure the user into a statutory disclosure
   failure. Final confirmation happens on the meeting-end screen, by a human.
4. **Relapse IS auto-confirmed** — the opposite direction from rule 3. A false positive there says
   "look at this again", which creates no risk. It is reversible by tap and the reversal is logged.
5. **Signal-light order never changes mid-meeting.** Preload fixes the order; only colour, sticker
   and evidence line update. Priority is the cards' job.
6. **Utterances with `speaker != "them"` or sub-threshold `confidence` cause no transition.**
7. **Never send raw audio to the backchannel.** Supreme Court 2023도8603 defined '청취' as
   listening in real time during the conversation. Co-attendees receive state and signals only.
   §2-4's backchannel never needed audio, so this costs no functionality.
8. **Never auto-match deals.** No name or company string matching. Mis-linking surfaces another
   customer's unresolved items on screen; `unlink` is mandatory.
9. **Carryover keeps the previous round's colour.** Round 2 starts at round 1's final state, not
   ⚪ — the item is not "unraised", it is *still blocked*. Recorded as a `trigger="carryover"`
   change and excluded from every metric.

## Metrics — each role owns one number

`trigger` values `carryover` and `manual` are excluded from **all** metrics.

| Role | Metric |
|---|---|
| 1 음성·실시간 | Korean WER · `utterance_end → state_rendered` p95 (target 3s) · session drop rate |
| 2 항목 매칭 | matching F1 · `_other` misclassification rate |
| 3 상태 추적 | state accuracy · **within-meeting relapse recall (target ≥ 0.80)** |
| 4 생성 | card adoption rate · report evidence-citation rate |
| 5 템플릿·누적 | carryover accuracy · template coverage |

**Relapse recall is the project's only technical differentiator.** It is measured as *event*
detection, not per-timestep accuracy — a rare class would otherwise vanish into overall accuracy.

## Commands (once the skeleton exists)

- Backend: `uvicorn main:app --reload` from `backend/`, fixed port `8000`
- Frontend: `npm run dev` from `frontend/`
- Eval: `python -m app.eval.run --fixtures fixtures/`
- Env vars: see `guidelines/5_기술스택_폴더구조.md` §5-3. Real values go in `.env` (gitignored);
  only `.env.example` is committed. `CONFIDENCE_THRESHOLD` (0.7) and `RECURRENCE_COOLDOWN_SEC`
  (30) are tuning targets confirmed against fixtures in W3, not settled constants.
- No test or lint config exists yet — check `requirements.txt` / `package.json` once they are
  added rather than assuming a framework.

## Before implementing these, check with 조장

`guidelines/6_통합_체크포인트.md` §6-7 lists what is deliberately out of 1차 scope: user login,
CRM/calendar integration, in-person real-time audio, the home deal list with filters, a second
template, storing raw audio, and any manager-facing dashboard. **Backchannel audio transmission is
not deferred — it is permanently prohibited.**
