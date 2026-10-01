"""One voice at several speeds, for an ear test — 10-01 조장: Siwoo · 밝게 sounded a little fast.

  py eval/bench_tts_tempo.py [voice_id] [preset]   → eval/tts_out/tempo/<voice>_<preset>_<tempo>.mp3

Reads the TypeCast key from the repo .env through the backend settings (the key is never printed).
"""
import sys
from pathlib import Path

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "backend"))
from app.config import settings  # noqa: E402

VOICE = sys.argv[1] if len(sys.argv) > 1 else "tc_6699eb3849dfac016c29444c"     # Siwoo
PRESET = sys.argv[2] if len(sys.argv) > 2 else "happy"
TEMPOS = [1.0, 0.95, 0.9, 0.85]
LINES = [   # a question · a reaction · a book line — the three kinds the mascot reads
    "안녕! 나는 오또야. 오늘은 어디로 놀러 가 볼까?",
    "와, 문어가 먹물을 쏘았구나! 그다음엔 어떻게 됐어?",
    "바닷속은 조용하고 파랬어요. 반짝이는 해파리가 길을 밝혀 주었어요.",
]
OUT = Path(__file__).parent / "tts_out" / "tempo"
OUT.mkdir(parents=True, exist_ok=True)

for tempo in TEMPOS:
    for i, text in enumerate(LINES, 1):
        body = {"voice_id": VOICE, "text": text, "model": settings.typecast_model, "language": "kor",
                "prompt": {"emotion_type": "preset", "emotion_preset": PRESET, "emotion_intensity": 1.0},
                "output": {"audio_format": "mp3", "audio_tempo": tempo}}
        r = httpx.post("https://api.typecast.ai/v1/text-to-speech", json=body, timeout=30,
                       headers={"X-API-KEY": settings.typecast_api_key})
        if r.status_code != 200:
            print(f"tempo {tempo} line {i}: HTTP {r.status_code}"); sys.exit(1)
        f = OUT / f"{VOICE[-6:]}_{PRESET}_{tempo:.2f}_{i}.mp3"
        f.write_bytes(r.content)
        print(f"{f.name}  {len(r.content) // 1024} KB")
