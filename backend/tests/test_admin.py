"""Admin sign-in for the monitor (app/admin.py · 10-07): no session, no /stats; no hash, no way in."""
import sys
import time
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app import admin                     # noqa: E402
from app.config import settings           # noqa: E402
from main import app                      # noqa: E402

PW = "correct horse battery staple"


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setattr(settings, "mock", True)
    monkeypatch.setattr(settings, "admin_user", "otto-admin")
    monkeypatch.setattr(settings, "admin_password_hash", admin.hash_password(PW, rounds=1000))
    monkeypatch.setattr(settings, "admin_session_secret", "")
    monkeypatch.setattr(settings, "admin_cookie_secure", False)     # TestClient talks plain http
    admin._fails.clear()
    return TestClient(app)


def sign_in(client, user="otto-admin", password=PW):
    return client.post("/admin/login", data={"user": user, "password": password})


def test_stats_and_check_need_a_session(client):
    assert client.get("/stats").status_code == 401
    assert client.get("/admin/check").status_code == 401
    assert sign_in(client).status_code == 204
    assert client.get("/admin/check").status_code == 204
    assert client.get("/stats").json()["total"] >= 0


def test_the_cookie_is_httponly_and_strict(client, monkeypatch):
    monkeypatch.setattr(settings, "admin_cookie_secure", True)
    r = sign_in(client)
    cookie = r.headers["set-cookie"].lower()
    assert "httponly" in cookie and "samesite=strict" in cookie and "secure" in cookie
    assert PW.lower() not in cookie


def test_wrong_password_or_user_is_refused(client):
    assert sign_in(client, password="nope").status_code == 401
    assert sign_in(client, user="someone").status_code == 401
    assert client.get("/stats").status_code == 401


def test_repeated_failures_are_throttled(client):
    for _ in range(admin.FAILS_PER_WINDOW):
        assert sign_in(client, password="nope").status_code == 401
    assert sign_in(client).status_code == 429, "even the right password waits after five misses"


def test_no_hash_means_no_way_in(client, monkeypatch):
    monkeypatch.setattr(settings, "admin_password_hash", "")
    assert sign_in(client).status_code == 503
    assert client.get("/stats").status_code == 401


def test_a_forged_or_expired_session_is_refused(client):
    client.cookies.set(admin.COOKIE, f"otto-admin|{int(time.time()) + 999}|deadbeef")
    assert client.get("/admin/check").status_code == 401
    client.cookies.set(admin.COOKIE, admin._sign("otto-admin", int(time.time()) - 1))
    assert client.get("/admin/check").status_code == 401


def test_logout_ends_the_session(client):
    sign_in(client)
    client.post("/admin/logout")
    assert client.get("/admin/check").status_code == 401


def test_a_new_password_signs_everyone_out(client, monkeypatch):
    sign_in(client)
    monkeypatch.setattr(settings, "admin_password_hash", admin.hash_password("another long password", rounds=1000))
    assert client.get("/admin/check").status_code == 401


def test_hash_round_trip():
    h = admin.hash_password("pw-123456789", rounds=1000)
    assert h.startswith("pbkdf2_sha256$1000$")
    assert admin.verify_password("pw-123456789", h)
    assert not admin.verify_password("pw-12345678", h)
    assert not admin.verify_password("x", "garbage")
