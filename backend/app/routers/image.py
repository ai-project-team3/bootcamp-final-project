"""POST /image — a background for the place the child named, drawn during the session.

Rule 8: start when the place slot fills, never make the child wait, never show an
unchecked picture. So every way out that is not "drawn and checked in time" is
`preset: true` with a 200 — the app shows its own picture and moves on:

  blocked word → scene LLM says not drawable → ComfyUI down or slow →
  moderation flags it, fails, or has no key → preset

The deadline here (13 s) sits under the app's 15 s preset line so the answer
lands before the app gives up. The app's 8 s "조금 뒤에 올 거야" is its own.
"""
import asyncio
import base64
import json
import logging
import time
from functools import lru_cache

from fastapi import APIRouter

from ..config import REPO, settings
from ..filters.blocklist import is_blocked
from ..image import check, comfy
from ..llm.client import LLMError, complete
from ..llm.judge_prompt import system_block
from ..schemas.image import ImageRequest, ImageResult

router = APIRouter()
log = logging.getLogger("image")
EVAL = REPO / "eval"

# 1x1 transparent PNG — MOCK=1 only, so the app can wire the path with no GPU
_MOCK_PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==")


@lru_cache(maxsize=1)
def system() -> str:
    return system_block(EVAL / "image_prompt.md")


@lru_cache(maxsize=1)
def schema() -> dict:
    s = json.loads((EVAL / "image_schema.json").read_text(encoding="utf-8"))
    return {k: v for k, v in s.items() if k not in ("name", "description")}


def preset(reason: str, scene: str | None = None) -> ImageResult:
    log.info("image preset: %s", reason)
    return ImageResult(preset=True, reason=reason, scene=scene)


async def _draw(req: ImageRequest) -> ImageResult:
    t0 = time.monotonic()
    try:
        raw = await complete(system(), f"mode:{req.mode}\nplace:{req.place}", schema(),
                             name="image_scene", effort=settings.llm_effort_judge, max_output_tokens=200)
    except LLMError as e:
        return preset(f"scene llm: {e}")
    scene = (raw.get("scene") or "").strip()
    if not raw.get("safe") or not scene:
        return preset("not drawable")
    # the scene words are ours now, but check them too: they go into an unfiltered model
    if is_blocked(scene):
        return preset("blocked word in scene", scene)
    t1 = time.monotonic()
    try:
        png = await comfy.background(scene)
    except comfy.ComfyError as e:
        return preset(f"comfy: {e}", scene)
    t2 = time.monotonic()
    ok, why = await check.is_safe(png)
    log.info("image scene %.2fs draw %.2fs check %.2fs", t1 - t0, t2 - t1, time.monotonic() - t2)
    if not ok:
        return preset(f"check: {why}", scene)
    return ImageResult(preset=False, reason="ok", scene=scene, png_base64=base64.b64encode(png).decode())


@router.post("/image", response_model=ImageResult)
async def image(req: ImageRequest) -> ImageResult:
    if is_blocked(req.place):
        return preset("blocked word in place")      # never leaves the server
    if settings.mock:
        return ImageResult(preset=False, reason="mock", scene="mock scene",
                           png_base64=base64.b64encode(_MOCK_PNG).decode())
    try:
        return await asyncio.wait_for(_draw(req), timeout=settings.image_deadline_s)
    except asyncio.TimeoutError:
        return preset(f"over {settings.image_deadline_s:.0f}s")
