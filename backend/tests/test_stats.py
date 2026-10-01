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


def test_a_caller_shows_up_with_its_device_and_count(client):
    client.get("/health", headers={"User-Agent": "Dalvik/2.1.0 (Linux; U; Android 16; SM-S938N Build/X)"})
    me = next((c for c in client.get("/stats").json()["clients"]
               if "SM-S938N" in c["user_agent"]), None)
    assert me and me["count"] >= 1 and "/health" in me["top_paths"]


def test_a_reader_off_the_tunnel_gets_no_visitor_list(client):
    """/stats answers on the public domain too — an IP list there would be a public visitor log."""
    client.get("/health")
    public = client.get("/stats", headers={"CF-Connecting-IP": "203.0.113.7"}).json()
    assert public["clients_shown"] is False and public["clients"] == []
    assert public["endpoints"], "counts stay public; only the visitors are withheld"
    assert client.get("/stats").json()["clients_shown"] is True


def test_a_forwarded_ip_wins_over_the_socket_peer(client):
    """Docker's NAT rewrites the peer to one gateway address, so only the header can tell phones apart."""
    client.get("/health", headers={"CF-Connecting-IP": "198.51.100.22", "User-Agent": "probe/1"})
    ips = [c["ip"] for c in client.get("/stats").json()["clients"] if c["user_agent"] == "probe/1"]
    assert ips == ["198.51.100.22"]


def test_shares_add_up_and_averages_are_reported(client):
    client.get("/health")
    stats = client.get("/stats").json()
    assert stats["total"] == sum(e["count"] for e in stats["endpoints"])
    assert abs(sum(e["share"] for e in stats["endpoints"]) - 1.0) < 0.01
    assert all(e["avg_ms"] >= 0 for e in stats["endpoints"])
    assert stats["uptime_s"] >= 0
