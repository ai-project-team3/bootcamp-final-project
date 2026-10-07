"""Problem reports from the parent area — taken in here, handled on the admin page (#283 · docs/문제신고_서버_설계_283.md).

Play wants an in-app report path. The app used to hand a report to the mail app, so nothing reached one place.

  POST /report                          public (the app calls it, no sign-in) — stores one report
  GET  /admin/reports?status=open|done|all   ┐
  GET  /admin/reports/{id}                   │ admin only (app/admin.py session) — the monitor domain's nginx
  GET  /admin/reports/{id}/attachment        │ already proxies /admin/ here, and the public backend address
  POST /admin/reports/{id}/status            │ answers 401 without the cookie
  DELETE /admin/reports/{id}                 ┘ a guardian asked, by report number, for it to be removed

What is kept (the lead's decision, 10-07): category · the time the server received it · app version · mode · page ·
the guardian's note, and — only when the guardian ticked it — one picture or one sentence the AI made for that page.
Never a child's drawing, recording or voice: the request model forbids every other key (422 before anything is
written), and a picture is re-encoded here so only plain JPEG pixels stay on disk. No IP, User-Agent, cookie or
header is written; abuse limits live in memory only.

Kept until handled; once done, deleted 30 days later (at start, every 24 h, and whenever the admin opens the list).
Files go to REPORT_DIR — on PC1 `C:\\otto\\state\\reports`, mounted as /state so a redeploy keeps them (#240).

⚖️ Values the lead has not decided yet (design §8) are the design's recommendations and marked ⚖️ below.
"""
import asyncio
import base64
import binascii
import io
import json
import logging
import os
import re
import secrets
import time
from collections import defaultdict, deque
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, Request, Response
from PIL import Image, UnidentifiedImageError
from pydantic import BaseModel, ConfigDict, Field, ValidationError, model_validator

from . import admin
from .config import REPO, settings

log = logging.getLogger("reports")

router = APIRouter()
admin_router = APIRouter(prefix="/admin/reports", dependencies=[Depends(admin.require_admin)])

KST = timezone(timedelta(hours=9))
KEEP_DAYS_AFTER_DONE = 30
ORPHAN_GRACE_S = 3600           # a picture or .tmp with no report is left this long — a report may be mid-write
# ⚖️ §8-④ attachment size · format — the app sends 1024 px JPEG 85 (usually under 300 KB)
BODY_MAX = int(2.5 * 1024 * 1024)
PICTURE_MAX = int(1.5 * 1024 * 1024)
SIDE_MIN, SIDE_MAX = 64, 4096
KEEP_LONG_SIDE = 1024
KEEP_QUALITY = 85
# ⚖️ §8-⑦ abuse limits — per address · per day · folder
PER_IP = 5
PER_IP_WINDOW_S = 600
PER_DAY = 100
FOLDER_MAX = 200 * 1024 * 1024
# ⚖️ §8-② unhandled reports are never deleted; past this they are flagged on the admin page
STALE_DAYS = 14

ID_RE = re.compile(r"^R-\d{4}-[0-9A-F]{4}$")
STATUSES = ("received", "reviewing", "done")


# ── what the app may send ─────────────────────────────────────────────────

class Attachment(BaseModel):
    # unknown keys are refused — a `wav_base64` or `strokes` slipped in by mistake never reaches the disk
    model_config = ConfigDict(extra="forbid")
    kind: Literal["image", "preset", "text"]
    source: Literal["background", "hero", "friend", "diary_otto"] | None = None
    data_base64: str | None = None
    name: str | None = Field(None, pattern=r"^[A-Za-z0-9_]{1,64}$")
    text: str | None = Field(None, min_length=1, max_length=500)

    @model_validator(mode="after")
    def _one_kind(self):
        want = {"image": {"source", "data_base64"}, "preset": {"name"}, "text": {"text"}}[self.kind]
        given = {k for k in ("source", "data_base64", "name", "text") if getattr(self, k) is not None}
        if given != want:
            raise ValueError(f"a {self.kind} attachment carries exactly {sorted(want)}")
        return self


