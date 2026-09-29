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
    base = settings.comfy_url.rstrip("/")
    wf = workflow(scene, random.randrange(2 ** 31))
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
