"""/tts provider switch (10-01): TypeCast's account was blocked, OpenAI speaks for now."""
import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.routers import tts as tts_route   # noqa: E402


def test_openai_provider_calls_openai_speech_with_marin(monkeypatch):
    seen = {}

    class Resp:
        status_code = 200
        content = b"ID3mp3"

    class Http:
        def __init__(self, **_): pass
        async def __aenter__(self): return self
        async def __aexit__(self, *a): return False
        async def post(self, url, json=None, headers=None):
            seen.update(url=url, body=json)
            return Resp()

    monkeypatch.setattr(tts_route.settings, "mock", False)
    monkeypatch.setattr(tts_route.settings, "tts_provider", "openai")
    monkeypatch.setattr(tts_route.settings, "openai_api_key", "x")
    monkeypatch.setattr(tts_route.httpx, "AsyncClient", Http)
    out = asyncio.run(tts_route.speak(tts_route.TtsRequest(text="안녕")))
    assert seen["url"].endswith("/audio/speech") and seen["body"]["voice"] == "marin"
    assert out.media_type == "audio/mpeg" and out.body == b"ID3mp3"
