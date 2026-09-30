"""ComfyUI nodes that move a picture in and out **without touching disk**.

Why (issue #32 · guidelines/1 §1-5): a child's drawing may reach our GPU only to be redrawn,
and must not be kept. ComfyUI's own path writes both ends to disk — /upload/image puts the
input in input/, SaveImage puts the result in output/ — and neither has a delete endpoint.
These two nodes carry the picture as base64 inside the prompt and the history entry instead;
the backend deletes the history entry as soon as it has read it (app/image/comfy.py `run`).

Install on the ComfyUI machine (PC2): copy this file into ComfyUI/custom_nodes/ and restart
ComfyUI. Without it the redraw graph is rejected and /image answers preset — the drawing
still never lands on disk.
"""
import base64
import io

import numpy as np
import torch
from PIL import Image


class OttoLoadImageB64:
    @classmethod
    def INPUT_TYPES(cls):
        return {"required": {"png_base64": ("STRING", {"multiline": False})}}

    RETURN_TYPES = ("IMAGE",)
    FUNCTION = "load"
    CATEGORY = "otto"

    def load(self, png_base64):
        im = Image.open(io.BytesIO(base64.b64decode(png_base64))).convert("RGB")
        arr = np.asarray(im).astype(np.float32) / 255.0
        return (torch.from_numpy(arr)[None, ...],)


class OttoReturnImageB64:
    @classmethod
    def INPUT_TYPES(cls):
        return {"required": {"images": ("IMAGE",)}}

    RETURN_TYPES = ()
    FUNCTION = "give"
    OUTPUT_NODE = True
    CATEGORY = "otto"

    def give(self, images):
        out = []
        for img in images:
            arr = np.clip(img.cpu().numpy() * 255.0, 0, 255).astype(np.uint8)
            buf = io.BytesIO()
            Image.fromarray(arr).save(buf, "PNG")
            out.append(base64.b64encode(buf.getvalue()).decode())
        return {"ui": {"otto_png": out}}


NODE_CLASS_MAPPINGS = {"OttoLoadImageB64": OttoLoadImageB64, "OttoReturnImageB64": OttoReturnImageB64}
