"""Daily spend cap for the whole server (10-06 · the server-connected Play build · #30 · #172).

The phone limits itself (2 books a day · `net/CallLimits.kt`); this is the last guard for the wallet —
a phone with cleared data, many testers at once, or a loop in some build. Each paid call adds its
measured price (`eval/results.md` 10-02 「한 세션의 값」); past `DAILY_CAP_KRW` the paid routes answer
429 until midnight and the app carries on as with the server down (baked lines · its own questions).
`/stt` and `/image` run on our own GPU and are not counted.

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
PRICES = {"/tts": 3.69, "/turn": 0.90, "/story": 2.49, "/judge": 0.63}

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
    if path not in PRICES or settings.daily_cap_krw <= 0:
        return False
    with _lock:
        _roll()
        return _state["spent"] >= settings.daily_cap_krw


def spent(path: str) -> None:
    """A paid call succeeded — add its price."""
    if path not in PRICES:
        return
    with _lock:
        _roll()
        _state["spent"] += PRICES[path]
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
