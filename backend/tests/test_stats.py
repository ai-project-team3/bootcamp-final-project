"""GET /stats — what the port-80 monitor reads (monitoring/, scripts/deploy/start_monitor.ps1).

Counts are process-wide and never reset, so every assertion here is about the *change* a call
makes, not an absolute number: another test module may have gone through the same middleware.
"""
import sys
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.config import settings          # noqa: E402
from main import app                     # noqa: E402


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setattr(settings, "mock", True)
    return TestClient(app)


def row(stats: dict, method: str, path: str) -> dict | None:
    return next((e for e in stats["endpoints"] if e["method"] == method and e["path"] == path), None)


def test_a_call_is_counted_under_its_route(client):
    before = row(client.get("/stats").json(), "GET", "/health")
    client.get("/health")
    after = row(client.get("/stats").json(), "GET", "/health")
    assert after["count"] == (before["count"] if before else 0) + 1
    assert after["status"]["200"] >= 1 and after["errors"] == 0


def test_stats_does_not_count_itself(client):
    client.get("/stats")
    assert row(client.get("/stats").json(), "GET", "/stats") is None, \
        "the observer would dominate the ratios it reports"


def test_an_unknown_path_goes_in_one_bucket(client):
    """Bots probe the public tunnel; one counter per probed path would grow without bound."""
    before = row(client.get("/stats").json(), "GET", "(unmatched)")
    client.get("/nope-one")
    client.get("/nope-two")
    after = row(client.get("/stats").json(), "GET", "(unmatched)")
    assert after["count"] == (before["count"] if before else 0) + 2
    assert after["status"]["404"] >= 2 and after["errors"] >= 2


def test_a_failure_is_counted_against_the_endpoint_that_failed(client):
    """The question a 502 raises is "which one" — a global status total cannot answer it."""
    client.post("/judge", json={"not": "a judge request"})
    r = row(client.get("/stats").json(), "POST", "/judge")
    assert r["status"]["422"] >= 1 and r["errors"] >= 1


def test_shares_add_up_and_averages_are_reported(client):
    client.get("/health")
    stats = client.get("/stats").json()
    assert stats["total"] == sum(e["count"] for e in stats["endpoints"])
    assert abs(sum(e["share"] for e in stats["endpoints"]) - 1.0) < 0.01
    assert all(e["avg_ms"] >= 0 for e in stats["endpoints"])
    assert stats["uptime_s"] >= 0
