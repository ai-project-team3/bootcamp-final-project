"""Can one background become three — and does combination A still fit with large-v3?

Two questions, one run, because they share the one card:

1. Combination A (image + STT + diarization) was measured on 09-21 with
   `large-v3-turbo`. On 09-23 STT moved to `large-v3`, and guidelines/10 wrote
   "+0.5GB, still inside A" — an estimate, never re-measured as a combination.
2. Mentor, 09-23: "make three images per scene — one for the book, one for the
   preview, ...". Measured three ways with STT + diarization resident:
     single      one background, the confirmed 09-21 recipe (8-step Lightning)
     seq3        three backgrounds queued one after another
     batch3      three backgrounds in one prompt (batch_size=3)
   against rule 8's lines: 8 s -> "it'll come in a moment", 15 s -> preset.

VRAM is read from nvidia-smi, sampled every 0.1 s during each generation.
ComfyUI's own /system_stats is process-local (bench_coresident docstring), and
Windows does not report per-process use, so the run first asks ComfyUI to unload
its models and takes that as the floor: whatever is left is the desktop
(browser, Discord, player). Every stage after is a delta from that floor, so the
numbers line up with 09-21 even though the desktop is heavier today.

Run with ComfyUI up, from the diarization venv (torch + faster-whisper + pyannote):

    .venv-diar\\Scripts\\python -m eval.bench_three_images
"""
from __future__ import annotations

import importlib.util
import json
import math
import os
import statistics
import struct
import subprocess
import tempfile
import threading
import time
import urllib.parse
import urllib.request
import wave
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT_IMG = ROOT / "assets" / "bench" / "three"
OUT_RAW = ROOT / "eval" / "raw"

WHISPER_MODEL = os.getenv("WHISPER_MODEL", "large-v3")   # 09-23 confirmed default
WHISPER_COMPUTE = "int8_float16"                          # same as 09-21 combination A
DIAR_MODEL = "pyannote/speaker-diarization-3.1"

LORA = "sdxl_lightning_8step_lora.safetensors"            # 09-21: 3.74 s, style kept
STEPS, STRENGTH = 8, 1.0

SINGLE_RUNS = 3
MULTI_RUNS = 2
CARD_MIB = 12288
RULE8_SOFT, RULE8_HARD = 8.0, 15.0


