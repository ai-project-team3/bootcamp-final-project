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
from fastapi import APIRouter, UploadFile

router = APIRouter()


@router.post("/stt")
async def transcribe(file: UploadFile) -> dict:
    raise NotImplementedError("W1: faster-whisper large-v3-turbo, int8")
