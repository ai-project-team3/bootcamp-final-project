"""Why a vendor call failed, in one short word, counted per endpoint for the monitor (#298 · 10-08).

Every non-200 from OpenAI (and the TTS vendors) became a bare 502, so the monitor only said "/tts 502".
On 10-07 the OpenAI credit ran out and it took hours to notice: /stt and /health stayed green.
Now each failure is classified where it is caught and counted, and `/stats` shows the reasons.

Reasons (stable — the monitor and the `vendor:<reason>` tag in 502 details read them):
  quota       insufficient_quota / billing — the credit is gone; nothing works until someone pays
  rate_limit  any other 429
  auth        401 / 403 — key revoked or wrong
  vendor_5xx  the vendor itself is down
  timeout     no answer in time
  other       anything else (4xx we sent wrong, network errors, …)

Only the status code and the vendor's `error.code` / `error.type` are read — never a key, never the
request body (it can hold a child's words), never the vendor's free-text message.
"""
import asyncio
import contextvars
import logging
import time
from collections import Counter

import httpx

log = logging.getLogger("vendor")

REASONS = ("quota", "rate_limit", "auth", "vendor_5xx", "timeout", "other")

_QUOTA_CODES = {"insufficient_quota", "billing_hard_limit_reached", "billing_not_active",
                "quota_exceeded", "account_deactivated"}


def _error_fields(body) -> tuple[str, str]:
    """OpenAI's `{"error": {"code": ..., "type": ...}}` — lower-cased, empty when absent or not JSON"""
    if body is not None and not isinstance(body, dict):
        try:
            body = body.json()                 # an httpx.Response (or anything shaped like one)
        except Exception:
            return "", ""
    if not isinstance(body, dict):
        return "", ""
    err = body.get("error")
    if not isinstance(err, dict):
        # ElevenLabs puts it under "detail": {"status": "quota_exceeded", ...}
        err = body.get("detail") if isinstance(body.get("detail"), dict) else {}
    code = err.get("code") or err.get("status") or ""
    kind = err.get("type") or ""
    return str(code).lower(), str(kind).lower()


def classify(status: int | None = None, body=None, exc: BaseException | None = None) -> str:
    """One reason from what the caller saw: an HTTP status (+ the response or its parsed JSON), or an exception"""
    if exc is not None:
        if isinstance(exc, (asyncio.TimeoutError, TimeoutError, httpx.TimeoutException)):
            return "timeout"
        return "other"
    code, kind = _error_fields(body)
    # OpenAI says "out of credit" as a 429 with code insufficient_quota; TypeCast as a bare 402
    if status == 402 or code in _QUOTA_CODES or kind in _QUOTA_CODES or "billing" in code or "quota" in code:
        return "quota"
    if status == 429 or code == "rate_limit_exceeded" or kind == "rate_limit_error":
        return "rate_limit"
    if status in (401, 403) or code == "invalid_api_key" or kind in ("authentication_error", "permission_error"):
        return "auth"
    if status in (408, 504):
        return "timeout"
    if status is not None and status >= 500:
        return "vendor_5xx"
    return "other"


# ── counting ──
# The middleware puts a fresh list here for each request; note() appends to it, and the middleware files
# the reasons under the route it matched. A call outside any request (a background warm-up) has no list.
current: contextvars.ContextVar[list | None] = contextvars.ContextVar("vendor_errors", default=None)

_COUNTS: Counter[tuple[str, str, str]] = Counter()        # (method, route, reason) -> failures
_LAST: dict[str, float] = {}                              # reason -> wall time of the latest
_LAST_VENDOR: dict[str, str] = {}                         # reason -> which vendor it was


def note(vendor: str, reason: str) -> str:
    """Record one failure for the request in progress. Returns `vendor:<reason>` for a 502 detail."""
    if reason not in REASONS:
        reason = "other"
    _LAST[reason] = time.time()
    _LAST_VENDOR[reason] = vendor
    sink = current.get()
    if sink is not None:
        sink.append(reason)
    else:
        _COUNTS[("-", "(background)", reason)] += 1
    if reason in ("quota", "auth"):
        log.error("vendor %s failed: %s — check the account, every call will fail", vendor, reason)
    return f"vendor:{reason}"


def file_under(method: str, path: str, reasons: list[str]) -> None:
    for r in reasons:
        _COUNTS[(method, path, r)] += 1


def per_endpoint(method: str, path: str) -> dict[str, int]:
    return {r: n for (m, p, r), n in sorted(_COUNTS.items()) if m == method and p == path}


def summary() -> dict:
    """reason -> count over every endpoint · when the last one was · which vendor"""
    totals: Counter[str] = Counter()
    for (_, _, r), n in _COUNTS.items():
        totals[r] += n
    now = time.time()
    return {
        r: {"count": totals[r], "last_at": round(_LAST[r]), "last_ago_s": round(now - _LAST[r]),
            "vendor": _LAST_VENDOR.get(r, "")}
        for r in REASONS if r in _LAST
    }


def background_rows() -> dict[str, int]:
    return per_endpoint("-", "(background)")
