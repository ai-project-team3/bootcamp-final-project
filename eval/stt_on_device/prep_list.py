"""Flatten the child + adult eval clips into stage/ and write list.txt: '<name> <audio_ctx>' per clip.

audio_ctx = ceil((sec + 1) * 50), capped at 1500 — the encoder window just covers the clip (+1 s), so no clip is cut.
Also writes clips.csv (name, set, id, age, speaker, ref, sec) for scoring.
"""
import csv
import json
import math
import re
import shutil
import sys
import wave
from pathlib import Path

TAG = re.compile(r"\([A-Z]+:[^)]*\)")
src = Path(sys.argv[1])
stage = Path(sys.argv[2])
stage.mkdir(exist_ok=True)
rows = []
for j in sorted(src.glob("*/*.json")):
    d = json.loads(j.read_text(encoding="utf-8"))
    u, r = d["발화정보"], d["녹음자정보"]
    ref = re.sub(r"\s+", " ", TAG.sub(" ", u["stt"])).strip()
    wav = j.with_suffix(".wav")
    if wav.exists() and ref:
        rows.append({"set": "child", "id": j.stem, "wav": wav, "ref": ref, "age": int(r["age"]), "speaker": r["recorderId"]})
with open(src / "manifest.csv", encoding="utf-8-sig") as f:
    for r in csv.DictReader(f):
        wav = src / f"{r['clip_id']}.wav"
        if wav.exists():
            rows.append({"set": "adult", "id": r["clip_id"], "wav": wav, "ref": r["ref"], "age": 0, "speaker": "adult"})
lst, out = [], []
for i, r in enumerate(rows):
    name = f"c{i:04d}.wav"
    with wave.open(str(r["wav"])) as w:
        sec = w.getnframes() / w.getframerate()
    shutil.copyfile(r["wav"], stage / name)
    lst.append(f"{name} {min(1500, math.ceil((sec + 1) * 50))}")
    out.append({"name": name, "set": r["set"], "id": r["id"], "age": r["age"], "speaker": r["speaker"], "ref": r["ref"], "sec": round(sec, 2)})
(stage / "list.txt").write_text("\n".join(lst) + "\n", encoding="utf-8", newline="\n")
with open(stage.parent / "clips.csv", "w", encoding="utf-8-sig", newline="") as f:
    wr = csv.DictWriter(f, fieldnames=list(out[0]))
    wr.writeheader()
    wr.writerows(out)
secs = sorted(o["sec"] for o in out)
print(len(out), "clips · child", sum(o["set"] == "child" for o in out), "· adult", sum(o["set"] == "adult" for o in out),
      f"· sec p50 {secs[len(secs) // 2]} max {secs[-1]} · >30s {sum(s > 30 for s in secs)}")
