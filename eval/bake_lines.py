"""Bake the app's own mascot lines with the server's voice, into the app (10-01).

Why: every line went to /tts, fixed ones too — a wait before each line and the paid voice
spent on words that never change. Baked lines play from the phone at once (Voice.baked).

Which lines: every line the app said while the test suite ran with OTTO_SPEECH_DUMP, in the
no-consent form NameMask.speakable gives (너 · 그 친구 · 친구야). They come from the tests' scripts,
never from a real child. Only lines matching one of these exactly play from the app — any other
line (the server's, one with a name read aloud, a child's echo) still goes to /tts. A stricter
"written whole in the app" filter kept 60 of 359 and missed plain lines like 「무슨 일이 생겼어?」.

Voice: the server's own settings (backend/app/config.py openai_tts_*), so a baked line and
a live one sound like the same mascot. Re-encoded to 40 kbps mono to keep the app small.

  OTTO_SPEECH_DUMP=… ./gradlew :app:testDebugUnitTest     (android/)
  py eval/bake_lines.py <dump file>     → android/app/src/main/assets/voice/<key>.mp3
                                          + eval/baked_lines.tsv (key · line)
Keys are sha1 of the line with spaces collapsed, first 16 hex — the same as Voice.bakedKey.
Files already there are kept, so a re-run only speaks new lines; a line no longer said is removed.
"""
import csv
import hashlib
import io
import re
import sys
from pathlib import Path

import av
import httpx

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "backend"))
from app.config import settings  # noqa: E402

OUT = ROOT / "android/app/src/main/assets/voice"
LIST = Path(__file__).parent / "baked_lines.tsv"
KBPS = 40


def norm(s: str) -> str:
    return re.sub(r"\s+", " ", s.strip())


def key(line: str) -> str:
    return hashlib.sha1(norm(line).encode("utf-8")).hexdigest()[:16]


def speak(text: str) -> bytes:
    for attempt in (1, 2, 3):
        try:
            r = httpx.post(f"{settings.openai_base_url.rstrip('/')}/audio/speech", timeout=60,
                           headers={"Authorization": f"Bearer {settings.openai_api_key}"},
                           json={"model": settings.openai_tts_model, "voice": settings.openai_tts_voice,
                                 "input": text, "instructions": settings.openai_tts_instructions,
                                 "response_format": "mp3"})
            if r.status_code == 200:
                return r.content
            print(f"  HTTP {r.status_code} {r.text[:120]}")
        except httpx.HTTPError as e:
            print(f"  {type(e).__name__}, retry {attempt}")
    raise SystemExit(f"could not speak: {text}")


def shrink(mp3: bytes) -> bytes:
    """mono · 24 kHz · KBPS kbps mp3"""
    src = av.open(io.BytesIO(mp3))
    buf = io.BytesIO()
    dst = av.open(buf, "w", format="mp3")
    st = dst.add_stream("libmp3lame", rate=24000)
    st.layout = "mono"
    st.bit_rate = KBPS * 1000
    res = av.AudioResampler(format=st.format, layout="mono", rate=24000)
    for frame in src.decode(audio=0):
        for f in res.resample(frame):
            for p in st.encode(f):
                dst.mux(p)
    for f in res.resample(None):
        for p in st.encode(f):
            dst.mux(p)
    for p in st.encode(None):
        dst.mux(p)
    dst.close()
    return buf.getvalue()


def main() -> None:
    dump = Path(sys.argv[1])
    said = {norm(l) for l in dump.read_text(encoding="utf-8").splitlines() if l.strip()}
    lines = sorted(said)
    print(f"baking {len(lines)} lines")
    OUT.mkdir(parents=True, exist_ok=True)
    want = {key(l): l for l in lines}
    for f in OUT.glob("*.mp3"):
        # neutral_*.mp3 share the folder (eval/bake_neutral.py) — not ours to remove
        if f.stem not in want and not f.stem.startswith("neutral_"):
            f.unlink()
    new = 0
    for k, line in want.items():
        f = OUT / f"{k}.mp3"
        if f.exists():
            continue
        f.write_bytes(shrink(speak(line)))
        new += 1
        print(f"  {k} {line}")
    with open(LIST, "w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh, delimiter="\t")
        w.writerow(["key", "line"])
        for k, line in want.items():
            w.writerow([k, line])
    size = sum(f.stat().st_size for f in OUT.glob("*.mp3"))
    print(f"spoke {new} new · {len(want)} lines · {size / 1e6:.1f} MB in {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
