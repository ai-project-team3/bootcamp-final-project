# -*- coding: utf-8 -*-
"""주인공 하의 변형 생성 — 치마 · 반바지 (ComfyUI HTTP API · krea2 turbo)

gen_heroes.py 가 만든 27장은 전부 **긴바지**다. 골라서 만들기에서 하의를 고를 수 있게 하려면
치마 · 반바지 변형이 있어야 한다 (9/21 사용자 요청).

  머리 3 × 옷 3 × 안경 3 × 하의 2(치마 · 반바지) = 54장

파일 이름은 기존과 이어진다 — 긴바지는 접미사 없이 `hero_short_blue_round`,
치마 · 반바지는 `hero_short_blue_round_skirt` · `_shorts`. 그래야 이미 있는 27장을 그대로 쓴다.

**성별은 그림에 넣지 않는다.** 성별은 고를 때 하의 · 머리의 기본값만 정하고(남=바지, 여=치마),
그림은 머리 · 옷 · 안경 · 하의 네 가지로만 갈린다. 그래야 장수가 두 배로 늘지 않는다.

쓰는 법
  python gen_heroes_bottom.py              # 없는 것만
  python gen_heroes_bottom.py hero_short_blue_round_skirt
"""
import json, time, urllib.request, urllib.parse, os, sys, random

API = os.environ.get("COMFY_URL", "http://127.0.0.1:8188").rstrip("/")  # 다른 PC 의 ComfyUI 는 COMFY_URL (ART.md)
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "drawable")
os.makedirs(OUT, exist_ok=True)

STYLE = ("soft felt fabric and paper-craft 3D children's picture book illustration, cute, rounded shapes, "
         "warm pastel colors, clean, gentle lighting, no text, no letters, high quality")
CUT_STYLE = "single subject centered, full body, front view, isolated on plain pure white background, no shadow, " + STYLE

HAIR = {"short": "short brown hair", "long": "long brown hair", "tied": "brown hair tied in a ponytail"}
SHIRT = {"red": "red", "blue": "royal blue", "yellow": "yellow"}
GL = {"none": "no glasses", "round": "round glasses", "square": "square glasses"}
BOTTOM = {"skirt": "a blue pleated skirt", "shorts": "blue shorts"}

JOBS = []
for h in HAIR:
    for c in SHIRT:
        for g in GL:
            for b in BOTTOM:
                JOBS.append((
                    f"hero_{h}_{c}_{g}_{b}", 1024, 1024,
                    f"cute paper puppet child character, {HAIR[h]}, {GL[g]}, "
                    f"{SHIRT[c]} t-shirt with a small green dinosaur print, {BOTTOM[b]}, "
                    "standing straight, arms at sides, smiling, " + CUT_STYLE))


def post(path, data):
    req = urllib.request.Request(API + path, data=json.dumps(data).encode(), headers={"Content-Type": "application/json"})
    return json.load(urllib.request.urlopen(req, timeout=60))


def get(path):
    return json.load(urllib.request.urlopen(API + path, timeout=60))


def txt2img(prompt, w, h, prefix, seed):
    return {
        "1": {"class_type": "UNETLoader", "inputs": {"unet_name": "krea2_turbo_nvfp4.safetensors", "weight_dtype": "default"}},
        "2": {"class_type": "CLIPLoader", "inputs": {"clip_name": "qwen3vl_4b_fp8_scaled.safetensors", "type": "krea2"}},
        "3": {"class_type": "VAELoader", "inputs": {"vae_name": "qwen_image_vae.safetensors"}},
        "4": {"class_type": "CLIPTextEncode", "inputs": {"text": prompt, "clip": ["2", 0]}},
        "5": {"class_type": "ConditioningZeroOut", "inputs": {"conditioning": ["4", 0]}},
        "6": {"class_type": "EmptyLatentImage", "inputs": {"width": w, "height": h, "batch_size": 1}},
        "7": {"class_type": "KSampler", "inputs": {"model": ["1", 0], "positive": ["4", 0], "negative": ["5", 0], "latent_image": ["6", 0],
                                                    "seed": seed, "steps": 8, "cfg": 1, "sampler_name": "er_sde", "scheduler": "simple", "denoise": 1}},
        "8": {"class_type": "VAEDecode", "inputs": {"samples": ["7", 0], "vae": ["3", 0]}},
        "9": {"class_type": "SaveImage", "inputs": {"images": ["8", 0], "filename_prefix": "puppet/" + prefix}},
    }


