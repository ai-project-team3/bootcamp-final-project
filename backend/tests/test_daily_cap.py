"""The server's daily spend cap (10-06 · app/limits.py) — the last guard for the Play build's wallet."""
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app import limits                    # noqa: E402
from app.config import settings          # noqa: E402
from main import app                     # noqa: E402


@pytest.fixture
def client(monkeypatch, tmp_path):
    monkeypatch.setattr(settings, "mock", True)
    monkeypatch.setattr(settings, "daily_cap_state", str(tmp_path / "cap.json"))
    limits.reset()
    yield TestClient(app)
    limits.reset()


def test_paid_calls_stop_at_the_cap_and_free_ones_do_not(client, monkeypatch):
    monkeypatch.setattr(settings, "daily_cap_krw", 5.0)          # one voice line (3.69) fits, the second crosses
    assert client.post("/tts", json={"text": "안녕!"}).status_code == 200
    assert client.post("/tts", json={"text": "또 안녕!"}).status_code == 200
    r = client.post("/tts", json={"text": "세 번째"})
    assert r.status_code == 429 and r.json()["error"] is True
    # speech-to-text runs on our own GPU — not counted, not refused
    assert client.post("/stt", files={"file": ("a.wav", b"RIFF....", "audio/wav")}).status_code == 200
    assert limits.today()["spent_krw"] == pytest.approx(7.4, abs=0.1)


def test_the_day_survives_a_restart(client, monkeypatch, tmp_path):
    monkeypatch.setattr(settings, "daily_cap_krw", 100.0)
    client.post("/tts", json={"text": "안녕!"})
    saved = (tmp_path / "cap.json").read_text(encoding="utf-8")
    limits.reset()
    limits._load()
    assert limits.today()["spent_krw"] == pytest.approx(3.7, abs=0.1), saved


def test_zero_means_off(client, monkeypatch):
    monkeypatch.setattr(settings, "daily_cap_krw", 0.0)
    for _ in range(5):
        assert client.post("/tts", json={"text": "안녕!"}).status_code == 200
