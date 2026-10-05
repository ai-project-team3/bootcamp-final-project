# -*- coding: utf-8 -*-
"""How soon could the first sound play if /tts streamed? (10-05 · turn-time goal)

The 10-05 device trace: every live line waited 1.1~2.7 s for /tts, which hands the phone a finished mp3
(whole OpenAI answer, then loudness levelling). Streaming would start playback at the first bytes. Same
model, voice and instructions as backend/app/routers/tts.py; first byte vs whole answer, several lines.

    py eval/bench_tts_stream.py [--runs 3]
"""
from __future__ import annotations

import argparse
import asyncio
import statistics
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "backend"))
import httpx  # noqa: E402
from app.config import settings  # noqa: E402

# lines from the 10-05 trace: acks and questions as the mascot said them
LINES = [
    "풍선마을로 가는구나!",
    "풍선마을에서 어떤 일이 생길까?",
    "바늘괴물이 풍선을 마구 터뜨렸구나.",
    "바늘괴물은 왜 풍선을 터뜨렸을까?",
    "또치를 아저씨가 동물원으로 데리고 돌아갔구나.",
]


async def one(http: httpx.AsyncClient, text: str) -> tuple[float, float, int]:
    body = {"model": settings.openai_tts_model, "voice": settings.openai_tts_voice, "input": text,
            "instructions": settings.openai_tts_instructions, "response_format": "mp3"}
    t0 = time.monotonic()
    first = None
    n = 0
    async with http.stream("POST", f"{settings.openai_base_url.rstrip('/')}/audio/speech", json=body,
                           headers={"Authorization": f"Bearer {settings.openai_api_key}"}) as r:
        r.raise_for_status()
        async for chunk in r.aiter_bytes():
            if first is None and chunk:
                first = time.monotonic() - t0
            n += len(chunk)
    return first or 0.0, time.monotonic() - t0, n


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--runs", type=int, default=3)
    a = ap.parse_args()
    firsts, totals = [], []
    async with httpx.AsyncClient(timeout=30) as http:
        for _ in range(a.runs):
            for text in LINES:
                f, t, n = await one(http, text)
                firsts.append(f); totals.append(t)
                print(f"  first {f:4.2f}s · whole {t:4.2f}s · {n // 1024} KB · {text}")
    print(f"{settings.openai_tts_model} · {settings.openai_tts_voice}: first byte p50 {statistics.median(firsts):.2f}s · "
          f"whole p50 {statistics.median(totals):.2f}s · n={len(firsts)}")


if __name__ == "__main__":
    asyncio.run(main())
