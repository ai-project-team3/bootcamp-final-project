"""OpenAI voices, round 3 — the sage_simple_0320 recipe on every voice, and sage_0320 refined.

Why (eval/voice_pitch.py, 10-01): sage and coral both rose to a median F0 of 296 Hz — the
child range (≈250-400) — only on the pinned snapshot gpt-4o-mini-tts-2025-03-20 with the short
"playful, like talking with a young child" instruction. The same voices on the current model with
the same words stayed at ~210 Hz. So the snapshot + instruction is the cause, not the voice.

  py eval/bench_tts_openai_voices3.py  → eval/tts_out/openai_voices3/*.mp3
"""
import sys
from pathlib import Path

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "backend"))
from app.config import settings  # noqa: E402

MODEL = "gpt-4o-mini-tts-2025-03-20"
LINES = [
    "안녕! 나는 오또야. 오늘은 어디로 놀러 가 볼까?",
    "와, 문어가 먹물을 쏘았구나! 그다음엔 어떻게 됐어?",
    "바닷속은 조용하고 파랬어요. 반짝이는 해파리가 길을 밝혀 주었어요.",
]
SIMPLE = "Warm, friendly and playful. Speak naturally, like talking with a young child. Not too fast."
REFINE = {
    "simple": SIMPLE,
    "simple_take2": SIMPLE,     # same again — does it come out the same?
    "light": ("Bright, light and playful, like a cheerful young kid character. "
              "Speak naturally with small pauses. Not too fast."),
    "slower": SIMPLE + " Speak a little slower and softer, with gentle pauses.",
    "smile": ("Smiling while you speak. Light, bouncy and playful, like talking with a young child. "
              "Natural, not exaggerated. Not too fast."),
}
RUNS = [(v, "simple") for v in ("alloy", "ash", "ballad", "echo", "fable", "nova", "shimmer", "verse")]
RUNS += [("sage", k) for k in REFINE]
OUT = Path(__file__).parent / "tts_out" / "openai_voices3"
OUT.mkdir(parents=True, exist_ok=True)

for voice, style in RUNS:
    name = f"{voice}_0320_{style}.mp3"
    if (OUT / name).exists():
        continue
    parts = []
    for text in LINES:
        r = None
        for _ in (1, 2):
            try:
                r = httpx.post(f"{settings.openai_base_url}/audio/speech", timeout=40,
                               headers={"Authorization": f"Bearer {settings.openai_api_key}"},
                               json={"model": MODEL, "voice": voice, "input": text,
                                     "instructions": REFINE.get(style, SIMPLE), "response_format": "mp3"})
                break
            except httpx.HTTPError as e:
                print(f"  {name}: {type(e).__name__}, retrying")
        if r is None or r.status_code != 200:
            print(f"{name}: HTTP {getattr(r, 'status_code', '-')} {getattr(r, 'text', '')[:100]}"); break
        parts.append(r.content)
    else:
        (OUT / name).write_bytes(b"".join(parts))
        print(name)
