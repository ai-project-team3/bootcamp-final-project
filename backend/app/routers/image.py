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
import random
import time
from functools import lru_cache

from fastapi import APIRouter

from ..config import REPO, settings
from ..filters.blocklist import is_blocked
from ..image import character, check, comfy
from ..llm.client import LLMError, complete
from ..llm.judge_prompt import system_block
from ..schemas.image import ImageRequest, ImageResult

router = APIRouter()
log = logging.getLogger("image")
EVAL = REPO / "eval"

# 1x1 transparent PNG — MOCK=1 only, so the app can wire the path with no GPU
_MOCK_PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==")


@lru_cache(maxsize=2)
def system(kind: str = "background") -> str:
    return system_block(EVAL / ("image_prompt.md" if kind == "background" else "character_prompt.md"))


@lru_cache(maxsize=2)
def schema(kind: str = "background") -> dict:
    name = "image_schema.json" if kind == "background" else "character_schema.json"
    s = json.loads((EVAL / name).read_text(encoding="utf-8"))
    return {k: v for k, v in s.items() if k not in ("name", "description")}


def preset(reason: str, scene: str | None = None) -> ImageResult:
    log.info("image preset: %s", reason)
    return ImageResult(preset=True, reason=reason, scene=scene)


_uploaded: dict[str, str] = {}          # rig → name in ComfyUI's input folder (uploaded once per server)


async def _paint(req: ImageRequest, scene: str, rig: str | None) -> bytes:
    if req.kind == "background":
        return await comfy.background(scene)
    tmpl = character.template(rig)
    if tmpl is not None and rig not in _uploaded:
        _uploaded[rig] = await comfy.upload(tmpl, f"otto_mannequin_{rig}.png")
    raw = await comfy.run(comfy.character_workflow(scene, rig, random.randrange(2 ** 31), _uploaded.get(rig)))
    return await asyncio.to_thread(character.cut_and_fit, raw)


async def _draw(req: ImageRequest) -> ImageResult:
    t0 = time.monotonic()
    field = "place" if req.kind == "background" else "description"
    try:
        raw = await complete(system(req.kind), f"mode:{req.mode}\n{field}:{req.words}", schema(req.kind),
                             name=f"image_{req.kind}", effort=settings.llm_effort_judge, max_output_tokens=200)
    except LLMError as e:
        return preset(f"scene llm: {e}")
    scene = (raw.get("scene") or raw.get("subject") or "").strip()
    rig = raw.get("rig") if req.kind == "character" else None
    if not raw.get("safe") or not scene:
        return preset("not drawable")
    # the scene words are ours now, but check them too: they go into an unfiltered model
    if is_blocked(scene):
        return preset("blocked word in scene", scene)
    t1 = time.monotonic()
    try:
        png = await _paint(req, scene, rig)
    except comfy.ComfyError as e:
        return preset(f"comfy: {e}", scene)
    except character.CutoutError as e:
        return preset(f"cutout: {e}", scene)
    t2 = time.monotonic()
    ok, why = await check.is_safe(png)
    log.info("image %s scene %.2fs draw %.2fs check %.2fs", req.kind, t1 - t0, t2 - t1, time.monotonic() - t2)
    if not ok:
        return preset(f"check: {why}", scene)
    return ImageResult(preset=False, reason="ok", scene=scene, rig=rig,
                       png_base64=base64.b64encode(png).decode())


@router.post("/image", response_model=ImageResult)
async def image(req: ImageRequest) -> ImageResult:
    if is_blocked(req.words):
        return preset(f"blocked word in {'place' if req.kind == 'background' else 'description'}")
    if settings.mock:
        return ImageResult(preset=False, reason="mock", scene="mock scene",
                           rig="human" if req.kind == "character" else None,
                           png_base64=base64.b64encode(_MOCK_PNG).decode())
    try:
        return await asyncio.wait_for(_draw(req), timeout=settings.image_deadline_s)
    except asyncio.TimeoutError:
        return preset(f"over {settings.image_deadline_s:.0f}s")
