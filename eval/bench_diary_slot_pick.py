# -*- coding: utf-8 -*-
"""Does the judge ever pick `companion` (who with) or `reaction` (how it felt) as the next diary slot? (10-05)

The diary app asks what the judge's `next_slot` names, in the line model's words
(PictureDiary.kt `serverNext`) — any of the 12 slots, not only its own four questions
(place · problem · solution · keep). Without a verdict it walks those four in order, so
who-with and how-it-felt are only ever asked when the judge picks them. Nobody had counted
how often it does.

Each case is one real D3 turn: the slots the phone holds, the question just asked, the child's
answer. The server's own judge and line calls run on it (`judge.run` · `turn.run_line`), so the
prompt, schema, guardrails and effort are exactly what the phone gets.

Counted per turn: the next slot the judge picked, and the line's question for it, to read by eye.
Every case is a real answer, so a verdict that fills nothing drops the child's words from the book
(only place · problem get them back — PictureDiary.kt keeps a required slot's answer after two misses).
Where the child names who was there (「할머니랑」), `companion` should be one of the fills.

    backend/.venv/Scripts/python eval/bench_diary_slot_pick.py --rounds 3 --yes-spend

Costs about 15 won (gpt-6-luna · 2 calls × 12 cases × 3 rounds).
"""
from __future__ import annotations

import argparse
import asyncio
import json
import sys
import time
from collections import Counter
from pathlib import Path

EVAL = Path(__file__).resolve().parent
sys.path.insert(0, str(EVAL.parent / "backend"))

from app.config import settings                 # noqa: E402
from app.llm.client import LLMError             # noqa: E402
from app.routers import judge, turn             # noqa: E402
from app.schemas.judge import SLOT_NAMES        # noqa: E402
from app.schemas.turn import TurnRequest        # noqa: E402

RAW = EVAL / "raw"
# the slots the diary has an app question for (PICTURE_QUESTIONS) — `extra` is 「내일」
APP_ASKS = {"place", "problem", "solution", "extra"}
WANTED = {"companion", "reaction"}
WHO_SAID = {"d05", "d06"}       # cases where the child names who was there

PLACE_Q = "오늘 어디 갔었어?"
EVENT_Q = "{place}에서 무슨 일이 있었어?"
END_Q = "그래서 어떻게 됐어?"

# (id, filled slots, asked slot, question, utterance, note)
CASES = [
    ("d01", {}, "place", PLACE_Q, "동물원 갔어", "place only"),
    ("d02", {"place": "놀이터"}, "problem", EVENT_Q, "미끄럼틀 탔어", "plain event"),
    ("d03", {"place": "바다"}, "problem", EVENT_Q, "모래성 만들었어", "plain event"),
    ("d04", {"place": "공원"}, "problem", EVENT_Q, "강아지 봤어", "plain event"),
    ("d05", {"place": "할머니 집"}, "problem", EVENT_Q, "할머니랑 송편 만들었어", "who-with said"),
    ("d06", {"place": "키즈카페"}, "problem", EVENT_Q, "{친구1}이랑 공놀이 했어", "who-with said"),
    ("d07", {"place": "유치원"}, "problem", EVENT_Q, "친구가 내 블록 무너뜨렸어", "trouble"),
    ("d08", {"place": "병원"}, "problem", EVENT_Q, "주사 맞았어", "feeling-heavy"),
    ("d09", {"place": "집"}, "problem", EVENT_Q, "동생이 울었어", "trouble"),
    ("d10", {"place": "놀이터", "problem": "그네 탔어"}, "solution", END_Q, "엄청 높이 올라갔어", "ending"),
    ("d11", {"place": "수영장", "problem": "물에 들어갔어"}, "solution", END_Q, "재밌었어", "feeling as ending"),
    ("d12", {"place": "마트", "problem": "과자 샀어"}, "solution", END_Q, "집에 와서 먹었어", "ending"),
]


def request(case: tuple) -> TurnRequest:
    _, filled, asked, q, said, _ = case
    slots = {k: None for k in SLOT_NAMES if k != "extra"} | filled
    question = q.replace("{place}", filled.get("place", ""))
    # the phone sends no template in diary (StoryTurn.kt exchangeTurn) and a level name
    return TurnRequest(mode="diary", slots=slots, asked_slot=asked, question=question,
                       utterance=said, turn=3, level="chain")


async def one(case: tuple) -> dict:
    req = request(case)
    t = time.monotonic()
    try:
        v = await judge.run(req)
    except LLMError as e:
        return {"id": case[0], "error": f"judge: {e}"}
    line = await turn.run_line(req, v, settings.turn_deadline_s - (time.monotonic() - t))
    return {
        "id": case[0], "note": case[5], "utterance": case[4],
        "fills": [(v.slot_1, v.value_1), (v.slot_2, v.value_2)],
        "next_slot": v.next_slot, "next_reason": v.next_reason, "story_ready": v.story_ready,
        "no_longer_needed": v.no_longer_needed, "emotion": v.emotion,
        "question": line.question if line else None, "ack": line.ack if line else None,
        "s": round(time.monotonic() - t, 2),
    }


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--rounds", type=int, default=3)
    ap.add_argument("--yes-spend", action="store_true")
    a = ap.parse_args()
    if not a.yes_spend:
        sys.exit(f"{len(CASES) * a.rounds * 2} paid calls — pass --yes-spend")

    rows = []
    for r in range(a.rounds):
        for case in CASES:
            row = await one(case) | {"round": r}
            rows.append(row)
            if "error" in row:
                print(f"[{row['id']}] ! {row['error']}")
                continue
            print(f"[{row['id']}] r{r} {row['utterance']!r:<22} fill={[f for f in row['fills'] if f[0]]} "
                  f"→ next={row['next_slot']} ready={row['story_ready']} q={row['question']!r}")

    RAW.mkdir(exist_ok=True)
    out = RAW / f"diary_slot_pick_{time.strftime('%m%d_%H%M')}.jsonl"
    out.write_text("\n".join(json.dumps(x, ensure_ascii=False) for x in rows) + "\n", encoding="utf-8")

    ok = [x for x in rows if "error" not in x]
    picks = Counter(x["next_slot"] or "(none)" for x in ok)
    print(f"\nnext_slot over {len(ok)} turns ({len(rows) - len(ok)} failed): "
          + " · ".join(f"{k} {n}" for k, n in picks.most_common()))
    wanted = sum(n for k, n in picks.items() if k in WANTED)
    off_app = sum(n for k, n in picks.items() if k not in APP_ASKS | WANTED | {"(none)"})
    print(f"companion/reaction picked {wanted}/{len(ok)} · other slots the app has no question for {off_app}/{len(ok)}")
    dropped = [x for x in ok if not any(f[0] for f in x["fills"])]
    said_who = [x for x in ok if x["id"] in WHO_SAID]
    who_kept = [x for x in said_who if any(f[0] == "companion" for f in x["fills"])]
    print(f"answer dropped (nothing filled) {len(dropped)}/{len(ok)} · who-with said and kept as companion {len(who_kept)}/{len(said_who)}")
    for cid in dict.fromkeys(x["id"] for x in ok):
        got = Counter(x["next_slot"] or "-" for x in ok if x["id"] == cid)
        print(f"  {cid}: " + " ".join(f"{k}×{n}" for k, n in got.items()))
    print(f"raw → {out.relative_to(EVAL.parent)}")


if __name__ == "__main__":
    asyncio.run(main())
