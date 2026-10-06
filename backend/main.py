"""말로 짓는 인형극 — backend proxy.

Thin on purpose. It holds the API keys, runs the rule filter, and calls the
LLM. Everything that can live on the phone already does: VAD, name masking,
preset matching, level calculation, report arithmetic.

Run (from backend/):
    py -m uvicorn main:app --host 0.0.0.0 --port 8000
    MOCK=1 → spec-shaped fixed answers, no keys, no GPU (for wiring the app)
"""
import asyncio
import logging
import time
from collections import Counter
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
# Starlette's, not FastAPI's: it also catches the framework's own 400 ("error
# parsing the body") and 404, which FastAPI's subclass handler would miss.
from starlette.exceptions import HTTPException

from app import limits
from app.config import settings
from app.image import comfy

# Our own INFO lines (which voice spoke · stt timing · image steps) reach `docker logs`.
# Nothing was configured before, so only warnings showed (10-01). httpx logs each URL at INFO — kept quiet.
logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(name)s · %(message)s")
logging.getLogger("httpx").setLevel(logging.WARNING)
log_cap = logging.getLogger("limits")
from app.routers import image, judge, story, stt, tts, turn


@asynccontextmanager
async def lifespan(_: FastAPI):
    # Rule 8 needs the first picture of the day in time too: cold, it took 48.9 s (09-29)
    if not settings.mock and settings.image_warmup:
        asyncio.create_task(comfy.warm_up())
    if not settings.mock and settings.stt_warmup:
        asyncio.create_task(asyncio.to_thread(stt.warm_up))
    yield


app = FastAPI(title="말로 짓는 인형극", lifespan=lifespan)
app.include_router(judge.router)
app.include_router(story.router)
app.include_router(stt.router)
app.include_router(tts.router)
app.include_router(turn.router)
app.include_router(image.router)


# Spec §3-0: every error has one shape. The app reads `error`, never the status text.
@app.exception_handler(HTTPException)
async def _http_error(_: Request, e: HTTPException) -> JSONResponse:
    return JSONResponse({"error": True, "message": str(e.detail)}, status_code=e.status_code)


@app.exception_handler(RequestValidationError)
async def _shape_error(_: Request, e: RequestValidationError) -> JSONResponse:
    first = e.errors()[0] if e.errors() else {}
    where = ".".join(str(p) for p in first.get("loc", ()))
    return JSONResponse({"error": True, "message": f"{where}: {first.get('msg', 'invalid')}"},
                        status_code=422)


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "mock": settings.mock}


# ── /stats — what the monitor on port 80 reads (monitoring/, scripts/deploy/start_monitor.ps1) ──
#
# In memory on purpose: a restart is a reset. Nothing a child said is kept — only the route
# template, the status code and how long it took, never a path argument or a body.
_START = time.monotonic()
_HITS: Counter[tuple[str, str]] = Counter()      # (method, route template) -> calls
_MS: Counter[tuple[str, str]] = Counter()        # (method, route template) -> total ms
_STATUS: Counter[int] = Counter()                # status -> calls, over everything
# status per endpoint too: "which one is failing" is the question a 502 actually raises
# (the /story ReadTimeout of 10-01 was invisible for exactly this reason)
_ESTATUS: Counter[tuple[str, str, int]] = Counter()


@app.middleware("http")
async def _count(request: Request, call_next):
    t0 = time.monotonic()
    # the server's daily spend cap (10-06 · app/limits.py) — the app treats 429 as the server being away
    if limits.over(request.url.path):
        log_cap.warning("daily cap reached — %s refused", request.url.path)
        return JSONResponse({"error": True, "message": "daily cap"}, status_code=429)
    response = await call_next(request)
    if response.status_code == 200:
        limits.spent(request.url.path)
    ms = (time.monotonic() - t0) * 1000
    # The route template, not the raw path: an unmatched path (bots probe "/" and worse on the
    # public tunnel) must not grow a new counter every time.
    route = request.scope.get("route")
    path = getattr(route, "path", None) or "(unmatched)"
    if path != "/stats":                         # the observer does not count itself
        key = (request.method, path)
        _HITS[key] += 1
        _MS[key] += ms
        _STATUS[response.status_code] += 1
        _ESTATUS[(request.method, path, response.status_code)] += 1
    return response


@app.get("/stats")
def stats() -> dict:
    total = sum(_HITS.values())
    return {
        "uptime_s": round(time.monotonic() - _START),
        "total": total,
        "endpoints": [
            {
                "method": method,
                "path": path,
                "count": n,
                "share": round(n / total, 4) if total else 0.0,
                "avg_ms": round(_MS[(method, path)] / n),
                "status": {str(code): hits
                           for (m, p, code), hits in sorted(_ESTATUS.items())
                           if m == method and p == path},
                "errors": sum(hits for (m, p, code), hits in _ESTATUS.items()
                              if m == method and p == path and code >= 400),
            }
            for (method, path), n in _HITS.most_common()
        ],
        "status": {str(code): n for code, n in sorted(_STATUS.items())},
    }
