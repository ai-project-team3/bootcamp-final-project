"""ComfyUI on our GPU. One background, the 09-21 recipe: SDXL base + Lightning 8-step LoRA.

Numbers behind the recipe (results.md 09-21 · 09-25): 3.74 s alone, 3.86 s with
large-v3 resident. 4-step was 7.5x faster but lost the cut-paper look, so 8 it is.
The style strings are the measured ones (assets/tools/bench_lightning.py) — change
them and the timing and the look are no longer the measured ones.

⚠️ The model has no safety filter. Nothing from here goes to a child unchecked —
routers/image.py runs the check.
"""
import asyncio
import base64
import random
import time
import urllib.parse

import httpx

from ..config import settings

# Wool felt since 10-05, to match the app's own felt art (the lead: 「양모 펠트 초안 괜찮네」 10-03; device
# round 10-05: 「아직 양모 펠트 컨셉이 아닌 것 같음」). Same model and steps; measured 10-03 (3 places, same time as
# cut paper — eval/bench_felt_style.py). Cut paper was the 09-21 bench string.
BG_STYLE = (", wide landscape, no characters, no people, no animals, soft wool felt and fabric craft 3D children's picture "
            "book illustration, visible felt fibers and stitched edges, cute rounded shapes, warm pastel colors "
            "(cream, mustard yellow, coral, teal, sky blue), gentle soft lighting, cozy, no text, no letters, high quality")
NEG = ("text, letters, words, watermark, signature, photo, photorealistic, 3d render, "
       "blurry, ugly, scary, dark, horror, "
       "nudity, blood, weapon, gore")      # last line added for the product; not in the bench
W, H = 1344, 768

# Crayon (10-07 종훈 picked 「크레용 아이 그림체」 from four candidates: thick hand-drawn outlines, waxy fill).
# Measured 10-13 on PC2 (eval/bench_crayon_style.py · eval/results.md): same time as felt, characters cut out 8/8.
# The first background words came out as grey pencil sketches on SDXL Lightning (3 of 4 places) — 「crayon」 alone
# reads as a sketch, and at cfg 1.0 the negative barely counts. Naming the colours and 「oil pastel」 keeps them coloured
CRAYON_BG_STYLE = (", wide landscape, no characters, no people, no animals, bright colorful oil pastel and crayon drawing "
                   "by a child, thick waxy strokes, bold dark brown outlines, simple big rounded shapes, saturated sky blue, "
                   "grass green, sunny yellow and coral, cute naive picture book art, white paper, no text, no letters")
CRAYON_NEG = ("text, letters, words, watermark, signature, photo, photorealistic, 3d render, 3d, felt, wool, fabric, "
              "plastic, glossy, blurry, ugly, scary, dark, horror, "
              "nudity, blood, weapon, gore")
CRAYON_CHAR_STYLE = (", children's crayon drawing character, thick dark brown hand-drawn outlines, waxy crayon texture "
                     "fill, simple flat rounded shapes, bright warm colors, isolated on plain pure white background, "
                     "no shadow, no text")
CRAYON_CHAR_NEG = ("text, letters, watermark, photo, photorealistic, 3d render, 3d, felt, wool, fabric, plastic, glossy, "
                   "blurry, ugly, scary, dark, horror, "
                   "background scenery, frame, border, card, backdrop, circle behind, colored background, "
                   "multiple characters, nudity, blood, weapon, gore")


class ComfyError(Exception):
    pass


def styles(style: str) -> dict:
    """The words for one art style — background, its negative, character, its negative.
    Unknown names are the schema's job (422); here they fall back to felt"""
    if style == "crayon":
        return {"bg": CRAYON_BG_STYLE, "neg": CRAYON_NEG, "char": CRAYON_CHAR_STYLE, "char_neg": CRAYON_CHAR_NEG}
    return {"bg": BG_STYLE, "neg": NEG, "char": CHAR_STYLE, "char_neg": CHAR_NEG}


def workflow(scene: str, seed: int, style: str = "felt", mode: str = "story") -> dict:
    st = styles(style)
    if mode == "diary":                  # the diary keeps colored pencil whatever the book's style (#264)
        st = {**st, "bg": DIARY_BG_STYLE, "neg": DIARY_BG_NEG}
    return {
        "1": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": settings.image_ckpt}},
        "8": {"class_type": "LoraLoaderModelOnly", "inputs": {
            "model": ["1", 0], "lora_name": settings.image_lora, "strength_model": 1.0}},
        "2": {"class_type": "CLIPTextEncode", "inputs": {"text": scene + st["bg"], "clip": ["1", 1]}},
        "3": {"class_type": "CLIPTextEncode", "inputs": {"text": st["neg"], "clip": ["1", 1]}},
        "4": {"class_type": "EmptyLatentImage", "inputs": {"width": W, "height": H, "batch_size": 1}},
        "5": {"class_type": "KSampler", "inputs": {
            "model": ["8", 0], "positive": ["2", 0], "negative": ["3", 0], "latent_image": ["4", 0],
            # ComfyUI caches by graph hash: a fixed seed would hand back the last image
            "seed": seed, "steps": 8, "cfg": 1.0,
            "sampler_name": "euler", "scheduler": "sgm_uniform", "denoise": 1}},
        "6": {"class_type": "VAEDecode", "inputs": {"samples": ["5", 0], "vae": ["1", 2]}},
        "7": {"class_type": "SaveImage", "inputs": {"images": ["6", 0], "filename_prefix": "otto/bg"}},
    }