def bgremove(filename, prefix):
    return {
        "1": {"class_type": "LoadImage", "inputs": {"image": filename}},
        "2": {"class_type": "LoadBackgroundRemovalModel", "inputs": {"bg_removal_name": "birefnet.safetensors"}},
        "3": {"class_type": "RemoveBackground", "inputs": {"bg_removal_model": ["2", 0], "image": ["1", 0]}},
        "3b": {"class_type": "InvertMask", "inputs": {"mask": ["3", 0]}},
        "4": {"class_type": "JoinImageWithAlpha", "inputs": {"image": ["1", 0], "alpha": ["3b", 0]}},
        "5": {"class_type": "SaveImage", "inputs": {"images": ["4", 0], "filename_prefix": "puppet/" + prefix + "_cut"}},
    }


def run(workflow, label, tries=2):
    for t in range(tries):
        pid = post("/prompt", {"prompt": workflow})["prompt_id"]
        t0 = time.time()
        while True:
            time.sleep(3)
            h = get("/history/" + pid)
            if pid in h:
                st = h[pid].get("status", {})
                if st.get("status_str") == "error":
                    print(f"[{label}] error (try {t+1}):", json.dumps(st.get("messages", []))[:300], flush=True)
                    break
                outs = h[pid].get("outputs", {})
                imgs = [i for o in outs.values() for i in o.get("images", [])]
                if imgs:
                    print(f"[{label}] done in {time.time()-t0:.0f}s", flush=True)
                    return imgs[0]
                if st.get("completed"):
                    break
            if time.time() - t0 > 600:
                print(f"[{label}] timeout", flush=True)
                break
    return None


def download(img, dest):
    q = urllib.parse.urlencode({"filename": img["filename"], "subfolder": img.get("subfolder", ""), "type": img.get("type", "output")})
    open(dest, "wb").write(urllib.request.urlopen(API + "/view?" + q, timeout=120).read())


def upload_output_as_input(img):
    q = urllib.parse.urlencode({"filename": img["filename"], "subfolder": img.get("subfolder", ""), "type": "output"})
    data = urllib.request.urlopen(API + "/view?" + q, timeout=120).read()
    boundary = "----puppet" + str(random.randint(1, 10**9))
    name = img["filename"]
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"image\"; filename=\"{name}\"\r\nContent-Type: image/png\r\n\r\n").encode() + data + f"\r\n--{boundary}\r\nContent-Disposition: form-data; name=\"overwrite\"\r\n\r\ntrue\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(API + "/upload/image", data=body, headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})
    r = json.load(urllib.request.urlopen(req, timeout=120))
    return (r.get("subfolder", "") + "/" if r.get("subfolder") else "") + r["name"]


for _ in range(80):
    try:
        get("/system_stats"); break
    except Exception:
        time.sleep(5)
else:
    print("server not reachable", flush=True); sys.exit(1)

only = set(sys.argv[1:])
made, failed = [], []
for name, w, h, prompt in JOBS:
    if only and name not in only:
        continue
    dest = os.path.join(OUT, name + ".png")
    if os.path.exists(dest) and not only:
        continue
    img = run(txt2img(prompt, w, h, name, random.randint(1, 2**31)), name)
    if not img:
        failed.append(name); continue
    cut = run(bgremove(upload_output_as_input(img), name), name + ":cut")
    download(cut or img, dest)
    made.append(name)
    print(f"[{name}] saved  ({len(made)}/{len(JOBS)})", flush=True)

print(f"\n== done: {len(made)} made, {len(failed)} failed ==", flush=True)
if failed:
    print("FAILED:", " ".join(failed), flush=True)
