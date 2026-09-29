"""ComfyUI on our GPU. One background, the 09-21 recipe: SDXL base + Lightning 8-step LoRA.

Numbers behind the recipe (results.md 09-21 · 09-25): 3.74 s alone, 3.86 s with
large-v3 resident. 4-step was 7.5x faster but lost the cut-paper look, so 8 it is.
The style strings are the measured ones (assets/tools/bench_lightning.py) — change
them and the timing and the look are no longer the measured ones.

⚠️ The model has no safety filter. Nothing from here goes to a child unchecked —
routers/image.py runs the check.
"""
import asyncio
import random
import urllib.parse

import httpx

from ..config import settings

BG_STYLE = (", cut paper collage landscape, layered torn construction paper, flat 2d shapes, "
            "warm crayon-box colors, children's picture book, no characters, no people, no text")
NEG = ("text, letters, words, watermark, signature, photo, photorealistic, 3d render, "
       "felt, fabric, plush, clay, blurry, ugly, scary, dark, horror, "
       "nudity, blood, weapon, gore")      # last line added for the product; not in the bench
W, H = 1344, 768


class ComfyError(Exception):
    pass


def workflow(scene: str, seed: int) -> dict:
    return {
        "1": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": settings.image_ckpt}},
        "8": {"class_type": "LoraLoaderModelOnly", "inputs": {
            "model": ["1", 0], "lora_name": settings.image_lora, "strength_model": 1.0}},
        "2": {"class_type": "CLIPTextEncode", "inputs": {"text": scene + BG_STYLE, "clip": ["1", 1]}},
        "3": {"class_type": "CLIPTextEncode", "inputs": {"text": NEG, "clip": ["1", 1]}},
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


async def background(scene: str) -> bytes:
    """PNG bytes. Raises ComfyError; the caller owns the deadline and cancelling cancels the job."""
    return await run(workflow(scene, random.randrange(2 ** 31)))


# ── characters: img2img from a posed mannequin (docs/캐릭터_생성_규격.md §8) ──────────
#
# 치영's 09-28 experiment (Krea2): starting from a mannequin keeps the pose the rig needs
# (A-pose arms apart from the body · four separated legs). 09-29 on this PC's SDXL + Lightning:
# a *coloured* mannequin kept its blue shirt whatever the child asked for, so the templates
# are colourless grey; denoise 0.85 (human) gave the asked-for character and kept the arms out,
# 0.8 (four legs) keeps the legs apart. Two samples each — measure more before trusting it.

CHAR_STYLE = (", cut paper collage, layered torn construction paper, flat 2d shapes, warm crayon-box colors, "
              "children's picture book character, isolated on plain pure white background, no shadow, no text")
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


def character_workflow(subject: str, rig: str, seed: int, template: str | None) -> dict:
    wf = workflow("", seed)
    wf["2"]["inputs"]["text"] = f"a cute {subject} puppet, {CHAR_POSE[rig]}{CHAR_STYLE}"
    wf["3"]["inputs"]["text"] = CHAR_NEG
    wf["7"]["inputs"]["filename_prefix"] = "otto/char"
    if template is None:                       # blob: nothing to keep, plain text-to-image
        wf["4"]["inputs"].update({"width": 1024, "height": 1024})
        return wf
    wf["10"] = {"class_type": "LoadImage", "inputs": {"image": template}}
    wf["11"] = {"class_type": "VAEEncode", "inputs": {"pixels": ["10", 0], "vae": ["1", 2]}}
    wf["5"]["inputs"].update({"latent_image": ["11", 0], "denoise": CHAR_DENOISE[rig]})
    del wf["4"]
    return wf


async def run(wf: dict) -> bytes:
    """Queue one graph and return its first PNG. Cancelling cancels the ComfyUI job."""
    base = settings.comfy_url.rstrip("/")
    pid = None
    try:
        async with httpx.AsyncClient(timeout=10) as http:
            r = await http.post(f"{base}/prompt", json={"prompt": wf})
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
                for img in node.get("images", []):
                    v = await http.get(f"{base}/view?{urllib.parse.urlencode(img)}")
                    return v.content
    except httpx.HTTPError as e:
        raise ComfyError(f"network: {type(e).__name__}") from e
    except asyncio.CancelledError:
        if pid:
            await asyncio.shield(_cancel(base, pid))
        raise
    raise ComfyError("no image in output")


async def warm_up() -> None:
    """Load the models once at server start. Cold, the first picture took 48.9 s (09-29);
    warm, 4.6-4.7 s. Without this the first child of the day always gets the preset."""
    try:
        await background("a sunny meadow with a small hill")
    except Exception:
        pass                             # ComfyUI not up yet — /image falls back to presets anyway