def _load_lightning():
    """Reuse the exact workflow that produced 3.74 s instead of re-typing it."""
    path = ROOT / "assets" / "tools" / "bench_lightning.py"
    spec = importlib.util.spec_from_file_location("bench_lightning", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


LB = _load_lightning()


def device_mb() -> float:
    out = subprocess.run(
        ["nvidia-smi", "--query-gpu=memory.used", "--format=csv,noheader,nounits"],
        capture_output=True, text=True, timeout=30,
    ).stdout.strip().splitlines()[0]
    return float(out)


class PeakSampler:
    """Poll nvidia-smi in the background; generation peaks last well under a second."""

    def __init__(self, every: float = 0.1):
        self.every, self.peak, self._stop = every, 0.0, threading.Event()

    def __enter__(self):
        self.peak = device_mb()
        self._t = threading.Thread(target=self._run, daemon=True)
        self._t.start()
        return self

    def _run(self):
        while not self._stop.is_set():
            self.peak = max(self.peak, device_mb())
            time.sleep(self.every)

    def __exit__(self, *exc):
        self._stop.set()
        self._t.join()
        self.peak = max(self.peak, device_mb())


def make_wav(path: str, seconds: int) -> None:
    """Two alternating tones — content is meaningless, it only makes the models run."""
    sr = 16000
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        frames = bytearray()
        for i in range(sr * seconds):
            hz = 180 if (i // (sr * 5)) % 2 == 0 else 320
            amp = 0 if (i // (sr * 5)) % 4 == 3 else 6000
            frames += struct.pack("<h", int(amp * math.sin(2 * math.pi * hz * i / sr)))
        w.writeframes(bytes(frames))


def workflow(prefix: str, seed: int, batch: int) -> dict:
    wf = LB.build(prefix, seed, LORA, STEPS, STRENGTH)
    wf["4"]["inputs"]["batch_size"] = batch
    return wf


def submit(wf: dict) -> str:
    return LB.post("/prompt", {"prompt": wf})["prompt_id"]


def wait(pid: str) -> list[dict]:
    while True:
        hist = LB.get("/history/" + pid)
        if pid in hist:
            imgs = []
            for node in hist[pid]["outputs"].values():
                imgs += node.get("images", [])
            return imgs
        time.sleep(0.1)


def save(imgs: list[dict], name: str) -> None:
    OUT_IMG.mkdir(parents=True, exist_ok=True)
    for i, img in enumerate(imgs):
        q = urllib.parse.urlencode(img)
        data = urllib.request.urlopen(LB.API + "/view?" + q, timeout=180).read()
        (OUT_IMG / f"{name}_{i}.png").write_bytes(data)


def gen(kind: str, seed: int, tag: str) -> tuple[float, float, int]:
    """(seconds until the last image exists, peak device MiB, image count)."""
    with PeakSampler() as ps:
        t0 = time.time()
        if kind == "single":
            imgs = wait(submit(workflow(tag, seed, 1)))
        elif kind == "batch3":
            imgs = wait(submit(workflow(tag, seed, 3)))
        else:  # seq3 — queue all three at once, ComfyUI runs them back to back
            pids = [submit(workflow(f"{tag}_{k}", seed + k, 1)) for k in range(3)]
            imgs = []
            for pid in pids:
                imgs += wait(pid)
        took = time.time() - t0
    save(imgs, tag)
    return took, ps.peak, len(imgs)


def verdict(sec: float) -> str:
    if sec <= RULE8_SOFT:
        return "8초 안 — 안내 문구 없이"
    if sec <= RULE8_HARD:
        return "8~15초 — 「조금 뒤에 올 거야」"
    return "15초 넘음 — 프리셋으로 확정"


def main() -> None:
    from eval.config import load_dotenv
    load_dotenv()
    token = os.environ.get("HF_TOKEN") or os.environ.get("HUGGINGFACE_TOKEN")
    if not token:
        raise SystemExit("HF_TOKEN 이 .env 에 없습니다 (pyannote 를 받으려면 필요)")

    # Unique seeds per run: ComfyUI caches by workflow hash and would return
    # an identical graph in ~0.25 s, which would make every timing meaningless.
    seed0 = int(time.time()) % 1_000_000_000
    stages: list[tuple[str, float]] = []

    # ── floor: unload ComfyUI's models; what remains is the desktop ──
    # /free answers with an empty body on some ComfyUI versions, so don't parse it
    req = urllib.request.Request(
        LB.API + "/free",
        data=json.dumps({"unload_models": True, "free_memory": True}).encode(),
        headers={"Content-Type": "application/json"},
    )
    urllib.request.urlopen(req, timeout=60).read()
    time.sleep(3)
    floor = device_mb()
    stages.append(("바닥 (ComfyUI 모델 내림 · 화면 앱만)", floor))
    print(f"바닥  {floor:,.0f} MiB  — 브라우저·디스코드 등 화면 앱 몫\n")

    # ── + image: warm-up generation loads SDXL + LoRA ──
    print("그림 모델 올리는 중 (첫 생성, 시간 안 셈)...")
    gen("single", seed0, "warm")
    img = device_mb()
    stages.append(("+ 그림 (SDXL + Lightning LoRA)", img))
    print(f"  +그림     {img:,.0f} MiB  (추가 {img - floor:,.0f})")

    # ── + STT ──
    import torch  # noqa: F401 — loads torch's cuBLAS/cuDNN before CTranslate2 looks for them
    from eval.bench_coresident import add_cuda_dlls
    add_cuda_dlls()
    from faster_whisper import WhisperModel
    stt = WhisperModel(WHISPER_MODEL, device="cuda", compute_type=WHISPER_COMPUTE)
    wav = os.path.join(tempfile.gettempdir(), "three_warm.wav")
    make_wav(wav, 6)
    for _ in stt.transcribe(wav, language="ko", beam_size=5)[0]:   # lazy until first encode
        pass
    after_stt = device_mb()
    stages.append((f"+ 받아쓰기 ({WHISPER_MODEL} {WHISPER_COMPUTE})", after_stt))
    print(f"  +받아쓰기 {after_stt:,.0f} MiB  (추가 {after_stt - img:,.0f})")

    # ── + diarization ──
    # SKIP_DIAR=1 measures "image + STT only": diarization is no longer needed in
    # co-op (09-22) and is out of scope for story/diary, so dropping it is a real option.
    if os.getenv("SKIP_DIAR") == "1":
        os.remove(wav)
        after_diar = after_stt
        stages.append(("(화자분리 안 올림 — SKIP_DIAR=1)", after_diar))
        print(f"  화자분리 생략 → 모델 합계 {after_diar - floor:,.0f} MiB · 카드 남은 자리 {CARD_MIB - after_diar:,.0f} MiB\n")
        return _generate(stages, seed0, suffix="_nodiar")

    import torch
    from pyannote.audio import Pipeline
    os.environ["HF_TOKEN"] = token
    # Same scoped workaround as bench_diarization: torch>=2.6 defaults
    # weights_only=True and pyannote's official checkpoint pickles its own classes.
    # ⚠️ Measurement-only. Do not copy into service code.
    _load = torch.load
    torch.load = lambda *a, **k: _load(*a, **{**k, "weights_only": False})
    pipe = Pipeline.from_pretrained(DIAR_MODEL)
    pipe.to(torch.device("cuda"))
    make_wav(wav, 60)
    pipe(wav, num_speakers=2)
    os.remove(wav)
    after_diar = device_mb()
    stages.append(("+ 화자분리 (pyannote 3.1)", after_diar))
    print(f"  +화자분리 {after_diar:,.0f} MiB  (추가 {after_diar - after_stt:,.0f})")
    print(f"  → 조합 A 모델 합계 {after_diar - floor:,.0f} MiB · 카드 남은 자리 {CARD_MIB - after_diar:,.0f} MiB\n")
    _generate(stages, seed0, suffix="")


def _generate(stages: list[tuple[str, float]], seed0: int, suffix: str) -> None:
    """Timed generations with whatever is resident now, then the summary + raw JSON."""
    runs: list[dict] = []
    plan = [("single", SINGLE_RUNS), ("seq3", MULTI_RUNS), ("batch3", MULTI_RUNS)]
    for kind, n in plan:
        for r in range(n):
            seed0 += 10
            took, peak, count = gen(kind, seed0, f"{kind}_{r}")
            runs.append({"kind": kind, "run": r, "sec": round(took, 2),
                         "peak_mib": peak, "images": count})
            print(f"  {kind:7s} #{r}  {took:5.2f}초 · {count}장 · 최대 {peak:,.0f} MiB"
                  f"  (카드까지 {CARD_MIB - peak:,.0f})")

    # ── summary ──
    print("\n" + "=" * 70)
    print(f"{'방식':8s} {'중앙 시간':>9s} {'장당':>7s} {'최대 VRAM':>11s} {'여유':>8s}  규칙 8")
    summary = {}
    for kind, _ in plan:
        rs = [x for x in runs if x["kind"] == kind]
        med = statistics.median(x["sec"] for x in rs)
        peak = max(x["peak_mib"] for x in rs)
        per = med / rs[0]["images"]
        summary[kind] = {"median_sec": round(med, 2), "per_image_sec": round(per, 2),
                         "peak_mib": peak, "headroom_mib": CARD_MIB - peak}
        print(f"{kind:8s} {med:8.2f}초 {per:6.2f}초 {peak:10,.0f} {CARD_MIB - peak:7,.0f}  {verdict(med)}")
    print("=" * 70)

    OUT_RAW.mkdir(parents=True, exist_ok=True)
    out = OUT_RAW / f"three_images_{date.today().isoformat()}{suffix}.json"
    out.write_text(json.dumps({
        "whisper": f"{WHISPER_MODEL} {WHISPER_COMPUTE}",
        "lora": LORA, "steps": STEPS, "size": [LB.W, LB.H],
        "stages": [{"stage": s, "mib": m} for s, m in stages],
        "runs": runs, "summary": summary,
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n원자료 {out.relative_to(ROOT)} · 그림 {OUT_IMG.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
