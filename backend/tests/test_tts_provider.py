"""/tts: TypeCast first, OpenAI when TypeCast fails (10-01 — the account was blocked once)."""
import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.routers import tts as tts_route   # noqa: E402


def _fake(monkeypatch, typecast_status: int):
    seen = []

    class Resp:
        def __init__(self, code, content): self.status_code, self.content = code, content

    class Http:
        def __init__(self, **_): pass
        async def __aenter__(self): return self
        async def __aexit__(self, *a): return False
        async def post(self, url, json=None, headers=None):
            seen.append((url, json))
            if "typecast" in url:
                return Resp(typecast_status, b"TCmp3")
            return Resp(200, b"OAmp3")

    monkeypatch.setattr(tts_route.settings, "mock", False)
    monkeypatch.setattr(tts_route.settings, "tts_provider", "typecast")
    monkeypatch.setattr(tts_route.settings, "tts_fallback", "openai")
    monkeypatch.setattr(tts_route.settings, "typecast_api_key", "k")
    monkeypatch.setattr(tts_route.settings, "openai_api_key", "k")
    monkeypatch.setattr(tts_route.httpx, "AsyncClient", Http)
    return seen


def test_typecast_speaks_siwoo_happy_a_little_slower(monkeypatch):
    seen = _fake(monkeypatch, 200)
    out = asyncio.run(tts_route.speak(tts_route.TtsRequest(text="안녕")))
    assert out.body == b"TCmp3" and out.headers["X-Otto-TTS"] == "typecast"
    body = seen[0][1]
    assert body["voice_id"] == "tc_6699eb3849dfac016c29444c"                       # Siwoo
    assert body["prompt"]["emotion_preset"] == "happy" and body["output"]["audio_tempo"] == 0.95


def test_a_refused_typecast_falls_back_to_openai_sage_0320(monkeypatch):
    """403 UNUSUAL_ACTIVITY_DETECTED / 402 out of credit must not silence the mascot."""
    seen = _fake(monkeypatch, 403)
    out = asyncio.run(tts_route.speak(tts_route.TtsRequest(text="안녕")))
    assert out.body == b"OAmp3" and out.headers["X-Otto-TTS"] == "openai"
    assert seen[1][0].endswith("/audio/speech") and seen[1][1]["voice"] == "sage" and seen[1][1]["model"] == "gpt-4o-mini-tts-2025-03-20"


def test_elevenlabs_speaks_with_the_set_voice_and_model_when_switched_on(monkeypatch):
    """10-06: off by default; one .env line switches after the ear test (eval/bench_tts_eleven_v4.py)."""
    seen = _fake(monkeypatch, 200)
    monkeypatch.setattr(tts_route.settings, "tts_provider", "elevenlabs")
    monkeypatch.setattr(tts_route.settings, "tts_fallback", "openai")
    monkeypatch.setattr(tts_route.settings, "elevenlabs_api_key", "k")
    monkeypatch.setattr(tts_route.settings, "elevenlabs_voice_id", "voiceKo")
    out = asyncio.run(tts_route.speak(tts_route.TtsRequest(text="안녕")))
    assert out.headers["X-Otto-TTS"] == "elevenlabs"
    url, body = seen[0]
    assert "/v1/text-to-speech/voiceKo" in url and body["model_id"] == "eleven_v4_turbo" and body["language_code"] == "ko"


def test_elevenlabs_without_a_voice_falls_back_instead_of_going_silent(monkeypatch):
    seen = _fake(monkeypatch, 200)
    monkeypatch.setattr(tts_route.settings, "tts_provider", "elevenlabs")
    monkeypatch.setattr(tts_route.settings, "elevenlabs_api_key", "k")
    monkeypatch.setattr(tts_route.settings, "elevenlabs_voice_id", "")
    out = asyncio.run(tts_route.speak(tts_route.TtsRequest(text="안녕")))
    assert out.headers["X-Otto-TTS"] == "openai" and seen[0][0].endswith("/audio/speech")


def test_the_default_is_still_openai():
    from app.config import Settings
    assert Settings.model_fields["tts_provider"].default == "openai"