async def _cancel(base: str, pid: str) -> None:
    """Drop our job so a given-up picture does not hold the queue for the next child.

    09-29: without this, a 48.9 s cold first picture made the next four wait
    behind it and all timed out, though each alone took 4.7 s.
    """
    try:
        async with httpx.AsyncClient(timeout=3) as http:
            await http.post(f"{base}/queue", json={"delete": [pid]})
            running = (await http.get(f"{base}/queue")).json().get("queue_running", [])
            if any(item[1] == pid for item in running):
                await http.post(f"{base}/interrupt", json={"prompt_id": pid})
    except Exception:                    # best effort — the deadline already answered the child
        pass


async def background(scene: str, style: str = "felt", mode: str = "story") -> bytes:
    """PNG bytes. Raises ComfyError; the caller owns the deadline and cancelling cancels the job."""
    return await run(workflow(scene, random.randrange(2 ** 31), style, mode), front=True)


# ── characters: img2img from a posed mannequin (docs/캐릭터_생성_규격.md §8) ──────────
#
# 치영's 09-28 experiment (Krea2): starting from a mannequin keeps the pose the rig needs
# (A-pose arms apart from the body · four separated legs). 09-29 on this PC's SDXL + Lightning:
# a *coloured* mannequin kept its blue shirt whatever the child asked for, so the templates
# are colourless grey; denoise 0.85 (human) gave the asked-for character and kept the arms out,
# 0.8 (four legs) keeps the legs apart. Two samples each — measure more before trusting it.

# Wool felt (10-05 · eval/bench_felt_character.py: 4 subjects × 2 seeds, cut-out 8/8 both styles, p50 4.2 s both)
CHAR_STYLE = (", soft wool felt and fabric craft 3D children's picture book character, visible felt fibers and stitched "
              "edges, cute rounded shapes, warm pastel colors, isolated on plain pure white background, no shadow, no text")
# frame · card · backdrop: 09-29 live, the octopus came on a square paper card and was cut out card and all
CHAR_NEG = ("text, letters, watermark, photo, photorealistic, blurry, ugly, scary, dark, horror, "
            "background scenery, frame, border, card, backdrop, circle behind, colored background, "
            "multiple characters, nudity, blood, weapon, gore")
CHAR_POSE = {
    "human": "front view, full body, both arms stretched out diagonally downward away from the body in an A-pose",
    "quad": "side view facing right, full body, standing on four clearly separated legs",
    "blob": "front view, full body, simple round shape",
}
# blob 0.9 — 09-29 live: drawn from nothing, octopus and monster came on beige / grey paper
# backdrops the cut-out could not remove; a grey round mannequin on white keeps the white
CHAR_DENOISE = {"human": 0.85, "quad": 0.8, "blob": 0.9}


async def upload(png: bytes, name: str) -> str:
    """Put a picture in ComfyUI's input folder; returns the name LoadImage wants."""
    base = settings.comfy_url.rstrip("/")
    try:
        async with httpx.AsyncClient(timeout=10) as http:
            r = await http.post(f"{base}/upload/image", files={"image": (name, png, "image/png")},
                                data={"overwrite": "true"})
    except httpx.HTTPError as e:
        raise ComfyError(f"upload network: {type(e).__name__}") from e
    if r.status_code != 200:
        raise ComfyError(f"upload HTTP {r.status_code}")
    j = r.json()
    return (j["subfolder"] + "/" if j.get("subfolder") else "") + j["name"]


def character_workflow(subject: str, rig: str, seed: int, template: str | None, style: str = "felt") -> dict:
    st = styles(style)
    wf = workflow("", seed, style)
    wf["2"]["inputs"]["text"] = f"a cute {subject} {'drawing' if style == 'crayon' else 'puppet'}, {CHAR_POSE[rig]}{st['char']}"
    wf["3"]["inputs"]["text"] = st["char_neg"]
    wf["7"]["inputs"]["filename_prefix"] = "otto/char"
    if template is None:                       # blob: nothing to keep, plain text-to-image
        wf["4"]["inputs"].update({"width": 1024, "height": 1024})
        return wf
    wf["10"] = {"class_type": "LoadImage", "inputs": {"image": template}}
    wf["11"] = {"class_type": "VAEEncode", "inputs": {"pixels": ["10", 0], "vae": ["1", 2]}}
    wf["5"]["inputs"].update({"latent_image": ["11", 0], "denoise": CHAR_DENOISE[rig]})
    del wf["4"]
    return wf


