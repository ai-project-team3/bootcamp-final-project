"""Daily spend cap for the whole server (10-06 · the server-connected Play build · #30 · #172).

The phone limits itself (2 books a day · `net/CallLimits.kt`); this is the last guard for the wallet —
a phone with cleared data, many testers at once, or a loop in some build. Each paid call adds its
measured price (`eval/results.md` 10-02 「한 세션의 값」); past `DAILY_CAP_KRW` the paid routes answer
429 until midnight and the app carries on as with the server down (baked lines · its own questions).
`/stt` and pictures drawn on PC2 run on our own GPU and are not counted. Pictures on the Modal GPU are
(#542 · billed by the second): each `/image` drawn there, and each real wake by the seconds of uptime
it adds — a public `/wake` would otherwise keep an L4 up all day.

Kept in a small file so a deploy restart does not reset the day. Nothing a child said is in it — a date
and a number.
"""
from __future__ import annotations

import json
import logging
import threading
from datetime import date
from pathlib import Path

from .config import settings

log = logging.getLogger(__name__)

# won per successful call, 10-02 measurement (USD 1 = 1,400 won)
# /partner (Jev, once a session · #303) has no measured price yet — counted at the /judge rate
PRICES = {"/tts": 3.69, "/turn": 0.90, "/story": 2.49, "/judge": 0.63, "/partner": 0.63}

# Modal L4 ≈ USD 0.80 an hour ≈ 0.31 won a second (list price, not measured on our bill): one picture ≈ 6 s;
# a wake is priced per call by the uptime it adds (comfy.wake_gpu). Not URL paths: main.py's middleware
# counts PRICES for every call, and a picture on PC2 or a skipped wake costs nothing — the routes count these
MODAL_WON_PER_S = 0.31
MODAL = {"modal picture": round(6 * MODAL_WON_PER_S, 2), "modal wake": 0.0}

_lock = threading.Lock()
_state = {"day": "", "spent": 0.0, "warned": False}


def _path() -> Path:
    return Path(settings.daily_cap_state) if settings.daily_cap_state else Path(__file__).resolve().parents[1] / "daily_cap.json"


def _load() -> None:
    try:
        _state.update(json.loads(_path().read_text(encoding="utf-8")))
    except (OSError, ValueError):
        pass


def _save() -> None:
    try:
        _path().write_text(json.dumps(_state), encoding="utf-8")
    except OSError as e:
        log.warning("daily cap state not saved: %s", e)


def _roll() -> None:
    today = date.today().isoformat()
    if _state["day"] != today:
        _state.update(day=today, spent=0.0, warned=False)
        _save()


_load()


def over(path: str) -> bool:
    """True when [path] is a paid route and today's cap is spent."""
    if (path not in PRICES and path not in MODAL) or settings.daily_cap_krw <= 0:
        return False
    with _lock:
        _roll()
        return _state["spent"] >= settings.daily_cap_krw


def spent(path: str, won: float | None = None) -> None:
    """A paid call succeeded — add its price ([won] when the call is priced by its size)."""
    price = PRICES.get(path, MODAL.get(path))
    if price is None:
        return
    with _lock:
        _roll()
        _state["spent"] += price if won is None else won
        if not _state["warned"] and settings.daily_cap_krw > 0 and _state["spent"] >= settings.daily_cap_krw / 2:
            _state["warned"] = True
            log.warning("daily cap: half of %.0f won spent today", settings.daily_cap_krw)
        _save()


def today() -> dict:
    with _lock:
        _roll()
        return {"day": _state["day"], "spent_krw": round(_state["spent"], 1), "cap_krw": settings.daily_cap_krw}


def reset() -> None:
    """Tests only."""
    with _lock:
        _state.update(day="", spent=0.0, warned=False)
