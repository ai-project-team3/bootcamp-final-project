"""「오또가 그려 줘」 (kind redraw) on a Modal GPU in our own account (#460 ⑥ · 10-13 lead decision: 「우리 서버」).

Off by default — the backend only calls this when REDRAW_MODAL_URL is set (app/config.py).

Why a Modal *Server* (`@app.server`) and not `@modal.fastapi_endpoint`: Modal keeps a Web Function's inputs
and outputs for up to 7 days, while a Server's request and response are not stored, only proxied to the
container (modal.com/docs/guide/security, retention table, checked 10-13). Measured 10-13 on L4
(docs/그림생성_API_조사_410.md §0-5): warm 3.0~3.3 s a redraw, memory snapshot restores.

What keeps the drawing off disk here:
- ComfyUI's nodes run in this process (no ComfyUI server, no /upload, no history, no output folder);
  the drawing comes in and goes out as base64 through comfy_nodes/otto_memory.py
- the model volume is mounted read-only
- web.py logs no body and answers an error with the exception's class name only

    modal deploy backend/modal_gpu/otto_redraw_modal.py
    (once) modal run backend/modal_gpu/otto_redraw_modal.py::download_models

Calls need Modal proxy auth (Modal-Key / Modal-Secret headers) — REDRAW_MODAL_KEY / REDRAW_MODAL_SECRET.
"""
import os
import sys
import time
from pathlib import Path

import modal

HERE = Path(__file__).resolve().parent
COMFY = "/root/comfy/ComfyUI"
COMFY_REF = "v0.9.2"             # pinned so a rebuild runs the node code this was measured with

image = (
    modal.Image.debian_slim(python_version="3.11")
    .apt_install("git")
    .pip_install("fastapi[standard]==0.115.4", "uvicorn==0.32.0", "huggingface_hub[hf_transfer]==0.26.2")
    .pip_install("torch==2.7.1", "torchvision==0.22.1", index_url="https://download.pytorch.org/whl/cu128")
    .run_commands(f"git clone --depth 1 --branch {COMFY_REF} https://github.com/comfyanonymous/ComfyUI {COMFY}",
                  f"pip install -r {COMFY}/requirements.txt")
    .env({"HF_HUB_ENABLE_HF_TRANSFER": "1"})
    .add_local_file(str(HERE.parent / "comfy_nodes" / "otto_memory.py"), f"{COMFY}/custom_nodes/otto_memory.py")
    .add_local_file(str(HERE / "web.py"), "/root/otto_web.py")
)

MODELS = [
    ("stabilityai/stable-diffusion-xl-base-1.0", "sd_xl_base_1.0.safetensors", "checkpoints"),
    ("ByteDance/SDXL-Lightning", "sdxl_lightning_8step_lora.safetensors", "loras"),
]
volume = modal.Volume.from_name("otto-models", create_if_missing=True)
app = modal.App("otto-redraw", image=image)


@app.function(volumes={"/models": volume}, timeout=1800)
def download_models():
    from huggingface_hub import hf_hub_download
    for repo, name, sub in MODELS:
        os.makedirs(f"/models/{sub}", exist_ok=True)
        hf_hub_download(repo, name, local_dir=f"/models/{sub}")
    volume.commit()


def _boot_comfy() -> dict:
    sys.argv = ["main.py", "--disable-auto-launch"]
    sys.path.insert(0, COMFY)
    os.chdir(COMFY)
    for _, name, sub in MODELS:
        dst = f"{COMFY}/models/{sub}/{name}"
        if not os.path.exists(dst):
            os.symlink(f"/models/{sub}/{name}", dst)
    import nodes                                     # ComfyUI's own node code, same as PC2
    sys.path.insert(0, f"{COMFY}/custom_nodes")
    import otto_memory
    return {**nodes.NODE_CLASS_MAPPINGS, **otto_memory.NODE_CLASS_MAPPINGS}


