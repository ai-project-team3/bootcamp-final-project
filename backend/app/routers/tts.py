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
        async with httpx.AsyncClient(timeout=30) as http:
            r = await http.post("https://api.typecast.ai/v1/text-to-speech", json=body,
                                headers={"X-API-KEY": settings.typecast_api_key})
    except httpx.HTTPError as e:
        raise HTTPException(502, f"tts network: {type(e).__name__}") from e
    if r.status_code != 200:
        raise HTTPException(502, f"tts HTTP {r.status_code}")
    return Response(r.content, media_type="audio/mpeg")
