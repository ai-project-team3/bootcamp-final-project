"""말로 짓는 인형극 — backend proxy.

Thin on purpose. It holds the API keys, runs the rule filter, and calls the
LLM. Everything that can live on the phone already does: VAD, name masking,
preset matching, level calculation, report arithmetic.

Run (from backend/):
    py -m uvicorn main:app --host 0.0.0.0 --port 8000
    MOCK=1 → spec-shaped fixed answers, no keys, no GPU (for wiring the app)
"""
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
# Starlette's, not FastAPI's: it also catches the framework's own 400 ("error
# parsing the body") and 404, which FastAPI's subclass handler would miss.
from starlette.exceptions import HTTPException

from app.config import settings
from app.routers import image, judge, story, stt, tts, turn

app = FastAPI(title="말로 짓는 인형극")
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