async def _forget(base: str, pid: str) -> None:
    """Drop the history entry — for a redraw it holds the child's drawing (in the prompt) and the result."""
    try:
        async with httpx.AsyncClient(timeout=3) as http:
            await http.post(f"{base}/history", json={"delete": [pid]})
    except Exception:
        pass


def _modal_headers() -> dict:
    return {"Modal-Key": settings.redraw_modal_key, "Modal-Secret": settings.redraw_modal_secret}


async def _run_modal(wf: dict) -> bytes:
    """The redraw on our Modal GPU (backend/modal_gpu/ · #460 ⑥). The drawing rides in this one POST body
    and Modal does not store a Server's requests (docs/그림생성_API_조사_410.md §0-5 ④). Errors name only
    the kind of failure — never the body, which holds the child's drawing."""
    base = settings.redraw_modal_url.rstrip("/")
    try:
        async with httpx.AsyncClient(timeout=30) as http:
            while True:
                r = await http.post(f"{base}/draw", json={"workflow": wf}, headers=_modal_headers())
                if r.status_code != 503:
                    break
                await asyncio.sleep(1)          # no container up yet: the proxy turns us away instead of queuing
    except httpx.HTTPError as e:
        raise ComfyError(f"modal network: {type(e).__name__}") from None
    if r.status_code != 200:
        raise ComfyError(f"modal HTTP {r.status_code}")
    try:
        return base64.b64decode(r.json()["png_base64"])
    except (ValueError, KeyError, TypeError) as e:
        raise ComfyError(f"modal answer: {type(e).__name__}") from None


_WAKE_EVERY_S = 10.0
_woke_at = 0.0
_wakes: set = set()                             # running wake tasks, held so they are not collected


async def _wake() -> None:
    try:
        async with httpx.AsyncClient(timeout=5) as http:
            await http.get(f"{settings.redraw_modal_url.rstrip('/')}/wake", headers=_modal_headers())
    except Exception:
        pass                                    # a wake is a hint — the redraw itself still waits for a container


def wake_redraw() -> None:
    """Ask the Modal GPU to be up soon, without waiting. Called on every picture-diary turn so the GPU stays
    up while the child draws and the redraw does not pay a cold start (§0-5 ②: kept up 4/4 within 45 s,
    woken once at the start 4/4 but only because the GPU came fast that hour). No-op when Modal is off."""
    global _woke_at
    if not settings.redraw_modal_url:
        return
    now = time.monotonic()
    if now - _woke_at < _WAKE_EVERY_S:
        return
    _woke_at = now
    task = asyncio.get_running_loop().create_task(_wake())
    _wakes.add(task)
    task.add_done_callback(_wakes.discard)


async def run(wf: dict, front: bool = False) -> bytes:
    """Queue one graph and return its first PNG. Cancelling cancels the ComfyUI job.

    A graph that returns its picture through OttoReturnImageB64 (the redraw) is read from the
    history entry, and that entry is deleted whatever happens — nothing of it stays on PC2.
    With REDRAW_MODAL_URL set, that graph goes to our Modal GPU instead (_run_modal).
    [front] puts the job at the head of ComfyUI's queue — a picture a child is waiting for
    (background · character) goes ahead of redraws already queued (#32, 10-02).
    """
    base = settings.comfy_url.rstrip("/")
    in_memory = any(n["class_type"] == "OttoReturnImageB64" for n in wf.values())
    if in_memory and settings.redraw_modal_url:
        return await _run_modal(wf)
    pid = None
    try:
        async with httpx.AsyncClient(timeout=10) as http:
            r = await http.post(f"{base}/prompt", json={"prompt": wf, "front": front})
            if r.status_code != 200:
                raise ComfyError(f"prompt HTTP {r.status_code}")
            pid = r.json()["prompt_id"]
            while True:
                hist = (await http.get(f"{base}/history/{pid}")).json()
                if pid in hist:
                    break
                await asyncio.sleep(0.2)
            entry = hist[pid]
            if entry.get("status", {}).get("status_str") == "error":
                raise ComfyError("generation failed")
            for node in entry["outputs"].values():
                for b64 in node.get("otto_png", []):
                    return base64.b64decode(b64)
                for img in node.get("images", []):
                    v = await http.get(f"{base}/view?{urllib.parse.urlencode(img)}")
                    return v.content
    except httpx.HTTPError as e:
        raise ComfyError(f"network: {type(e).__name__}") from e
    except asyncio.CancelledError:
        if pid:
            await asyncio.shield(_cancel(base, pid))
        raise
    finally:
        if in_memory and pid:
            await asyncio.shield(_forget(base, pid))
    raise ComfyError("no image in output")