_LOADERS = ("CheckpointLoaderSimple", "LoraLoaderModelOnly")


def run_graph(classes: dict, wf: dict, keep: dict) -> str:
    """A small executor for our fixed graphs: resolve ["id", n] links, call each node once, return the
    OttoReturnImageB64 picture. `keep` holds the loader outputs so the weights stay on the GPU."""
    import torch
    out: dict = {}

    def value(nid):
        if nid in out:
            return out[nid]
        node = wf[nid]
        key = None
        if node["class_type"] in _LOADERS:
            key = (node["class_type"], repr(sorted((k, v) for k, v in node["inputs"].items() if not isinstance(v, list))))
            if key in keep:
                out[nid] = keep[key]
                return out[nid]
        args = {k: (value(v[0])[v[1]] if isinstance(v, list) else v) for k, v in node["inputs"].items()}
        cls = classes[node["class_type"]]
        res = getattr(cls(), cls.FUNCTION)(**args)
        if isinstance(res, dict) and "result" in res:
            res = res["result"]
        out[nid] = res
        if key:
            keep[key] = res
        return res

    try:
        with torch.inference_mode():                 # ComfyUI's executor runs every node under this
            for node in wf.values():
                if node["class_type"] == "OttoReturnImageB64":
                    src = node["inputs"]["images"]
                    images = value(src[0])[src[1]]
                    return classes["OttoReturnImageB64"]().give(images)["ui"]["otto_png"][0]
        raise ValueError("no OttoReturnImageB64 in graph")
    finally:
        out.clear()                                  # the decoded drawing and its latents go now


WARM = {
    "1": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": MODELS[0][1]}},
    "8": {"class_type": "LoraLoaderModelOnly", "inputs": {"model": ["1", 0], "lora_name": MODELS[1][1], "strength_model": 1.0}},
    "2": {"class_type": "CLIPTextEncode", "inputs": {"text": "a meadow", "clip": ["1", 1]}},
    "4": {"class_type": "EmptyLatentImage", "inputs": {"width": 512, "height": 512, "batch_size": 1}},
    "5": {"class_type": "KSampler", "inputs": {"model": ["8", 0], "positive": ["2", 0], "negative": ["2", 0],
                                                 "latent_image": ["4", 0], "seed": 1, "steps": 1, "cfg": 1.0,
                                                 "sampler_name": "euler", "scheduler": "sgm_uniform", "denoise": 1}},
    "6": {"class_type": "VAEDecode", "inputs": {"samples": ["5", 0], "vae": ["1", 2]}},
    "7": {"class_type": "OttoReturnImageB64", "inputs": {"images": ["6", 0]}},
}


# scaledown 60 s: the backend wakes this on every picture-diary turn (≈ 25 s apart), so it stays up while
# a child draws and goes away a minute after the diary ends (§0-5 ②: kept up, 4/4 within 45 s)
@app.server(gpu="L4", volumes={"/models": volume.read_only()}, scaledown_window=60, max_containers=2,
            startup_timeout=300, enable_memory_snapshot=True, experimental_options={"enable_gpu_snapshot": True})
class Redraw:
    @modal.enter(snap=True)
    def load(self):
        self.classes = _boot_comfy()
        self.keep: dict = {}
        run_graph(self.classes, WARM, self.keep)     # weights on the GPU — this state is what the snapshot holds
        self.t_loaded = time.time()

    @modal.enter(snap=False)
    def serve(self):
        import threading

        import uvicorn
        sys.path.insert(0, "/root")
        from otto_web import make_app

        state = {"restored": time.time() - self.t_loaded > 5}
        web = make_app(lambda wf: run_graph(self.classes, wf, self.keep), state)
        cfg = uvicorn.Config(web, host="0.0.0.0", port=8000, log_level="warning", access_log=False)
        threading.Thread(target=uvicorn.Server(cfg).run, daemon=True).start()
