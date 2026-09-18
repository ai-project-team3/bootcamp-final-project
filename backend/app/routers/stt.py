"""POST /stt — audio in, text out.

The phone sends only speech segments (Silero VAD), which cuts hallucination,
cost and latency at once. Audio is deleted the moment it is transcribed:
docs/기술개요.md §3.
"""
from fastapi import APIRouter, UploadFile

router = APIRouter()


@router.post("/stt")
async def transcribe(file: UploadFile) -> dict:
    raise NotImplementedError("W1: faster-whisper large-v3-turbo, int8")