class ReportIn(BaseModel):
    model_config = ConfigDict(extra="forbid")
    category: Literal["image", "text", "error", "other"]
    mode: Literal["story", "diary", "coop"] | None = None
    page: int | None = Field(None, ge=1, le=40)
    note: str = Field("", max_length=1000)
    app_version: str = Field(min_length=1, max_length=32)
    attachment: Attachment | None = None


class StatusIn(BaseModel):
    model_config = ConfigDict(extra="forbid")
    status: Literal["received", "reviewing", "done"]


# ── storage ───────────────────────────────────────────────────────────────

def folder() -> Path:
    p = Path(settings.report_dir) if settings.report_dir else REPO / "backend" / "reports"
    p.mkdir(parents=True, exist_ok=True)
    return p


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _iso(t: datetime) -> str:
    return t.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def _parse(s: str) -> datetime:
    return datetime.strptime(s, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)


def _write(path: Path, data: bytes) -> None:
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_bytes(data)
    os.replace(tmp, path)            # no half-written file is ever read


def _save(r: dict) -> None:
    _write(folder() / f"{r['id']}.json", json.dumps(r, ensure_ascii=False, indent=1).encode())


def _new_id(now: datetime) -> str:
    day = now.astimezone(KST).strftime("%m%d")
    while True:
        rid = f"R-{day}-{secrets.token_hex(2).upper()}"
        if not (folder() / f"{rid}.json").exists():
            return rid


def _load(rid: str) -> dict:
    if not ID_RE.fullmatch(rid):         # checked before it ever becomes a path
        raise HTTPException(422, "id: not a report number")
    p = folder() / f"{rid}.json"
    if not p.exists():
        raise HTTPException(404, "no such report")
    return json.loads(p.read_text(encoding="utf-8"))


def _all() -> list[dict]:
    out = []
    for p in folder().glob("R-*.json"):
        try:
            out.append(json.loads(p.read_text(encoding="utf-8")))
        except (OSError, ValueError):
            log.warning("reports: unreadable %s", p.name)
    return out


def _folder_bytes() -> int:
    return sum(p.stat().st_size for p in folder().iterdir() if p.is_file())


# ── the picture — re-encoded, so only pixels stay ─────────────────────────

def _picture(data_base64: str) -> bytes:
    try:
        raw = base64.b64decode(data_base64, validate=True)
    except (binascii.Error, ValueError):
        raise HTTPException(422, "attachment.data_base64: not base64")
    if len(raw) > PICTURE_MAX:
        raise HTTPException(413, "attachment picture too large")
    is_jpeg = raw[:3] == b"\xff\xd8\xff"
    is_png = raw[:8] == b"\x89PNG\r\n\x1a\n"
    is_webp = raw[:4] == b"RIFF" and raw[8:12] == b"WEBP"
    if not (is_jpeg or is_png or is_webp):
        raise HTTPException(422, "attachment.data_base64: not a JPEG, PNG or WebP picture")
    try:
        img = Image.open(io.BytesIO(raw))
        w, h = img.size                      # read from the header, before decoding the pixels
        if not (SIDE_MIN <= w <= SIDE_MAX and SIDE_MIN <= h <= SIDE_MAX):
            raise HTTPException(422, f"attachment picture must be {SIDE_MIN}-{SIDE_MAX} px a side")
        img.load()
    except HTTPException:
        raise
    except (UnidentifiedImageError, OSError, Image.DecompressionBombError):
        raise HTTPException(422, "attachment.data_base64: picture cannot be read")
    # transparency on white · long side 1024 · JPEG 85 · no EXIF or other chunks carried over
    if img.mode in ("RGBA", "LA", "P"):
        rgba = img.convert("RGBA")
        flat = Image.new("RGB", rgba.size, (255, 255, 255))
        flat.paste(rgba, mask=rgba.split()[3])
        img = flat
    else:
        img = img.convert("RGB")
    img.thumbnail((KEEP_LONG_SIDE, KEEP_LONG_SIDE))
    out = io.BytesIO()
    img.save(out, "JPEG", quality=KEEP_QUALITY, optimize=True)
    return out.getvalue()


