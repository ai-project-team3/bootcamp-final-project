"""Measure what a 4-step Lightning LoRA does to background generation time.

Runs the current 28-step recipe and the 4-step LoRA recipe back to back on the
same ComfyUI process, same seed, same size, so the two numbers are comparable.
The 9/20 measurement (20.1s at 1344x768) was taken on a different day, so the
baseline is re-run here rather than quoted.

Lightning needs cfg ~1.0 and sgm_uniform; feeding it the 28-step settings
produces a washed-out image and would make the comparison meaningless.

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
LORA = "sdxl_lightning_4step_lora.safetensors"

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
# own seed. Both recipes walk the same seed list, so they stay comparable.
SEEDS = [SEED + i for i in range(RUNS)]


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


def base_workflow(prefix, seed):
    return {
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


def lightning_workflow(prefix, seed):
    wf = base_workflow(prefix, seed)
    wf["8"] = {"class_type": "LoraLoaderModelOnly", "inputs": {
        "model": ["1", 0], "lora_name": LORA, "strength_model": 1.0}}
    wf["5"]["inputs"].update({
        "model": ["8", 0],
        "steps": 4,
        "cfg": 1.0,
        "sampler_name": "euler",
        "scheduler": "sgm_uniform",
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


def bench(label, builder):
    times = []
    peak = 0.0
    for i, seed in enumerate(SEEDS):
        t = run_once(builder(f"{label}_{i}", seed), f"{label}_{i}")
        peak = max(peak, vram_used_mb())
        times.append(t)
        print("  %-10s %d회차 %6.2f초" % (label, i + 1, t))
    # The first run of each recipe pays a one-off cost — loading the checkpoint,
    # or loading the LoRA on top of it. Report both so the warm number, which is
    # what a session actually sees, is not hidden by it.
    warm = times[1:] or times
    warm_avg = sum(warm) / len(warm)
    print("  %-10s 첫 회 %.2f초 · 이후 평균 %.2f초 · VRAM peak %.0f MiB"
          % (label, times[0], warm_avg, peak))
    return warm_avg, times, peak


def main():
    print("대기 중 VRAM: %.0f MiB" % vram_used_mb())
    print("\n[기준] SDXL base · 28 steps · cfg 6.5 · dpmpp_2m/karras")
    base_avg, base_times, base_peak = bench("base28", base_workflow)

    print("\n[Lightning] 4 steps · cfg 1.0 · euler/sgm_uniform")
    fast_avg, fast_times, fast_peak = bench("light4", lightning_workflow)

    print("\n" + "=" * 52)
    print("배경 1344x768 · %d회씩 · 같은 seed" % RUNS)
    print("  28 steps   %6.2f초   VRAM %.0f MiB" % (base_avg, base_peak))
    print("  4 steps    %6.2f초   VRAM %.0f MiB" % (fast_avg, fast_peak))
    print("  배속       %6.2f배   VRAM 차이 %+.0f MiB" % (base_avg / fast_avg, fast_peak - base_peak))
    print("\n그림은 assets/bench/ 에 있습니다 — 눈으로 비교해야 채택 여부가 정해집니다.")


if __name__ == "__main__":
    main()
