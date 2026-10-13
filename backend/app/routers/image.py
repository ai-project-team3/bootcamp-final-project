"""POST /image — a background for the place the child named, drawn during the session.

Rule 8: start when the place slot fills, never make the child wait, never show an
unchecked picture. So every way out that is not "drawn and checked in time" is
`preset: true` with a 200 — the app shows its own picture and moves on:

  blocked word → scene LLM says not drawable → ComfyUI down or slow →
  moderation flags it, fails, or has no key → preset

The deadline here (13 s) sits under the app's 15 s preset line so the answer
lands before the app gives up. What the app shows while it waits is the app's (10-02: story no longer
says 「조금 뒤에 올 거야」; the diary still does).
"""
import asyncio
import base64
import logging
import random
import time
from functools import lru_cache

from fastapi import APIRouter

from ..config import REPO, settings
from ..filters.blocklist import is_blocked
from ..image import character, check, comfy
from ..llm.client import LLMError, complete
from ..llm.judge_prompt import load_schema, system_block
from ..schemas.image import ImageRequest, ImageResult

router = APIRouter()
log = logging.getLogger("image")
EVAL = REPO / "eval"

# 1x1 transparent PNG — MOCK=1 only, so the app can wire the path with no GPU
_MOCK_PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==")


# each kind orders its picture with its own prompt (eval/). The redraw one takes things as well
# as characters — the character prompt turned a house and a sun down as "not a character" (#32)
_ORDER = {"background": "image", "character": "character", "redraw": "redraw"}


@lru_cache(maxsize=4)
def system(kind: str = "background", mode: str = "story") -> str:
    # a diary place: the same prompt plus one rule — nothing people ride or hold (#264 · 10-08);
    # story and co-op keep the measured one word for word
    if kind == "background" and mode == "diary":
        return system_block(EVAL / "image_diary_prompt.md")
    return system_block(EVAL / f"{_ORDER[kind]}_prompt.md")


def schema(kind: str = "background") -> dict:
    return load_schema(f"{_ORDER[kind]}_schema.json")


def preset(reason: str, scene: str | None = None) -> ImageResult:
    log.info("image preset: %s", reason)
    return ImageResult(preset=True, reason=reason, scene=scene)


# GPU order when several phones ask at once (#32, 10-02): a child waits for a background or a
# character, nobody waits for a redraw (it shows at the next brush pause). So story pictures go to
# the front of ComfyUI's queue and keep the 13 s deadline counted from arrival, queue included;
# redraws go one at a time behind them with a longer deadline, and still arrive late rather than never.
_redraw_turn = asyncio.Semaphore(1)

_uploaded: dict[str, str] = {}          # rig → name in ComfyUI's input folder (uploaded once per server)


async def _paint(req: ImageRequest, scene: str, rig: str | None) -> bytes:
    if req.kind == "background":
        return await comfy.background(scene, req.style, req.mode)
    if req.kind == "redraw":
        # the child's drawing lives only in this call: decoded, sent to ComfyUI through
        # memory (comfy_nodes/otto_memory.py), history entry deleted in comfy.run
        backdrop = req.role == "background"
        if backdrop:                             # the whole board, lines where they were; bands washed (#168)
            drawing = await asyncio.to_thread(character.prepare_board, base64.b64decode(req.png_base64))
            drawing = await asyncio.to_thread(character.wash_bands, drawing)
        else:
            drawing = await asyncio.to_thread(character.prepare_drawing, base64.b64decode(req.png_base64))
            if req.mode == "diary":              # colored pencil keeps an outline an outline: fill it (10-05)
                drawing = await asyncio.to_thread(character.fill_closed, drawing)
        # one redraw in ComfyUI at a time: the queue behind a story picture stays at most one
        # redraw long (~5 s), and the story picture still jumps the rest (front=True)
        async with _redraw_turn:
            raw = await comfy.run(comfy.redraw_workflow(scene, random.randrange(2 ** 31),
                                                        base64.b64encode(drawing).decode(), mode=req.mode,
                                                        role=req.role, style=req.style))
        del drawing
        if backdrop:                             # a scene behind everything — nothing to cut out
            return raw
        return await asyncio.to_thread(character.cut_out_all, raw)
    tmpl = character.template(rig)
    if tmpl is not None and rig not in _uploaded:
        _uploaded[rig] = await comfy.upload(tmpl, f"otto_mannequin_{rig}.png")
    raw = await comfy.run(comfy.character_workflow(scene, rig, random.randrange(2 ** 31), _uploaded.get(rig), req.style),
                          front=True)
    return await asyncio.to_thread(character.cut_and_fit, raw)


async def _draw(req: ImageRequest) -> ImageResult:
    t0 = time.monotonic()
    field = "place" if req.kind == "background" else "description"
    # the order LLM gets only the words — never the child's drawing
    try:
        raw = await complete(system(req.kind, req.mode), f"mode:{req.mode}\n{field}:{req.words}", schema(req.kind),
                             name=f"image_{req.kind}", effort=settings.llm_effort_judge, max_output_tokens=200)
    except LLMError as e:
        return preset(f"scene llm: {e}")
    scene = (raw.get("scene") or raw.get("subject") or "").strip()
    rig = raw.get("rig") if req.kind == "character" else None       # a redraw is not rigged
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
    except (ValueError, TypeError):              # bad base64 — say nothing about the content
        return preset("bad drawing", scene)
    t2 = time.monotonic()
    ok, why = await check.is_safe(png)          # the generated picture only — never the child's
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
        if req.kind == "redraw":                 # hand the drawing back: the app wires the path, no GPU
            return ImageResult(preset=False, reason="mock", scene="mock scene", png_base64=req.png_base64)
        return ImageResult(preset=False, reason="mock", scene="mock scene",
                           rig="human" if req.kind == "character" else None,
                           png_base64=base64.b64encode(_MOCK_PNG).decode())
    limit = settings.redraw_deadline_s if req.kind == "redraw" else settings.image_deadline_s
    try:
        return await asyncio.wait_for(_draw(req), timeout=limit)
    except asyncio.TimeoutError:
        return preset(f"over {limit:.0f}s")
