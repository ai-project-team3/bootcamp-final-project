"""Does faster-whisper fit on the card while ComfyUI is holding SDXL?

results.md line 179 asserted it does not. That line was written without ever
loading the two together — this measures it instead.

VRAM is read from nvidia-smi, not from ComfyUI. ComfyUI's /system_stats did not
move at all when a second process allocated, so it reports something
process-local; the driver is the authority for "does a second model still fit".
Run with ComfyUI up and SDXL already loaded.

    python -m eval.bench_coresident
"""
from __future__ import annotations

import glob
import json
import os
import sysconfig
import time
import urllib.request

API = "http://127.0.0.1:8188"


def add_cuda_dlls() -> list[str]:
    """CTranslate2 needs cuBLAS/cuDNN on PATH; the pip wheels don't register them.

    Without this the model loads fine and then dies on the first encode with
    "Library cublas64_12.dll is not found" — which reads like a missing CUDA
    install but is only a search-path problem.
    """
    site = sysconfig.get_paths()["purelib"]
    dirs = []
    for sub in ("cublas", "cudnn"):
        d = os.path.join(site, "nvidia", sub, "bin")
        if os.path.isdir(d) and glob.glob(os.path.join(d, "*.dll")):
            dirs.append(d)
    for d in dirs:
        os.add_dll_directory(d)
    # add_dll_directory alone is not enough here: CTranslate2 resolves cuBLAS
    # lazily at first encode via a plain LoadLibrary, which walks PATH rather
    # than the directories added above.
    if dirs:
        os.environ["PATH"] = os.pathsep.join(dirs) + os.pathsep + os.environ.get("PATH", "")
    return dirs


MODEL = "large-v3-turbo"
COMPUTE = "int8_float16"


def comfy_mb() -> tuple[float, float]:
    """(used, total) MiB as ComfyUI reports it — its own allocation, not the device."""
    with urllib.request.urlopen(API + "/system_stats", timeout=30) as r:
        dev = json.loads(r.read())["devices"][0]
    total = dev["vram_total"] / 2 ** 20
    return total - dev["vram_free"] / 2 ** 20, total


def device_mb() -> float:
    """Whole-device MiB in use, from the driver.

    ComfyUI's own figure did not move when a second process allocated, so it is
    reporting something process-local. nvidia-smi is the authority for "does a
    second model still fit on this card".
    """
    import subprocess
    out = subprocess.run(
        ["nvidia-smi", "--query-gpu=memory.used", "--format=csv,noheader,nounits"],
        capture_output=True, text=True, timeout=30,
    ).stdout.strip().splitlines()[0]
    return float(out)


def main() -> None:
    comfy_used, total = comfy_mb()
    before = device_mb()
    print("카드 전체              %.0f MiB" % total)
    print("ComfyUI가 잡은 것       %.0f MiB" % comfy_used)
    print("드라이버 기준 실사용    %.0f MiB · 여유 %.0f MiB\n" % (before, total - before))

    add_cuda_dlls()
    from faster_whisper import WhisperModel

    print("faster-whisper %s (%s) 올리는 중 — 처음이면 내려받습니다..." % (MODEL, COMPUTE))
    t0 = time.time()
    model = WhisperModel(MODEL, device="cuda", compute_type=COMPUTE)
    load_s = time.time() - t0

    loaded = device_mb()
    print("  올리는 데 %.1f초 · 추가로 잡은 VRAM %.0f MiB" % (load_s, loaded - before))

    # Loading is lazy in places; a real transcription forces the full working
    # set — beam search buffers included — so peak is measured on actual work.
    import wave
    import struct
    import tempfile
    import os
    import math

    path = os.path.join(tempfile.gettempdir(), "coresident_probe.wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(16000)
        # 6 seconds of a quiet tone — content does not matter, the point is to
        # make the decoder actually run.
        frames = b"".join(
            struct.pack("<h", int(2000 * math.sin(i * 0.05))) for i in range(16000 * 6)
        )
        w.writeframes(frames)

    print("\n6초 오디오로 실제 전사 — 여기서 최대 사용량이 나옵니다")
    peak = loaded
    t0 = time.time()
    segments, info = model.transcribe(path, language="ko", beam_size=5)
    for _ in segments:
        cur = device_mb()
        peak = max(peak, cur)
    infer_s = time.time() - t0
    cur = device_mb()
    peak = max(peak, cur)

    print("  전사 %.2f초" % infer_s)
    print("\n" + "=" * 54)
    print("ComfyUI(SDXL)만        %.0f MiB" % before)
    print("+ faster-whisper 최대   %.0f MiB   (추가 %.0f MiB)" % (peak, peak - before))
    print("카드 전체              %.0f MiB   남은 자리 %.0f MiB" % (total, total - peak))
    print("=" * 54)
    verdict = "들어갑니다" if peak < total else "넘칩니다"
    print("판정: %s" % verdict)
    print("\n※ 화자 분리(pyannote)는 아직 안 얹었습니다 — 여기서 남은 자리로 재야 합니다.")


if __name__ == "__main__":
    main()
