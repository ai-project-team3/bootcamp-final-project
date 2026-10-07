"""Problem reports (#283 · app/reports.py · docs/문제신고_서버_설계_283.md §7-1 S1–S12).

Nothing of the child's ever lands here, nothing about the sender is written, and handled reports go 30 days later.
"""
import base64
import io
import json
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app import admin, reports            # noqa: E402
from app.config import settings           # noqa: E402
from main import app                      # noqa: E402

PW = "correct horse battery staple"


@pytest.fixture
def folder(tmp_path, monkeypatch):
    monkeypatch.setattr(settings, "mock", False)
    monkeypatch.setattr(settings, "report_dir", str(tmp_path))
    monkeypatch.setattr(settings, "admin_user", "otto-admin")
    monkeypatch.setattr(settings, "admin_password_hash", admin.hash_password(PW, rounds=1000))
    monkeypatch.setattr(settings, "admin_session_secret", "")
    monkeypatch.setattr(settings, "admin_cookie_secure", False)
    admin._fails.clear()
    reports.reset_limits()
    return tmp_path


@pytest.fixture
def client(folder):
    # no `with` — lifespan would warm models up; the routes are what is under test
    return TestClient(app)


@pytest.fixture
def signed_in(client):
    assert client.post("/admin/login", data={"user": "otto-admin", "password": PW}).status_code == 204
    return client


def picture(fmt="PNG", size=(1600, 900), exif=False) -> str:
    img = Image.new("RGBA" if fmt == "PNG" else "RGB", size, (40, 120, 200, 255) if fmt == "PNG" else (40, 120, 200))
    out = io.BytesIO()
    if exif:
        ex = Image.Exif()
        ex[0x010F] = "SecretCam"            # Make
        img.save(out, fmt, exif=ex.tobytes())
    else:
        img.save(out, fmt)
    return base64.b64encode(out.getvalue()).decode()


BASE = {"category": "image", "mode": "story", "page": 3, "note": "주인공 얼굴이 무서워요", "app_version": "0.5-closed"}


def send(client, **over):
    body = {**BASE, "attachment": None, **over}
    return client.post("/report", json=body)


def files(folder):
    return sorted(p.name for p in folder.iterdir())


# S1
def test_a_report_without_attachment_is_one_json(client, folder):
    r = send(client)
    assert r.status_code == 200
    rid = r.json()["id"]
    assert reports.ID_RE.match(rid) and r.json()["keep_days_after_done"] == 30
    assert files(folder) == [f"{rid}.json"]
    saved = json.loads((folder / f"{rid}.json").read_text(encoding="utf-8"))
    assert set(saved) == {"id", "received_at", "category", "mode", "page", "note", "app_version",
                          "attachment", "status", "status_at", "done_at"}
    assert saved["status"] == "received" and saved["done_at"] is None and saved["attachment"] is None
    assert saved["note"] == BASE["note"]


# S2
def test_a_picture_is_kept_as_a_small_jpeg_without_exif(client, folder):
    rid = send(client, attachment={"kind": "image", "source": "background", "data_base64": picture("PNG")}).json()["id"]
    assert files(folder) == [f"{rid}.jpg", f"{rid}.json"]
    kept = Image.open(folder / f"{rid}.jpg")
    assert kept.format == "JPEG" and max(kept.size) <= 1024
    rid2 = send(client, attachment={"kind": "image", "source": "hero", "data_base64": picture("JPEG", exif=True)}).json()["id"]
    kept2 = Image.open(folder / f"{rid2}.jpg")
    assert not kept2.getexif(), "EXIF must not survive"
    assert b"SecretCam" not in (folder / f"{rid2}.jpg").read_bytes()


# S3
@pytest.mark.parametrize("att", [
    {"kind": "text", "text": "주인공은 친구1 과 숲에 갔어요"},
    {"kind": "preset", "name": "forest_day"},
])
def test_text_and_preset_live_in_the_json_only(client, folder, att):
    rid = send(client, attachment=att).json()["id"]
    assert files(folder) == [f"{rid}.json"]
    saved = json.loads((folder / f"{rid}.json").read_text(encoding="utf-8"))
    assert saved["attachment"]["kind"] == att["kind"]


