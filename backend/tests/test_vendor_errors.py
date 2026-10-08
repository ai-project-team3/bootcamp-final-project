"""Vendor failures get a reason and are counted per endpoint (#298 · 10-08).

10-07: the OpenAI credit ran out and the monitor said only "/tts 502" for hours. Counts are process-wide and
never reset, so the /stats assertions are about the change a call makes, as in test_stats.py.
"""
import asyncio
import sys
from pathlib import Path

import httpx
import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app import admin, vendor_errors      # noqa: E402
from app.config import settings           # noqa: E402
from app.llm import client as llm         # noqa: E402
from app.routers import tts as tts_route  # noqa: E402
from main import app                      # noqa: E402

QUOTA_BODY = {"error": {"message": "You exceeded your current quota", "type": "insufficient_quota",
                        "param": None, "code": "insufficient_quota"}}


# ── the classifier ──

@pytest.mark.parametrize("status, body, want", [
    (429, QUOTA_BODY, "quota"),                                                   # 10-07, word for word
    (429, {"error": {"code": "rate_limit_exceeded", "type": "requests"}}, "rate_limit"),
    (429, None, "rate_limit"),
    (402, None, "quota"),                                                         # TypeCast out of credit
    (401, {"error": {"code": "invalid_api_key", "type": "invalid_request_error"}}, "auth"),
    (403, None, "auth"),
    (500, None, "vendor_5xx"),
    (503, {"error": {"type": "server_error"}}, "vendor_5xx"),
    (504, None, "timeout"),
    (400, {"error": {"code": "invalid_value"}}, "other"),
    (401, {"detail": {"status": "quota_exceeded"}}, "quota"),                     # ElevenLabs' shape
])
def test_status_and_error_code_give_one_reason(status, body, want):
    assert vendor_errors.classify(status, body) == want


def test_a_response_is_read_for_its_error_code():
    r = httpx.Response(429, json=QUOTA_BODY)
    assert vendor_errors.classify(r.status_code, r) == "quota"


def test_a_body_that_is_not_json_falls_back_to_the_status():
    r = httpx.Response(429, content=b"<html>busy</html>")
    assert vendor_errors.classify(r.status_code, r) == "rate_limit"


def test_exceptions_split_into_timeout_and_other():
    assert vendor_errors.classify(exc=httpx.ReadTimeout("slow")) == "timeout"
    assert vendor_errors.classify(exc=asyncio.TimeoutError()) == "timeout"
    assert vendor_errors.classify(exc=httpx.ConnectError("refused")) == "other"


# ── the LLM client carries the reason ──

def _fake_http(module, monkeypatch, response: httpx.Response):
    class Http:
        def __init__(self, **_): pass
        async def __aenter__(self): return self
        async def __aexit__(self, *a): return False
        async def post(self, *a, **k): return response
    monkeypatch.setattr(module.httpx, "AsyncClient", Http)


def test_an_out_of_credit_llm_call_says_quota_and_never_the_key(monkeypatch):
    monkeypatch.setattr(llm.settings, "llm_provider", "openai")
    monkeypatch.setattr(llm.settings, "openai_api_key", "sk-test-secret")
    _fake_http(llm, monkeypatch, httpx.Response(429, json=QUOTA_BODY))
    with pytest.raises(llm.LLMError) as e:
        asyncio.run(llm.complete("sys", "아이가 한 말", {"type": "object"}, effort="none"))
    assert e.value.reason == "quota"
    assert str(e.value) == "HTTP 429 vendor:quota"
    assert "sk-test" not in str(e.value) and "아이가" not in str(e.value)


# ── counted per endpoint, shown in /stats ──

@pytest.fixture
def admin_client(monkeypatch):
    monkeypatch.setattr(settings, "admin_user", "otto-admin")
    monkeypatch.setattr(settings, "admin_password_hash", admin.hash_password("test-password-123", rounds=1000))
    monkeypatch.setattr(settings, "admin_cookie_secure", False)
    admin._fails.clear()
    c = TestClient(app)
    assert c.post("/admin/login", data={"user": "otto-admin", "password": "test-password-123"}).status_code == 204
    return c


def _tts_row(stats: dict) -> dict:
    return next((e for e in stats["endpoints"] if e["method"] == "POST" and e["path"] == "/tts"), {})


def test_a_quota_failure_is_counted_under_the_endpoint_and_shown_in_stats(admin_client, monkeypatch):
    before = admin_client.get("/stats").json()
    was = (_tts_row(before).get("vendor") or {}).get("quota", 0)
    was_all = (before.get("vendor_errors", {}).get("quota") or {}).get("count", 0)

    monkeypatch.setattr(settings, "mock", False)
    monkeypatch.setattr(settings, "tts_provider", "openai")
    monkeypatch.setattr(settings, "tts_fallback", "")
    monkeypatch.setattr(settings, "openai_api_key", "sk-test-secret")
    _fake_http(tts_route, monkeypatch, httpx.Response(429, json=QUOTA_BODY))
    r = admin_client.post("/tts", json={"text": "안녕"})

    # the app's contract does not change: still a 502 in the one error shape, now with a stable tag
    assert r.status_code == 502 and r.json()["error"] is True
    assert r.json()["message"].endswith("vendor:quota")

    after = admin_client.get("/stats").json()   # TestClient is sync httpx — the fake AsyncClient does not touch it
    assert _tts_row(after)["vendor"]["quota"] == was + 1
    q = after["vendor_errors"]["quota"]
    assert q["count"] == was_all + 1 and q["vendor"] == "openai" and q["last_ago_s"] <= 5


def test_a_failure_outside_any_request_is_still_counted():
    before = (vendor_errors.summary().get("timeout") or {}).get("count", 0)
    assert vendor_errors.note("openai", "timeout") == "vendor:timeout"
    assert vendor_errors.summary()["timeout"]["count"] == before + 1


def test_an_unknown_reason_is_filed_as_other():
    assert vendor_errors.note("openai", "made-up") == "vendor:other"
