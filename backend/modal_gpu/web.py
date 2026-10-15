"""The HTTP face of the redraw GPU on Modal (#460 ⑥) — kept free of `modal` so the backend tests can run it.

A child's drawing reaches this only inside one POST body, as base64 in the graph's OttoLoadImageB64 node,
and leaves only as the redrawn picture. Nothing here writes a file, logs a body, or echoes an error message:
a failed draw answers 500 with the exception's class name and nothing else (guidelines/1 §1-5).
"""
import logging
import time
from typing import Callable

from fastapi import FastAPI
from fastapi.responses import JSONResponse

log = logging.getLogger("otto.redraw_gpu")


def make_app(run: Callable[[dict], str], state: dict | None = None) -> FastAPI:
    """`run(workflow) -> png_base64` is the in-process ComfyUI executor (otto_redraw_modal.run_graph)."""
    state = state if state is not None else {}
    web = FastAPI(docs_url=None, redoc_url=None, openapi_url=None)

    @web.get("/wake")
    def wake():
        return {"ok": True, "restored": state.get("restored")}

    @web.post("/draw")
    def draw(body: dict):
        t0 = time.time()
        try:
            png = run(body["workflow"])
        except Exception as e:                       # noqa: BLE001 — the message may quote the drawing
            log.warning("redraw failed: %s", type(e).__name__)
            return JSONResponse({"error": type(e).__name__}, status_code=500)
        finally:
            body.clear()                             # drop our reference to the drawing now, not at GC
        return {"png_base64": png, "gen_s": round(time.time() - t0, 3)}

    return web
