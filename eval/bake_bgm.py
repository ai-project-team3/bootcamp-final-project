"""Bake the storybook background music (#221) — ACE-Step 1.5 takes → Opus .webm in the app.

The source takes are the raw WAVs (no fades): the app's player fades and loops them, so a fade baked
into the file would dip at every loop. Loudness is evened to -18 LUFS so moods sit at one level under
the narration. Same input → same output (no timestamps in the files).

    py eval/bake_bgm.py [--src D:/bgm_test/out/raw/ace] [--check]
"""
import argparse
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "android/app/src/main/assets/bgm"
# mood → the two takes kept (진웅 10-07: objective checks + ears; happy_discovery_1 dropped)
TRACKS = {
    "discovery": ["happy_discovery_0", "happy_discovery_2"],
    "playful": ["playful_comic_2", "playful_comic_0"],
    "adventure": ["forest_adventure_2", "forest_adventure_1"],
    "tense": ["tense_moment_0", "tense_moment_2"],
    "night": ["night_dream_0", "night_dream_2"],
    "ending": ["happy_ending_0", "happy_ending_2"],
}
SEED_BASE = 1000
LUFS = -18
TRIM = ("silenceremove=start_periods=1:start_threshold=-50dB,"
        "areverse,silenceremove=start_periods=1:start_threshold=-50dB,areverse")


def duration(p: Path) -> float:
    r = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(p)],
                       capture_output=True, text=True, check=True)
    return float(r.stdout.strip())


def bake(src: Path, dst: Path) -> None:
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(src), "-af", f"{TRIM},loudnorm=I={LUFS}:TP=-1.5:LRA=11",
                    "-ar", "48000", "-ac", "2", "-c:a", "libopus", "-b:a", "48k", "-vbr", "on",
                    "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", str(dst)], check=True)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", default="D:/bgm_test/out/raw/ace")
    ap.add_argument("--check", action="store_true", help="only verify what is in the app")
    a = ap.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    rows = []
    for mood, takes in TRACKS.items():
        for n, take in enumerate(takes, 1):
            dst = OUT / f"{mood}_{n}.webm"
            if not a.check:
                bake(Path(a.src) / f"{take}.wav", dst)
            rows.append({"file": dst.name, "mood": mood, "source": take,
                         "seed": SEED_BASE + int(take.rsplit("_", 1)[1]), "seconds": round(duration(dst), 1)})
    if not a.check:
        (OUT / "tracks.json").write_text(json.dumps(rows, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    size = sum((OUT / r["file"]).stat().st_size for r in rows)
    assert len(rows) == 12, rows
    assert size < 6_500_000, f"{size} bytes — over the 6.5 MB budget"
    assert all(50 <= r["seconds"] <= 61 for r in rows), rows
    print(f"{len(rows)} tracks · {size / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
