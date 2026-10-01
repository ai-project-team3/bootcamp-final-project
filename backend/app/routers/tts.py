"""POST /tts — the mascot's voice. TypeCast relay; the key stays on the server.

Only the mascot's lines come here. What the child recorded never leaves the
phone (difference 1). Fixed lines are baked per voice and bundled; this route is
for the variable ones (echoes, the judge's next question).

Nothing sent here may contain a real name: names are masked on the phone and
the mascot's lines are written not to say the child's name (0928 agenda §4-1).
"""
import struct

import httpx
from fastapi import APIRouter, HTTPException
from fastapi.responses import Response
from pydantic import BaseModel, Field

from ..config import settings

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
    if settings.mock:
        return Response(_silence_wav(), media_type="audio/wav")
    if settings.tts_provider == "openai":
        return await _openai(req)
    if not settings.typecast_api_key:
        raise HTTPException(502, "missing TYPECAST_API_KEY")
    prompt = {"emotion_type": "smart"}
    if req.previous_text:
        prompt["previous_text"] = req.previous_text
    if req.next_text:
        prompt["next_text"] = req.next_text
    body = {
        "voice_id": req.voice_id or settings.typecast_voice_id,
        "text": req.text, "model": settings.typecast_model, "language": "kor",
        "prompt": prompt, "output": {"audio_format": "mp3"},
    }
    try:
        async with httpx.AsyncClient(timeout=settings.tts_deadline_s) as http:   # phone waits 20 s
            r = await http.post("https://api.typecast.ai/v1/text-to-speech", json=body,
                                headers={"X-API-KEY": settings.typecast_api_key})
    except httpx.HTTPError as e:
        raise HTTPException(502, f"tts network: {type(e).__name__}") from e
    if r.status_code != 200:
        raise HTTPException(502, f"tts HTTP {r.status_code}")
    return Response(r.content, media_type="audio/mpeg")


async def _openai(req: TtsRequest) -> Response:
    """OpenAI speech (10-01 stand-in for TypeCast · settings.tts_provider). Same mp3 back,
    so the phone does not change. voice_id is a TypeCast id and is ignored here."""
    if not settings.openai_api_key:
        raise HTTPException(502, "missing OPENAI_API_KEY")
    body = {"model": settings.openai_tts_model, "voice": settings.openai_tts_voice, "input": req.text,
            "instructions": settings.openai_tts_instructions, "response_format": "mp3"}
    try:
        async with httpx.AsyncClient(timeout=settings.tts_deadline_s) as http:   # phone waits 20 s
            r = await http.post(f"{settings.openai_base_url.rstrip('/')}/audio/speech", json=body,
                                headers={"Authorization": f"Bearer {settings.openai_api_key}"})
    except httpx.HTTPError as e:
        raise HTTPException(502, f"tts network: {type(e).__name__}") from e
    if r.status_code != 200:
        raise HTTPException(502, f"tts openai HTTP {r.status_code}")
    return Response(r.content, media_type="audio/mpeg")
