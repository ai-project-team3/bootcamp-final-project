"""OpenAI voices, round 2 — 10-01, after round 1 sounded machine-like to the 조장.

What changed, from what people report (community.openai.com · the OpenAI TTS guide):
- OpenAI recommends `marin` / `cedar` for best quality — cedar was missing in round 1
- piling traits (age + emotion + pace + …) makes it worse; asking an adult voice to be a
  six-year-old was the strongest suspect. Short, direct, English instructions instead
- some report the pinned snapshot gpt-4o-mini-tts-2025-03-20 follows instructions better

  py eval/bench_tts_openai_voices2.py  → eval/tts_out/openai_voices2/<voice>_<style>[_<model>].mp3
"""
import sys
import time
from pathlib import Path

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "backend"))
from app.config import settings  # noqa: E402

LINES = [
    "안녕! 나는 오또야. 오늘은 어디로 놀러 가 볼까?",
    "와, 문어가 먹물을 쏘았구나! 그다음엔 어떻게 됐어?",
    "바닷속은 조용하고 파랬어요. 반짝이는 해파리가 길을 밝혀 주었어요.",
]
STYLES = {
    "none": None,
    "simple": "Warm, friendly and playful. Speak naturally, like talking with a young child. Not too fast.",
    "fm": ("Voice Affect: gentle, cheerful storyteller for little kids.\n"
           "Tone: warm and playful.\n"
           "Pacing: relaxed, with natural pauses between phrases.\n"
           "Emotion: genuine delight and curiosity."),
}
RUNS = [(v, s, "gpt-4o-mini-tts") for v in ("marin", "cedar", "coral", "sage") for s in STYLES]
RUNS += [(v, "simple", "gpt-4o-mini-tts-2025-03-20") for v in ("coral", "sage")]   # marin/cedar are newer than it
OUT = Path(__file__).parent / "tts_out" / "openai_voices2"
OUT.mkdir(parents=True, exist_ok=True)

for voice, style, model in RUNS:
    name = f"{voice}_{style}" + ("" if model == "gpt-4o-mini-tts" else "_0320") + ".mp3"
    if (OUT / name).exists():
        continue
    parts = []
    for text in LINES:
        body = {"model": model, "voice": voice, "input": text, "response_format": "mp3"}
        if STYLES[style]:
            body["instructions"] = STYLES[style]
        r = None
        for _ in (1, 2):
            try:
                r = httpx.post(f"{settings.openai_base_url}/audio/speech", json=body, timeout=40,
                               headers={"Authorization": f"Bearer {settings.openai_api_key}"})
                break
            except httpx.HTTPError as e:
                print(f"  {name}: {type(e).__name__}, retrying")
        if r is None or r.status_code != 200:
            print(f"{name}: HTTP {getattr(r, 'status_code', '-')} {getattr(r, 'text', '')[:100]}"); break
        parts.append(r.content)
    else:
        (OUT / name).write_bytes(b"".join(parts))
        print(name)
