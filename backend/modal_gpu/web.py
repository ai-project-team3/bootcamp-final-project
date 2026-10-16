"""The HTTP face of the picture GPU on Modal (#460 ⑥) — kept free of `modal` so the backend tests can run it.

Redraws, and backgrounds and characters when MODAL_KINDS says so. A child's drawing reaches this only inside
one POST body, as base64 in the graph's OttoLoadImageB64 node, and leaves only as the redrawn picture.
Nothing here writes a file, logs a body, or echoes an error message: a failed draw
answers 500 with the exception's class name and nothing else (guidelines/1 §1-5).
"""
import logging
import threading
import time
from typing import Callable

from fastapi import FastAPI
from fastapi.responses import JSONResponse

log = logging.getLogger("otto.redraw_gpu")


def make_app(run: Callable[[dict], str], state: dict | None = None) -> FastAPI:
    """`run(workflow) -> png_base64` is the in-process ComfyUI executor (otto_redraw_modal.run_graph)."""
    state = state if state is not None else {}
    web = FastAPI(docs_url=None, redoc_url=None, openapi_url=None)
    # FastAPI runs a plain def in a thread pool, so two pictures could meet on one GPU; the executor keeps the
    # loaded weights in one shared dict and is not made for that — one picture at a time per container
    gpu = threading.Lock()

    @web.get("/wake")
    def wake():
        return {"ok": True, "restored": state.get("restored")}

    @web.post("/draw")
    def draw(body: dict):
        try:
            with gpu:
                t0 = time.time()
                png = run(body["workflow"])
        except Exception as e:                       # noqa: BLE001 — the message may quote the drawing
            log.warning("draw failed: %s", type(e).__name__)
            return JSONResponse({"error": type(e).__name__}, status_code=500)
        finally:
            body.clear()                             # drop our reference to the drawing now, not at GC
        return {"png_base64": png, "gen_s": round(time.time() - t0, 3)}

    return web
