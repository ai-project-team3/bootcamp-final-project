"""OpenAI voices side by side for the mascot — 10-01, while TypeCast is blocked.

  py eval/bench_tts_openai_voices.py   → eval/tts_out/openai_voices/<voice>.mp3 (three lines back to back)

Same three lines and the same instruction as the server (settings.openai_tts_instructions),
only the voice changes, so the ear compares voices and nothing else. Key from the repo .env.
"""
import sys
import time
from pathlib import Path

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "backend"))
from app.config import settings  # noqa: E402

VOICES = ["coral", "nova", "shimmer", "sage", "fable", "ballad", "alloy", "verse", "marin"]
LINES = [
    "안녕! 나는 오또야. 오늘은 어디로 놀러 가 볼까?",
    "와, 문어가 먹물을 쏘았구나! 그다음엔 어떻게 됐어?",
    "바닷속은 조용하고 파랬어요. 반짝이는 해파리가 길을 밝혀 주었어요.",
]
OUT = Path(__file__).parent / "tts_out" / "openai_voices"
OUT.mkdir(parents=True, exist_ok=True)

for v in VOICES:
    parts = []
    if (OUT / f"{v}.mp3").exists():
        print(f"{v}.mp3  (already made)"); continue
    for text in LINES:
        r = None
        for attempt in (1, 2):           # OpenAI speech sometimes stalls past 30 s (10-01 · nova)
            t = time.monotonic()
            try:
                r = httpx.post(f"{settings.openai_base_url}/audio/speech", timeout=40,
                               headers={"Authorization": f"Bearer {settings.openai_api_key}"},
                               json={"model": settings.openai_tts_model, "voice": v, "input": text,
                                     "instructions": settings.openai_tts_instructions, "response_format": "mp3"})
                print(f"  {v} line {len(parts) + 1}: {time.monotonic() - t:.1f}s HTTP {r.status_code}")
                break
            except httpx.HTTPError as e:
                print(f"  {v} line {len(parts) + 1}: {type(e).__name__} after {time.monotonic() - t:.0f}s (try {attempt})")
        if r is None or r.status_code != 200:
            break
        parts.append(r.content)
    else:
        (OUT / f"{v}.mp3").write_bytes(b"".join(parts))     # mp3 frames play back to back
        print(f"{v}.mp3  {sum(map(len, parts)) // 1024} KB")