# ── abuse limits — memory only, nothing about the sender is written ───────

_per_ip: dict[str, deque[float]] = defaultdict(deque)
_day = {"date": "", "count": 0}


def _client(request: Request) -> str:
    # same headers as main._forwarded_ip (Cloudflare first). Kept only in this deque, never on disk.
    for h in ("cf-connecting-ip", "x-forwarded-for", "x-real-ip"):
        v = request.headers.get(h)
        if v:
            return v.split(",")[0].strip()
    return request.client.host if request.client else "?"


def _check_limits(ip: str, now: datetime) -> None:
    q = _per_ip[ip]
    t = now.timestamp()
    while q and t - q[0] > PER_IP_WINDOW_S:
        q.popleft()
    for k in [k for k, v in _per_ip.items() if not v and k != ip]:
        del _per_ip[k]               # addresses that went quiet are not kept around
    if len(q) >= PER_IP:
        raise HTTPException(429, "too many reports from here — try again later")
    today = now.astimezone(KST).strftime("%Y-%m-%d")
    if _day["date"] != today:
        _day.update(date=today, count=0)
    if _day["count"] >= PER_DAY:
        raise HTTPException(503, "reports are full for today")
    if _folder_bytes() >= FOLDER_MAX:
        log.warning("reports: folder over %d MB — new reports refused", FOLDER_MAX // (1024 * 1024))
        raise HTTPException(503, "reports are full")


def _count(ip: str, now: datetime) -> None:
    _per_ip[ip].append(now.timestamp())
    _day["count"] += 1


def reset_limits() -> None:
    """tests"""
    _per_ip.clear()
    _day.update(date="", count=0)


# ── 30 days after done ────────────────────────────────────────────────────

def purge(now: datetime | None = None) -> int:
    """Delete reports done more than 30 days ago, their pictures, and orphaned pictures · leftover .tmp files once an hour old."""
    now = now or _now()
    gone = 0
    keep: set[str] = set()
    d = folder()
    for p in d.glob("R-*.json"):
        try:
            r = json.loads(p.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            continue
        done_at = r.get("done_at")
        if r.get("status") == "done" and done_at and _parse(done_at) + timedelta(days=KEEP_DAYS_AFTER_DONE) <= now:
            p.unlink(missing_ok=True)
            (d / f"{r['id']}.jpg").unlink(missing_ok=True)
            gone += 1
        else:
            keep.add(p.stem)
    # a report writes its picture (via .tmp) before its JSON — a sweep that lands in between must not take
    # the picture or the half-written file, so leftovers go only once they are an hour old
    for p in [*d.glob("*.jpg"), *d.glob("*.tmp")]:
        if p.suffix == ".jpg" and p.stem in keep:
            continue
        try:
            old = now.timestamp() - p.stat().st_mtime > ORPHAN_GRACE_S
        except OSError:
            continue
        if old:
            p.unlink(missing_ok=True)
    if gone:
        log.info("reports: purged %d", gone)      # how many only — never what they said
    return gone


async def purge_daily() -> None:
    """Started in main's lifespan, cancelled when it ends"""
    while True:
        try:
            await asyncio.to_thread(purge)
        except Exception:  # noqa: BLE001 — a failed sweep must not kill the loop
            log.exception("reports: purge failed")
        await asyncio.sleep(24 * 3600)


# ── routes ────────────────────────────────────────────────────────────────

@router.post("/report")
async def take_report(request: Request) -> dict:
    declared = request.headers.get("content-length")
    if declared and declared.isdigit() and int(declared) > BODY_MAX:
        raise HTTPException(413, "report too large")
    body = bytearray()
    async for chunk in request.stream():
        body += chunk
        if len(body) > BODY_MAX:
            raise HTTPException(413, "report too large")
    try:
        r = ReportIn.model_validate_json(bytes(body))
    except ValidationError as e:
        first = e.errors()[0] if e.errors() else {}
        where = ".".join(str(p) for p in first.get("loc", ()))
        raise HTTPException(422, f"{where}: {first.get('msg', 'invalid')}")
    now = _now()
    ip = _client(request)
    if not settings.mock:
        _check_limits(ip, now)           # before the picture — a refused request does not decode 4096² first
    picture = _picture(r.attachment.data_base64) if r.attachment and r.attachment.kind == "image" else None
    if settings.mock:
        return {"id": "R-0000-MOCK", "keep_days_after_done": KEEP_DAYS_AFTER_DONE}
    rid = _new_id(now)
    att = None
    if r.attachment:
        a = r.attachment
        att = {"kind": a.kind}
        if a.kind == "image":
            _write(folder() / f"{rid}.jpg", picture)
            att.update(source=a.source, file=f"{rid}.jpg")
        elif a.kind == "preset":
            att["name"] = a.name
        else:
            att["text"] = a.text
    _save({
        "id": rid,
        "received_at": _iso(now),
        "category": r.category, "mode": r.mode, "page": r.page,
        "note": r.note, "app_version": r.app_version,
        "attachment": att,
        "status": "received",
        "status_at": _iso(now),
        "done_at": None,
    })
    _count(ip, now)
    log.info("reports: received %s (%s)", rid, r.category)
    return {"id": rid, "keep_days_after_done": KEEP_DAYS_AFTER_DONE}


def _view(r: dict, now: datetime) -> dict:
    out = dict(r)
    if r.get("status") == "done" and r.get("done_at"):
        out["delete_at"] = _iso(_parse(r["done_at"]) + timedelta(days=KEEP_DAYS_AFTER_DONE))
    out["stale"] = r.get("status") != "done" and _parse(r["received_at"]) + timedelta(days=STALE_DAYS) <= now
    return out


@admin_router.get("")
def list_reports(status: Literal["open", "done", "all"] = "open") -> dict:
    purge()                              # cheap — a server that was off for a while catches up on the first look
    now = _now()
    every = sorted(_all(), key=lambda r: r["received_at"], reverse=True)
    wanted = [r for r in every if status == "all" or (r["status"] == "done") == (status == "done")]
    open_ = [r for r in every if r["status"] != "done"]
    return {
        "counts": {
            "open": len(open_),
            "done": len(every) - len(open_),
            "all": len(every),
            "stale": sum(1 for r in open_ if _view(r, now)["stale"]),
        },
        "reports": [
            {
                "id": r["id"], "received_at": r["received_at"], "category": r["category"],
                "mode": r["mode"], "page": r["page"], "status": r["status"],
                "attachment_kind": (r.get("attachment") or {}).get("kind"),
                "note_head": (r.get("note") or "")[:80],
                "stale": _view(r, now)["stale"],
            }
            for r in wanted
        ],
    }


@admin_router.get("/{rid}")
def get_report(rid: str) -> dict:
    return _view(_load(rid), _now())


@admin_router.get("/{rid}/attachment")
def get_attachment(rid: str) -> Response:
    r = _load(rid)
    att = r.get("attachment") or {}
    p = folder() / f"{rid}.jpg"
    if att.get("kind") != "image" or not p.exists():
        raise HTTPException(404, "no picture with this report")
    return Response(p.read_bytes(), media_type="image/jpeg",
                    headers={"Cache-Control": "no-store", "X-Content-Type-Options": "nosniff"})


@admin_router.post("/{rid}/status")
def set_status(rid: str, body: StatusIn) -> dict:
    r = _load(rid)
    now = _now()
    if r["status"] != body.status:
        r["status"] = body.status
        r["status_at"] = _iso(now)
        # out of done again → the 30-day clock stops
        r["done_at"] = _iso(now) if body.status == "done" else None
        _save(r)
    return _view(r, now)


@admin_router.delete("/{rid}", status_code=204)
def delete_report(rid: str) -> None:
    _load(rid)
    (folder() / f"{rid}.json").unlink(missing_ok=True)
    (folder() / f"{rid}.jpg").unlink(missing_ok=True)
    log.info("reports: deleted %s on request", rid)
