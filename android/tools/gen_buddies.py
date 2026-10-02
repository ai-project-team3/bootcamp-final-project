# -*- coding: utf-8 -*-
"""같이 갈 친구 그림 — 장소마다 셋 (ComfyUI HTTP API · krea2 turbo)

전에는 공룡 셋(`dino_trex` · `dino_long` · `dino_horn`)밖에 없어서, 아이가 우주나 바닷속을 골라도
"공룡도 데려갈래!" 하고 공룡이 로켓에 탔다 (9/21 지적). 장소마다 그곳에 있을 법한 친구 셋을 만든다.

  우주 3 + 바닷속 3 + 눈 오는 데 3 = 9장   (공룡 나라 3장은 이미 있다)

파일 이름은 `Model.kt` 의 `DinoKind.art` 와 같아야 한다 — `bud_alien` · `bud_dolphin` …

쓰는 법
  python gen_buddies.py            # 없는 것만
  python gen_buddies.py bud_alien
"""
import json, time, urllib.request, urllib.parse, os, sys, random

API = os.environ.get("COMFY_URL", "http://127.0.0.1:8188").rstrip("/")  # 다른 PC 의 ComfyUI 는 COMFY_URL (ART.md)
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "drawable")
os.makedirs(OUT, exist_ok=True)

STYLE = ("soft felt fabric and paper-craft 3D children's picture book illustration, cute, rounded shapes, "
         "warm pastel colors, clean, gentle lighting, no text, no letters, high quality")
CUT_STYLE = "single subject centered, full body, front view, isolated on plain pure white background, no shadow, " + STYLE

BUDDIES = [
    ("bud_alien",     "a friendly little alien creature with three big round eyes and two soft antennae, mint green body"),
    ("bud_robot",     "a small friendly boxy robot with a square head, round bolt eyes and a little antenna, pale blue metal"),
    ("bud_star",      "a cute smiling yellow star character with tiny arms and legs, glowing softly"),
    ("bud_dolphin",   "a cute smiling baby dolphin, light blue, round friendly eyes"),
    ("bud_seahorse",  "a cute baby seahorse with a curled tail, coral pink, smiling"),
    ("bud_starfish",  "a cute smiling orange starfish with five arms and two round eyes"),
    ("bud_snowman",   "a small cheerful snowman with a carrot nose, twig arms and a red scarf"),
    ("bud_bear",      "a cute chubby baby polar bear cub sitting, soft white fur, smiling"),
    ("bud_penguin",   "a cute little baby penguin standing, round belly, orange beak and feet, smiling"),
]

JOBS = [(name, 1024, 1024, f"{desc}, " + CUT_STYLE) for name, desc in BUDDIES]


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