# S4
@pytest.mark.parametrize("extra,att", [
    ({"audio": "UklGRg=="}, None),
    ({"drawing": [[1, 2]]}, None),
    ({"device_id": "abc"}, None),
    ({}, {"kind": "text", "text": "x", "wav_base64": "UklGRg=="}),
])
def test_unknown_keys_are_refused_before_anything_is_written(client, folder, extra, att):
    r = client.post("/report", json={**BASE, **extra, "attachment": att})
    assert r.status_code == 422 and r.json()["error"] is True
    assert files(folder) == []


def test_an_attachment_carries_exactly_its_kind(client, folder):
    r = send(client, attachment={"kind": "text", "text": "x", "name": "forest_day"})
    assert r.status_code == 422 and files(folder) == []


# S5
def test_sound_bytes_as_a_picture_are_refused(client, folder):
    wav = base64.b64encode(b"RIFF" + b"\x00" * 4 + b"WAVEfmt " + b"\x00" * 64).decode()
    r = send(client, attachment={"kind": "image", "source": "background", "data_base64": wav})
    assert r.status_code == 422 and files(folder) == []


# S6
def test_too_large_is_413(client, folder):
    huge = "가" * (reports.BODY_MAX // 2)
    assert client.post("/report", content=json.dumps({**BASE, "note": huge}).encode(),
                       headers={"content-type": "application/json"}).status_code == 413
    big = base64.b64encode(b"\xff\xd8\xff" + b"\x00" * (reports.PICTURE_MAX + 10)).decode()
    r = send(client, attachment={"kind": "image", "source": "background", "data_base64": big})
    assert r.status_code == 413 and files(folder) == []


# S7
def test_nothing_about_the_sender_is_written(client, folder):
    rid = client.post("/report", json={**BASE, "attachment": None},
                      headers={"CF-Connecting-IP": "1.2.3.4", "User-Agent": "SenderAgent/9"}).json()["id"]
    text = (folder / f"{rid}.json").read_text(encoding="utf-8")
    assert "1.2.3.4" not in text and "SenderAgent" not in text


# S8
def test_admin_routes_need_a_session(client, folder):
    rid = send(client).json()["id"]
    for method, path in [("get", "/admin/reports"), ("get", f"/admin/reports/{rid}"),
                         ("get", f"/admin/reports/{rid}/attachment"), ("delete", f"/admin/reports/{rid}")]:
        assert getattr(client, method)(path).status_code == 401, path
    assert client.post(f"/admin/reports/{rid}/status", json={"status": "done"}).status_code == 401
    assert files(folder) == [f"{rid}.json"]


# S9
def test_status_moves_and_done_starts_the_clock(signed_in, folder):
    c = signed_in
    rid = send(c).json()["id"]
    assert c.post(f"/admin/reports/{rid}/status", json={"status": "reviewing"}).json()["done_at"] is None
    done = c.post(f"/admin/reports/{rid}/status", json={"status": "done"}).json()
    assert done["done_at"] and done["delete_at"]
    back = c.post(f"/admin/reports/{rid}/status", json={"status": "reviewing"}).json()
    assert back["done_at"] is None and "delete_at" not in back
    assert c.post(f"/admin/reports/{rid}/status", json={"status": "gone"}).status_code == 422
    assert c.get("/admin/reports/R-0101-ABCD").status_code == 404
    assert c.get("/admin/reports/..%2Fx").status_code in (404, 422)
    assert c.get("/admin/reports/R-01-x").status_code == 422


def test_list_detail_attachment_and_delete(signed_in, folder):
    c = signed_in
    plain = send(c, category="error", attachment=None).json()["id"]
    pic = send(c, attachment={"kind": "image", "source": "background", "data_base64": picture()}).json()["id"]
    listing = c.get("/admin/reports").json()
    assert listing["counts"] == {"open": 2, "done": 0, "all": 2, "stale": 0}
    assert [r["id"] for r in listing["reports"]][0] in (plain, pic)
    row = next(r for r in listing["reports"] if r["id"] == pic)
    assert row["attachment_kind"] == "image" and row["note_head"] == BASE["note"]
    a = c.get(f"/admin/reports/{pic}/attachment")
    assert a.status_code == 200 and a.headers["content-type"] == "image/jpeg"
    assert a.headers["cache-control"] == "no-store" and a.headers["x-content-type-options"] == "nosniff"
    assert c.get(f"/admin/reports/{plain}/attachment").status_code == 404
    assert c.delete(f"/admin/reports/{pic}").status_code == 204
    assert files(folder) == [f"{plain}.json"]
    c.post(f"/admin/reports/{plain}/status", json={"status": "done"})
    assert c.get("/admin/reports").json()["reports"] == []
    assert [r["id"] for r in c.get("/admin/reports?status=done").json()["reports"]] == [plain]


# S10
def test_purge_keeps_unhandled_and_recent(folder):
    now = datetime(2026, 11, 20, tzinfo=timezone.utc)

    def put(rid, status, done_days_ago=None, received_days_ago=1, jpg=False):
        r = {"id": rid, "received_at": reports._iso(now - timedelta(days=received_days_ago)), "category": "other",
             "mode": None, "page": None, "note": "", "app_version": "t", "attachment": None, "status": status,
             "status_at": reports._iso(now), "done_at": reports._iso(now - timedelta(days=done_days_ago))
             if done_days_ago is not None else None}
        (folder / f"{rid}.json").write_text(json.dumps(r), encoding="utf-8")
        if jpg:
            (folder / f"{rid}.jpg").write_bytes(b"x")

    put("R-1001-0001", "done", done_days_ago=31, jpg=True)
    put("R-1001-0002", "done", done_days_ago=29)
    put("R-1001-0003", "reviewing")
    put("R-1001-0004", "received", received_days_ago=365)
    (folder / "R-0909-DEAD.jpg").write_bytes(b"orphan")
    (folder / "R-1001-0009.json.tmp").write_bytes(b"half")
    assert reports.purge(now) == 1
    assert files(folder) == ["R-1001-0002.json", "R-1001-0003.json", "R-1001-0004.json"]


def test_old_unhandled_reports_are_flagged_not_deleted(signed_in, folder):
    old = (datetime.now(timezone.utc) - timedelta(days=reports.STALE_DAYS + 1))
    rid = "R-0101-AAAA"
    (folder / f"{rid}.json").write_text(json.dumps({
        "id": rid, "received_at": reports._iso(old), "category": "other", "mode": None, "page": None, "note": "",
        "app_version": "t", "attachment": None, "status": "received", "status_at": reports._iso(old), "done_at": None,
    }), encoding="utf-8")
    listing = signed_in.get("/admin/reports").json()
    assert listing["counts"]["stale"] == 1 and listing["reports"][0]["stale"] is True


# S11
def test_per_address_and_per_day_limits(client, folder, monkeypatch):
    for _ in range(reports.PER_IP):
        assert client.post("/report", json={**BASE}, headers={"CF-Connecting-IP": "9.9.9.9"}).status_code == 200
    assert client.post("/report", json={**BASE}, headers={"CF-Connecting-IP": "9.9.9.9"}).status_code == 429
    monkeypatch.setattr(reports, "PER_DAY", reports._day["count"] + 1)
    assert client.post("/report", json={**BASE}, headers={"CF-Connecting-IP": "8.8.8.8"}).status_code == 200
    assert client.post("/report", json={**BASE}, headers={"CF-Connecting-IP": "7.7.7.7"}).status_code == 503


def test_a_full_folder_refuses(client, folder, monkeypatch):
    monkeypatch.setattr(reports, "FOLDER_MAX", 10)
    (folder / "R-0101-FFFF.json").write_bytes(b"x" * 20)
    assert send(client).status_code == 503


# S12
def test_mock_stores_nothing(client, folder, monkeypatch):
    monkeypatch.setattr(settings, "mock", True)
    r = send(client)
    assert r.status_code == 200 and r.json()["id"] == "R-0000-MOCK"
    assert files(folder) == []
