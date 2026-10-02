"""What one session costs — the unit price #30 needs before a call cap can get a number (10-02).

Runs the server's own code (not a copy) against the real vendors, and adds up what the vendors
report, not an estimate:
  /turn   the 100 judge fixtures as 100 turns: judge + mascot line, tokens from the Responses API usage
  /tts    the mascot lines those turns produced, spoken through /audio/speech with SSE so the
          answer carries its token usage (text in · audio out)
  /story  the 3 story fixtures, effort high

  py eval/cost_session.py [turns=100] [tts_lines=30]

Prices are read from eval/model_catalog.json (gpt-6-luna) and TTS_PRICE below (developers.openai.com
pricing, read 10-02). KRW at 1,400 like the rest of results.md. Fixtures are written by us — no child data.
Images are left out: drawn on our own GPU, checked by omni-moderation (no charge).
"""
import asyncio
import json
import statistics
import sys
import time
from pathlib import Path

import httpx

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "backend"))
from app.config import settings                     # noqa: E402
from app.llm import client                          # noqa: E402
from app.routers import story as story_route        # noqa: E402
from app.routers import turn as turn_route          # noqa: E402
from app.schemas.story import StoryRequest          # noqa: E402
from app.schemas.turn import TurnRequest            # noqa: E402

KRW = 1400
TTS_PRICE = {"in": 0.60, "out": 12.00}             # USD per 1M tokens · gpt-4o-mini-tts (text in · audio out)
EVAL = ROOT / "eval"


def llm_price() -> dict:
    m = json.loads((EVAL / "model_catalog.json").read_text(encoding="utf-8"))["models"][settings.llm_model]
    return {"in": m["input_usd_per_mtok"], "out": m["output_usd_per_mtok"]}


def won(tokens_in: float, tokens_out: float, price: dict) -> float:
    return (tokens_in * price["in"] + tokens_out * price["out"]) / 1e6 * KRW


async def speak_usage(text: str) -> tuple[int, int, float]:
    """(text tokens in, audio tokens out, seconds of audio) for one line, as the server would speak it."""
    body = {"model": settings.openai_tts_model, "voice": settings.openai_tts_voice, "input": text,
            "instructions": settings.openai_tts_instructions, "response_format": "pcm", "stream_format": "sse"}
    tin = tout = 0
    pcm_bytes = 0
    async with httpx.AsyncClient(timeout=60) as http:
        async with http.stream("POST", f"{settings.openai_base_url.rstrip('/')}/audio/speech", json=body,
                               headers={"Authorization": f"Bearer {settings.openai_api_key}"}) as r:
            r.raise_for_status()
            async for line in r.aiter_lines():
                if not line.startswith("data:") or not line[5:].strip().startswith("{"):
                    continue          # "data: [DONE]"
                ev = json.loads(line[5:])
                if ev.get("type") == "speech.audio.delta":
                    pcm_bytes += len(ev.get("audio", "")) * 3 // 4
                elif ev.get("type") == "speech.audio.done":
                    u = ev.get("usage") or {}
                    tin, tout = int(u.get("input_tokens") or 0), int(u.get("output_tokens") or 0)
    return tin, tout, pcm_bytes / 2 / 24000          # pcm = 16-bit mono 24 kHz


async def main(n_turns: int, n_tts: int) -> None:
    settings.mock = False
    calls: list[tuple[str, int, int]] = []
    client.on_usage = lambda name, i, o: calls.append((name, i, o))

    fx = [json.loads(l) for l in (EVAL / "fixtures_judge.jsonl").read_text(encoding="utf-8").splitlines() if l.strip()]
    lines: list[str] = []
    sem = asyncio.Semaphore(5)

    async def one(f: dict) -> None:
        req = TurnRequest(mode="story", slots=f["slots"], asked_slot=f.get("asked"), template=f.get("template"),
                          question=f.get("context") or "", utterance=f["utterance"])
        async with sem:
            out = await turn_route.turn(req)
        if out.line:
            lines.append(" ".join(x for x in (out.line.ack, out.line.expand, out.line.question) if x))

    t0 = time.monotonic()
    await asyncio.gather(*(one(f) for f in fx[:n_turns]), return_exceptions=True)
    judge = [(i, o) for n, i, o in calls if n == "judge"]
    line = [(i, o) for n, i, o in calls if n == "mascot_line"]
    print(f"/turn {len(fx[:n_turns])} turns · {time.monotonic() - t0:.0f}s · judge {len(judge)} calls · line {len(line)} calls")

    sp = []
    for text in lines[:n_tts]:
        sp.append((len(text), *await speak_usage(text)))

    calls.clear()
    stories = [json.loads(l) for l in (EVAL / "fixtures_story.jsonl").read_text(encoding="utf-8").splitlines() if l.strip()]
    for s in stories:
        await story_route.story(StoryRequest(mode="story", slots=s["slots"]))
    story = [(i, o) for n, i, o in calls]

    p = llm_price()
    mean = lambda xs: statistics.mean(xs) if xs else 0.0
    j_in, j_out = mean([i for i, _ in judge]), mean([o for _, o in judge])
    l_in, l_out = mean([i for i, _ in line]), mean([o for _, o in line])
    s_in, s_out = mean([i for i, _ in story]), mean([o for _, o in story])
    t_in, t_out = mean([x[1] for x in sp]), mean([x[2] for x in sp])
    chars, secs = mean([x[0] for x in sp]), mean([x[3] for x in sp])
    per_turn_llm = won(j_in, j_out, p) + won(l_in, l_out, p)
    per_line_tts = won(t_in, t_out, TTS_PRICE)
    per_story = won(s_in, s_out, p)

    print(f"\nmodel {settings.llm_model} · tts {settings.openai_tts_model} · 1 USD = {KRW} KRW")
    print(f"judge  in {j_in:,.0f} · out {j_out:,.0f} tokens → {won(j_in, j_out, p):.3f}원")
    print(f"line   in {l_in:,.0f} · out {l_out:,.0f} tokens → {won(l_in, l_out, p):.3f}원")
    print(f"/turn  1 turn → {per_turn_llm:.3f}원")
    print(f"/tts   {len(sp)} lines · {chars:.0f} chars · {secs:.1f} s audio · in {t_in:,.0f} · out {t_out:,.0f} tokens → {per_line_tts:.3f}원 per line")
    print(f"/story {len(story)} books · in {s_in:,.0f} · out {s_out:,.0f} tokens → {per_story:.3f}원 per book")
    print(f"\nper turn (turn + one spoken line) = {per_turn_llm + per_line_tts:.3f}원")
    for turns in (16, 40, 80):
        total = turns * (per_turn_llm + per_line_tts) + per_story
        print(f"a session of {turns} turns + one book = {total:.1f}원")


if __name__ == "__main__":
    asyncio.run(main(int(sys.argv[1]) if len(sys.argv) > 1 else 100, int(sys.argv[2]) if len(sys.argv) > 2 else 30))
