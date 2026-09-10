"""Application entry point.

Add ONLY your own router's import and include_router lines here.
Conflicts in this file are resolved by 조장.
"""

import logging

from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException
from starlette.requests import Request

from app.config import settings
from app.routers import deal, eval, meeting, report, template, ws

logger = logging.getLogger(__name__)

app = FastAPI(title="영업 어시스턴트 AI", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origin_list or ["http://localhost:5173"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# --- routers ---------------------------------------------------------------
app.include_router(deal.router)
app.include_router(meeting.router)
app.include_router(ws.router)
app.include_router(template.router)
app.include_router(report.router)
app.include_router(eval.router)


# --- error shape -----------------------------------------------------------
# Every error response is {"error": true, "message": "..."}. FastAPI's defaults
# differ per path, so all four handlers below are required. Without the
# catch-all, an unhandled exception leaks as plain "Internal Server Error"
# and the frontend breaks reading .message.
def _err(status: int, message: str) -> JSONResponse:
    return JSONResponse(status_code=status, content={"error": True, "message": message})


# Register on Starlette's HTTPException, not FastAPI's. FastAPI's subclasses it,
# but unmatched routes and other framework-level errors raise the Starlette one -
# handling only fastapi.HTTPException lets a bare 404 out as {"detail": "..."}.
@app.exception_handler(StarletteHTTPException)
async def http_exception_handler(_: Request, exc: StarletteHTTPException) -> JSONResponse:
    return _err(exc.status_code, str(exc.detail))


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(_: Request, exc: RequestValidationError) -> JSONResponse:
    first = exc.errors()[0] if exc.errors() else {}
    field = ".".join(str(p) for p in first.get("loc", ())[1:]) or "요청"
    return _err(422, f"{field}: {first.get('msg', '형식이 올바르지 않습니다')}")


@app.exception_handler(Exception)
async def unhandled_exception_handler(_: Request, exc: Exception) -> JSONResponse:
    logger.exception("unhandled", exc_info=exc)
    return _err(500, "서버 내부 오류가 발생했습니다")


# WebSocket cannot carry a response body. Close with a code and a reason
# instead - see guidelines/3_API_명세.md.


@app.get("/health", tags=["meta"])
async def health() -> dict[str, str]:
    return {"status": "ok"}
