"""/tts: the same words in the same voice are bought once (10-14 · #461) — in memory only."""
import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import pytest  # noqa: E402

from app.routers import tts as tts_route   # noqa: E402


def _fake(monkeypatch):
    calls = []

    class Resp:
        def __init__(self, code, content): self.status_code, self.content = code, content

    class Http:
        def __init__(self, **_): pass
        async def __aenter__(self): return self
        async def __aexit__(self, *a): return False
        async def post(self, url, json=None, headers=None):
            calls.append(json["input"])
            return Resp(200, f"mp3-{len(calls)}".encode())

    monkeypatch.setattr(tts_route.settings, "mock", False)
    monkeypatch.setattr(tts_route.settings, "tts_provider", "openai")
    monkeypatch.setattr(tts_route.settings, "tts_fallback", "")
    monkeypatch.setattr(tts_route.settings, "openai_api_key", "k")
    monkeypatch.setattr(tts_route.httpx, "AsyncClient", Http)
    monkeypatch.setattr(tts_route, "level", lambda a: a)
    tts_route._cache.clear()
    return calls


def _say(text):
    return asyncio.run(tts_route.speak(tts_route.TtsRequest(text=text)))


def test_the_same_line_is_bought_once(monkeypatch):
    calls = _fake(monkeypatch)
    first, again = _say("앗, 오또 귀가 잠깐 멍해졌어."), _say("앗, 오또 귀가 잠깐 멍해졌어.")
    assert calls == ["앗, 오또 귀가 잠깐 멍해졌어."]
    assert again.body == first.body and again.headers["X-Otto-TTS-Cache"] == "hit"
    assert "X-Otto-TTS-Cache" not in first.headers
    _say("다른 줄이야.")
    assert len(calls) == 2


def test_a_changed_voice_does_not_play_the_old_clip(monkeypatch):
    calls = _fake(monkeypatch)
    _say("안녕")
    monkeypatch.setattr(tts_route.settings, "openai_tts_voice", "coral")
    _say("안녕")
    assert len(calls) == 2


def test_old_entries_expire_and_the_oldest_goes_first(monkeypatch):
    calls = _fake(monkeypatch)
    monkeypatch.setattr(tts_route.settings, "tts_cache_items", 2)
    for t in ("하나", "둘", "셋"):
        _say(t)
    _say("하나")                                   # pushed out by 셋
    assert calls == ["하나", "둘", "셋", "하나"]
    monkeypatch.setattr(tts_route.settings, "tts_cache_ttl_s", -1)
    _say("셋")                                     # past its time
    assert calls[-1] == "셋" and len(calls) == 5


def test_off_when_zero(monkeypatch):
    calls = _fake(monkeypatch)
    monkeypatch.setattr(tts_route.settings, "tts_cache_items", 0)
    _say("안녕"); _say("안녕")
    assert len(calls) == 2


def test_the_text_itself_is_not_kept(monkeypatch):
    _fake(monkeypatch)
    _say("민준이가 모래성을 만들었구나.")
    assert all("민준" not in k for k in tts_route._cache._d)

