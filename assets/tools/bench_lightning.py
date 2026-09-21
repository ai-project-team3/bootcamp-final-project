"""Measure what a step-distilled LoRA does to background generation time —
and, more importantly, to the cut-paper look this product is built on.

The 4-step run on 09-21 was 7.5x faster at identical VRAM, but the paper grain
softened into something painted and the negative prompt stopped holding. So the
question is no longer "is it faster" but "which setting keeps the style".

Runs the current 28-step recipe and each LoRA variant back to back in the same
ComfyUI process, same seeds, same size.

Lightning needs cfg ~1.0 with euler/sgm_uniform; feeding it the 28-step
settings washes the image out and makes the comparison meaningless.

    python assets/tools/bench_lightning.py
"""
import json
import os
import time
import urllib.parse
import urllib.request

API = "http://127.0.0.1:8188"
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.abspath(os.path.join(HERE, "..", "bench"))
os.makedirs(OUT, exist_ok=True)

CKPT = "sd_xl_base_1.0.safetensors"

# The real background prompt, not a toy one — step count interacts with how much
# detail the prompt asks for.
BG = ("a dinosaur land with tall ferns and a smoking volcano far away"
      ", cut paper collage landscape, layered torn construction paper, flat 2d shapes, "
      "warm crayon-box colors, children's picture book, no characters, no people, no text")
NEG = ("text, letters, words, watermark, signature, photo, photorealistic, 3d render, "
       "felt, fabric, plush, clay, blurry, ugly, scary, dark, horror")

W, H, SEED = 1344, 768, 20260921
RUNS = 3
# ComfyUI caches by workflow hash. Re-running an identical graph returns the
# previous image in ~0.25s and the timing means nothing, so every run gets its
# own seed. Every variant walks the same seed list, so they stay comparable.
SEEDS = [SEED + i for i in range(RUNS)]

# (label, lora file or None, steps, strength)
VARIANTS = [
    ("base28",   None,                                   28, None),
    ("l4_s10",   "sdxl_lightning_4step_lora.safetensors", 4, 1.0),
    ("l4_s08",   "sdxl_lightning_4step_lora.safetensors", 4, 0.8),
    ("l8_s10",   "sdxl_lightning_8step_lora.safetensors", 8, 1.0),
    ("l8_s08",   "sdxl_lightning_8step_lora.safetensors", 8, 0.8),
]


def post(path, data):
    req = urllib.request.Request(
        API + path,
        data=json.dumps(data).encode("utf-8"),
        headers={"Content-Type": "application/json"},
    )
    return json.loads(urllib.request.urlopen(req, timeout=600).read())


def get(path):
    return json.loads(urllib.request.urlopen(API + path, timeout=60).read())


def vram_used_mb():
    d = get("/system_stats")
    dev = d["devices"][0]
    return (dev["vram_total"] - dev["vram_free"]) / 2 ** 20


def build(prefix, seed, lora, steps, strength):
    wf = {
        "1": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": CKPT}},
        "2": {"class_type": "CLIPTextEncode", "inputs": {"text": BG, "clip": ["1", 1]}},
        "3": {"class_type": "CLIPTextEncode", "inputs": {"text": NEG, "clip": ["1", 1]}},
        "4": {"class_type": "EmptyLatentImage", "inputs": {"width": W, "height": H, "batch_size": 1}},
        "5": {"class_type": "KSampler", "inputs": {
            "model": ["1", 0], "positive": ["2", 0], "negative": ["3", 0], "latent_image": ["4", 0],
            "seed": seed, "steps": 28, "cfg": 6.5,
            "sampler_name": "dpmpp_2m", "scheduler": "karras", "denoise": 1}},
        "6": {"class_type": "VAEDecode", "inputs": {"samples": ["5", 0], "vae": ["1", 2]}},
        "7": {"class_type": "SaveImage", "inputs": {"images": ["6", 0], "filename_prefix": "bench/" + prefix}},
    }
    if lora:
        wf["8"] = {"class_type": "LoraLoaderModelOnly", "inputs": {
            "model": ["1", 0], "lora_name": lora, "strength_model": strength}}
        wf["5"]["inputs"].update({
            "model": ["8", 0], "steps": steps, "cfg": 1.0,
            "sampler_name": "euler", "scheduler": "sgm_uniform",
        })
    return wf


def run_once(wf, name):
    started = time.time()
    pid = post("/prompt", {"prompt": wf})["prompt_id"]
    while True:
        hist = get("/history/" + pid)
        if pid in hist:
            elapsed = time.time() - started
            for node in hist[pid]["outputs"].values():
                for img in node.get("images", []):
                    q = urllib.parse.urlencode(img)
                    data = urllib.request.urlopen(API + "/view?" + q, timeout=180).read()
                    with open(os.path.join(OUT, name + ".png"), "wb") as f:
                        f.write(data)
            return elapsed
        time.sleep(0.2)


def main():
    print("대기 중 VRAM: %.0f MiB\n" % vram_used_mb())
    rows = []
    for label, lora, steps, strength in VARIANTS:
        desc = "28 steps (기준)" if not lora else "%d steps · 강도 %.1f" % (steps, strength)
        print("[%s] %s" % (label, desc))
        times, peak = [], 0.0
        for i, seed in enumerate(SEEDS):
            t = run_once(build("%s_%d" % (label, i), seed, lora, steps, strength),
                         "%s_%d" % (label, i))
            peak = max(peak, vram_used_mb())
            times.append(t)
            print("   %d회차 %6.2f초" % (i + 1, t))
        # The first run of a variant pays a one-off cost (loading the LoRA), so
        # report the warm average — that is what a session actually sees.
        warm = times[1:] or times
        avg = sum(warm) / len(warm)
        print("   첫 회 %.2f초 · 이후 평균 %.2f초 · VRAM %.0f MiB\n" % (times[0], avg, peak))
        rows.append((label, desc, avg, peak))

    base = rows[0][2]
    print("=" * 62)
    print("%-10s %-22s %9s %9s %8s" % ("", "설정", "평균", "VRAM", "배속"))
    for label, desc, avg, peak in rows:
        print("%-10s %-22s %8.2f초 %7.0f MiB %7.2f배" % (label, desc, avg, peak, base / avg))
    print("=" * 62)
    print("\n그림은 assets/bench/ 에 있습니다.")
    print("채택은 시간이 아니라 눈으로 정합니다 — 종이 결이 남아 있는지, 네거티브가 지켜지는지.")


if __name__ == "__main__":
    main()
