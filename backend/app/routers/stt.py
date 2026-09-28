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
import os
import sysconfig
from functools import lru_cache

from fastapi import APIRouter, HTTPException, UploadFile

from ..config import settings
from ..filters.hallucination import check_transcript

router = APIRouter()


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
    # In memory only. The child's voice never touches the disk (spec §3-4).
    segments, _ = _model().transcribe(io.BytesIO(audio), language="ko", beam_size=5)
    return "".join(s.text for s in segments).strip()


@router.post("/stt")
async def transcribe(file: UploadFile) -> dict:
    audio = await file.read()
    if not audio:
        raise HTTPException(400, "empty audio")
    if settings.mock:
        return {"text": "공룡나라 갈래"}
    try:
        text = await asyncio.to_thread(_transcribe, audio)
    except Exception as e:      # decoder or CUDA failure: the phone falls back, it does not stop
        raise HTTPException(502, f"stt failed: {type(e).__name__}") from e
    finally:
        del audio
    return {"text": text if check_transcript(text).keep else ""}