# ── redraw: the child's own drawing → an Otto drawing (issue #32 · guidelines/1 §1-5) ───
#
# The drawing goes in as base64 (comfy_nodes/otto_memory.py) — never /upload/image, which
# would leave it in ComfyUI's input folder. Same cut-paper style as the characters.

# 09-30, three drawn-by-script pieces × 0.6~0.95: at 0.6~0.8 a line drawing on white comes
# back as itself; from 0.9 it becomes an Otto drawing that keeps the layout — but only on
# some seeds. Six samples, no real child drawings yet — eval/results.md 09-30 before changing.
REDRAW_DENOISE = 0.9

# The picture diary draws in colored pencil, not cut paper (진웅 · 09-30 · #32): same model,
# only words and the number change. 4 drawings × 3 seeds on PC2 — 11 of 12 kept the child's
# layout (the side-on dog stayed side-on), 4.2~6.9 s, no extra VRAM. 0.9 drifted (the dog
# turned to face front), 0.6~0.75 copied. docs/review/일기모드_0930_redraw/ on his branch.
DIARY_STYLE = (", colored pencil sketch, loose hand-drawn pencil outlines, light colored pencil shading, "
               "gentle storybook sketch, isolated on plain pure white paper background, no shadow, no text")
DIARY_NEG = ("text, letters, watermark, photo, photorealistic, 3d render, cut paper, collage, felt, "
             "blurry, ugly, scary, dark, horror, background scenery, frame, border, card, colored background, "
             "multiple subjects, nudity, blood, weapon, gore")
DIARY_DENOISE = 0.85

# A diary background piece (#168 · 10-06 진웅): the whole board with its bands washed, drawn as a wide
# scene and not cut out. 4 backgrounds × 3 seeds on PC2 — 0.9 kept ground height · sea · sky bands and
# filled the scene; 0.85 kept the layout 12/12 but stayed flat. With 8 steps there is nothing between:
# 0.82–0.888 is one schedule, 0.889–1.0 another (eval/results.md 10-06 · eval/diary_bg_1006).
DIARY_BG_STYLE = (", colored pencil drawing of a wide landscape, children's picture diary illustration, "
                  "loose hand-drawn colored pencil strokes, light colored pencil shading on white paper, "
                  "soft and gentle, simple, empty scene, no characters, no people, no animals, no text")
DIARY_BG_NEG = ("text, letters, watermark, photo, photorealistic, 3d render, cut paper, collage, felt, "
                "blurry, ugly, scary, dark, horror, frame, border, people, person, child, animal, character, "
                "nudity, blood, weapon, gore")
DIARY_BG_DENOISE = 0.9


def redraw_workflow(subject: str, seed: int, drawing_b64: str, denoise: float | None = None,
                    mode: str = "story", role: str | None = None, style: str = "felt") -> dict:
    diary = mode == "diary"
    scene = role == "background"
    if denoise is None:
        denoise = DIARY_BG_DENOISE if scene else DIARY_DENOISE if diary else REDRAW_DENOISE
    wf = workflow("", seed)
    if scene:
        wf["2"]["inputs"]["text"] = f"{subject}{DIARY_BG_STYLE}"
        wf["3"]["inputs"]["text"] = DIARY_BG_NEG
    else:
        # the diary keeps colored pencil whatever the book's style; story · co-op follow the book's style
        st = styles(style)
        wf["2"]["inputs"]["text"] = f"a cute {subject}, full view{DIARY_STYLE if diary else st['char']}"
        wf["3"]["inputs"]["text"] = DIARY_NEG if diary else st["char_neg"]
    wf["10"] = {"class_type": "OttoLoadImageB64", "inputs": {"png_base64": drawing_b64}}
    wf["11"] = {"class_type": "VAEEncode", "inputs": {"pixels": ["10", 0], "vae": ["1", 2]}}
    wf["5"]["inputs"].update({"latent_image": ["11", 0], "denoise": denoise})
    wf["7"] = {"class_type": "OttoReturnImageB64", "inputs": {"images": ["6", 0]}}
    del wf["4"]
    return wf


async def warm_up() -> None:
    """Load the models once at server start. Cold, the first picture took 48.9 s (09-29);
    warm, 4.6-4.7 s. Without this the first child of the day always gets the preset."""
    try:
        await background("a sunny meadow with a small hill")
    except Exception:
        pass                             # ComfyUI not up yet — /image falls back to presets anyway
