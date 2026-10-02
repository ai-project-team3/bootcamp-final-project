"""POST /tts — the mascot's voice. OpenAI by default (settings.tts_provider), TypeCast when set; the key stays on the server.

Only the mascot's lines come here. What the child recorded never leaves the
phone (difference 1). The app's own lines are baked into the app (eval/bake_lines.py); this route is
for the variable ones (echoes, the judge's next question).

Nothing sent here may contain a real name: names are masked on the phone and
the mascot's lines are written not to say the child's name (0928 agenda §4-1).
"""
import logging
import struct
import time

import httpx
from fastapi import APIRouter, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel, Field

from ..config import settings

log = logging.getLogger("tts")

router = APIRouter()


class TtsRequest(BaseModel):
    text: str = Field(min_length=1, max_length=300)
    voice_id: str | None = None           # persona choice; default is the setting
    previous_text: str | None = None      # smart emotion reads its neighbours
    next_text: str | None = None


def _silence_wav(seconds: float = 0.3, rate: int = 16000) -> bytes:
    n = int(seconds * rate)
    data = b"\x00\x00" * n
    return (b"RIFF" + struct.pack("<I", 36 + len(data)) + b"WAVEfmt " +
            struct.pack("<IHHIIHH", 16, 1, 1, rate, rate * 2, 2, 16) + b"data" +
            struct.pack("<I", len(data)) + data)


@router.post("/tts")
async def speak(req: TtsRequest) -> Response:
    """settings.tts_provider first, then tts_fallback if set (10-01 lead: openai alone, no fallback).
    The whole thing stays inside tts_deadline_s (the phone waits 20 s); the answer says who spoke
    in `X-Otto-TTS` so a deploy can be checked from outside."""
    if settings.mock:
        return Response(_silence_wav(), media_type="audio/wav")
    order = [settings.tts_provider]
    if settings.tts_fallback and settings.tts_fallback != settings.tts_provider:
        order.append(settings.tts_fallback)
    t0 = time.monotonic()
    last: HTTPException | None = None
    for i, provider in enumerate(order):
        left = settings.tts_deadline_s - (time.monotonic() - t0)
        # leave the fallback room: the first try gets at most 60 % when a second one waits
        budget = max(2.0, left * (0.6 if i < len(order) - 1 else 1.0))
        try:
            audio = await (_openai_audio if provider == "openai" else _typecast_audio)(req, budget)
            # who spoke, in the server log (`docker logs otto-backend`) — the text itself is not logged
            voice = settings.openai_tts_voice if provider == "openai" else (req.voice_id or settings.typecast_voice_id)
            log.info("tts %s · voice %s · %.2fs · %d chars%s", provider, voice, time.monotonic() - t0,
                     len(req.text), " · after a fallback" if i else "")
            return Response(audio, media_type="audio/mpeg", headers={"X-Otto-TTS": provider})
        except HTTPException as e:
            log.warning("tts %s failed: %s", provider, e.detail)
            last = e
    raise last or HTTPException(502, "tts: no provider")


async def _typecast_audio(req: TtsRequest, budget: float) -> bytes:
    if not settings.typecast_api_key:
        raise HTTPException(502, "missing TYPECAST_API_KEY")
    if settings.typecast_emotion == "smart":
        prompt = {"emotion_type": "smart"}
        if req.previous_text:
            prompt["previous_text"] = req.previous_text
        if req.next_text:
            prompt["next_text"] = req.next_text
    else:
        prompt = {"emotion_type": "preset", "emotion_preset": settings.typecast_emotion, "emotion_intensity": 1.0}
    body = {
        "voice_id": req.voice_id or settings.typecast_voice_id,
        "text": req.text, "model": settings.typecast_model, "language": "kor",
        "prompt": prompt, "output": {"audio_format": "mp3", "audio_tempo": settings.typecast_tempo},
    }
    try:
        async with httpx.AsyncClient(timeout=budget) as http:
            r = await http.post("https://api.typecast.ai/v1/text-to-speech", json=body,
                                headers={"X-API-KEY": settings.typecast_api_key})
    except httpx.HTTPError as e:
        raise HTTPException(502, f"tts typecast network: {type(e).__name__}") from e
    if r.status_code != 200:
        raise HTTPException(502, f"tts typecast HTTP {r.status_code}")
    return r.content


async def _openai_audio(req: TtsRequest, budget: float) -> bytes:
    """OpenAI speech (10-01 stand-in for TypeCast · settings.tts_provider). Same mp3 back,
    so the phone does not change. voice_id is a TypeCast id and is ignored here."""
    if not settings.openai_api_key:
        raise HTTPException(502, "missing OPENAI_API_KEY")
    body = {"model": settings.openai_tts_model, "voice": settings.openai_tts_voice, "input": req.text,
            "instructions": settings.openai_tts_instructions, "response_format": "mp3"}
    try:
        async with httpx.AsyncClient(timeout=budget) as http:
            r = await http.post(f"{settings.openai_base_url.rstrip('/')}/audio/speech", json=body,
                                headers={"Authorization": f"Bearer {settings.openai_api_key}"})
    except httpx.HTTPError as e:
        raise HTTPException(502, f"tts network: {type(e).__name__}") from e
    if r.status_code != 200:
        raise HTTPException(502, f"tts openai HTTP {r.status_code}")
    return r.content
