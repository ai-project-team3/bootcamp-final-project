"""POST /stt — audio in, text out.

The phone sends only speech segments (Silero VAD), which cuts hallucination,
cost and latency at once. Audio is deleted the moment it is transcribed:
노션 「기술개요」 §3.

Every transcript goes through app.filters.hallucination.check_transcript
before it is returned. whisper writes YouTube outro lines ("감사합니다",
"다음 영상에서 만나요") onto silence, and they pass the word filter because
they look harmless; a drop comes back as empty text so the phone treats it as
no answer. Do not use whisper's no_speech_prob -- turbo reports 0.00 always.
"""
import asyncio
import glob
import io
import logging
import os
import sysconfig
import threading
import time
from functools import lru_cache
from pathlib import Path

from fastapi import APIRouter, HTTPException, UploadFile

from ..config import settings
from ..filters.hallucination import check_transcript

router = APIRouter()
log = logging.getLogger("uvicorn.error")      # shows up in the server console next to the access log

# One at a time on the GPU. 09-29 on the S25+: the first five calls came in while the model
# was still loading and all five died (502) — lru_cache does not stop concurrent first loads.
_gpu = threading.Lock()


def _add_cuda_dlls() -> None:
    """Windows: pip's cuBLAS/cuDNN are not on the DLL search path (results.md 09-25 §2).

    The model loads fine and then dies on the first encode with "cublas64_12.dll
    is not found". CTranslate2 loads cuBLAS lazily with a plain LoadLibrary, so
    PATH is needed as well as add_dll_directory. Same fix as eval/bench_coresident.
    """
    if os.name != "nt":
        return
    site = sysconfig.get_paths()["purelib"]
    dirs = [d for sub in ("cublas", "cudnn")
            if glob.glob(os.path.join(d := os.path.join(site, "nvidia", sub, "bin"), "*.dll"))]
    for d in dirs:
        os.add_dll_directory(d)
    if dirs:
        os.environ["PATH"] = os.pathsep.join(dirs) + os.pathsep + os.environ.get("PATH", "")


@lru_cache(maxsize=1)
def _model():
    # Loaded on first use, not at import: MOCK=1 and the tests must not need a GPU.
    _add_cuda_dlls()
    from faster_whisper import WhisperModel
    return WhisperModel(settings.stt_model, device=settings.stt_device,
                        compute_type=settings.stt_compute_type)


def _transcribe(audio: bytes) -> str:
    # In memory only. The child's voice never touches the disk (spec §3-4) — except the
    # lead's own test switch below, off by default.
    with _gpu:
        segments, _ = _model().transcribe(io.BytesIO(audio), language="ko", beam_size=5)
        return "".join(s.text for s in segments).strip()


def warm_up() -> None:
    """Load the model at server start so the first child is not the one who waits (or gets a 502)."""
    try:
        with _gpu:
            _model()
        log.info("stt model loaded: %s", settings.stt_model)
    except Exception as e:
        log.warning("stt warm-up failed: %s: %s", type(e).__name__, e)


def _debug_keep(audio: bytes, text: str, kept: bool) -> None:
    """STT_DEBUG_DIR set → keep the audio and the text, to hear what the model heard.

    For the lead testing with their own voice only. Never set it where a child talks —
    the product promise is that the voice is deleted the moment it is transcribed.
    """
    d = settings.stt_debug_dir
    if not d:
        return
    Path(d).mkdir(parents=True, exist_ok=True)
    stem = Path(d) / time.strftime("%H%M%S")
    stem.with_suffix(".wav").write_bytes(audio)
    stem.with_suffix(".txt").write_text(f"{text}\nkept={kept}\n", encoding="utf-8")


def audio_stats(audio: bytes) -> str:
    """What the phone sent, in numbers only — length, loudness, clipping, silence at each end.

    10-01: transcripts felt worse in the evening while the server, fed the same clips, matched
    09-23 exactly (eval/stt_live_check.py). So the difference is in the audio the phone sends;
    this makes it visible in `docker logs` without a USB cable. No content is kept.
    """
    try:
        import wave

        import numpy as np
        with wave.open(io.BytesIO(audio)) as w:
            if w.getsampwidth() != 2:
                return "not 16-bit"
            rate = w.getframerate()
            x = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2").astype(np.float32)
            if w.getnchannels() > 1:
                x = x.reshape(-1, w.getnchannels()).mean(axis=1)
    except Exception:
        return "not wav"
    if x.size == 0:
        return "empty"
    rms = float(np.sqrt(np.mean(x ** 2))) or 1.0
    db = 20 * np.log10(rms / 32768)
    peak = float(np.abs(x).max())
    clipped = float(np.mean(np.abs(x) >= 32000)) * 100
    # silence at each end: 20 ms frames under 1/10 of this clip's own RMS
    f = max(1, rate // 50)
    frames = np.sqrt(np.mean(x[: x.size // f * f].reshape(-1, f) ** 2, axis=1)) if x.size >= f else np.array([rms])
    loud = np.nonzero(frames > rms * 0.1)[0]
    lead = (loud[0] if loud.size else len(frames)) * 0.02
    tail = (len(frames) - 1 - loud[-1] if loud.size else 0) * 0.02
    return (f"{x.size / rate:.1f}s @{rate}Hz · rms {db:.0f} dBFS · peak {peak / 32768:.2f} · "
            f"clipped {clipped:.1f}% · silence {lead:.1f}s before / {tail:.1f}s after")


@router.post("/stt")
async def transcribe(file: UploadFile) -> dict:
    audio = await file.read()
    if not audio:
        raise HTTPException(400, "empty audio")
    if settings.mock:
        return {"text": "공룡나라 갈래"}
    try:
        t0 = time.monotonic()
        text = await asyncio.to_thread(_transcribe, audio)
    except Exception as e:      # decoder or CUDA failure: the phone falls back, it does not stop
        log.warning("stt failed: %s: %s", type(e).__name__, e)
        raise HTTPException(502, f"stt failed: {type(e).__name__}") from e
    kept = check_transcript(text).keep
    # length and timing only — the words are a child's
    log.info("stt %.2fs · %d KB · %d chars · %s · %s", time.monotonic() - t0, len(audio) // 1024,
             len(text), "kept" if kept else "dropped as hallucination", audio_stats(audio))
    _debug_keep(audio, text, kept)
    del audio
    return {"text": text if kept else ""}
