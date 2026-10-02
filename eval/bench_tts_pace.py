"""Does dropping "Not too fast" make the mascot quicker — to hear and to make? (10-01)

The team felt the OpenAI voice about 3× slower than TypeCast. Two parts: time to make the audio
(the server waits for the whole file) and how long it talks. Same voice, model and lines as the
server; only the instruction's pace words change. Ear test: eval/tts_out/openai_pace/*.mp3.

  py eval/bench_tts_pace.py
"""
import io
import sys
import time
from pathlib import Path

import av
import httpx

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "backend"))
from app.config import settings  # noqa: E402

LINES = [
    "와, 문어가 먹물을 쏘았구나! 그다음엔 어떻게 됐어?",
    "바닷속에서 반짝이는 친구를 만났구나. 누구였어?",
    "동화책을 만들고 있어! 조금만 기다려 줘.",
]
BASE = "Warm, friendly and playful. Speak naturally, like talking with a young child."
STYLES = {
    "now": BASE + " Not too fast.",                       # the server's setting
    "no_pace": BASE,
    "lively": BASE + " Keep a lively, brisk pace, with short pauses.",
}
OUT = Path(__file__).parent / "tts_out" / "openai_pace"
OUT.mkdir(parents=True, exist_ok=True)

print(f"{'style':<9}{'make p50':>9}{'talk total':>11}  (3 lines)")
for name, instr in STYLES.items():
    makes, talk, parts = [], 0.0, []
    for text in LINES:
        t = time.monotonic()
        r = httpx.post(f"{settings.openai_base_url.rstrip('/')}/audio/speech", timeout=60,
                       headers={"Authorization": f"Bearer {settings.openai_api_key}"},
                       json={"model": settings.openai_tts_model, "voice": settings.openai_tts_voice,
                             "input": text, "instructions": instr, "response_format": "mp3"})
        makes.append(time.monotonic() - t)
        r.raise_for_status()
        talk += float(av.open(io.BytesIO(r.content)).duration / 1e6)
        parts.append(r.content)
    (OUT / f"sage_{name}.mp3").write_bytes(b"".join(parts))
    print(f"{name:<9}{sorted(makes)[1]:>8.2f}s{talk:>10.1f}s")
