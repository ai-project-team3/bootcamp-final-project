# -*- coding: utf-8 -*-
"""일기 모드 배경 · 소품 더하기 (ComfyUI HTTP API · krea2 turbo)

`gen_diary.py` 가 만든 일상 장소는 여섯 곳(놀이터 · 어린이집 · 할머니 집 · 집 · 공원 · 마트)뿐이라,
아이가 "키즈카페 갔어" · "병원 갔어" 라고 말하면 그림 없이 색 배경으로 떨어졌다 (9/21).
자주 말하는 네 곳을 더하고, 일기 모드의 "가방"(미션 1에 나온다) 그림도 같이 만든다.

쓰는 법
  python gen_diary_more.py
"""
import json, time, urllib.request, urllib.parse, os, sys, random

API = os.environ.get("COMFY_URL", "http://127.0.0.1:8188").rstrip("/")  # 다른 PC 의 ComfyUI 는 COMFY_URL (ART.md)
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "drawable")
os.makedirs(OUT, exist_ok=True)

STYLE = ("soft felt fabric and paper-craft 3D children's picture book illustration, cute, rounded shapes, "
         "warm pastel colors, clean, gentle lighting, no text, no letters, high quality")
CUT_STYLE = "single subject centered, full body, front view, isolated on plain pure white background, no shadow, " + STYLE

BG_STYLE = ("wide children's picture book background illustration, soft felt fabric and paper-craft 3D, "
            "warm pastel colors, gentle evening light, cozy, no people, no text, no letters, high quality")

JOBS = [
    ("bg_kidscafe", 1280, 768, "an indoor kids cafe playroom with a ball pit, soft play blocks and small slides, " + BG_STYLE),
    # 첫 시도는 벽화만 가득한 놀이방이 나와서 "병원"으로 보이지 않았다 — 접수대 · 키 재는 자 · 소독약 통을 넣어 다시 만든다 (9/21)
    ("bg_hospital", 1280, 768, "the inside of a small children's clinic: a wooden reception counter with a bell, "
     "a height measuring chart on the wall, a row of empty waiting chairs, a first aid box and a plant, "
     "bright and calm, not scary, " + BG_STYLE),
    ("bg_pool",     1280, 768, "a sunny shallow children's swimming pool with floats and a beach ball, " + BG_STYLE),
    ("bg_zoo",      1280, 768, "a cheerful zoo path with fences, a giraffe and an elephant far away, leafy trees, " + BG_STYLE),
    ("prop_bag",    1024, 1024, "a cute small child's backpack, red and yellow, standing upright, " + CUT_STYLE),
]


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
    # 배경은 오려내지 않는다 — 배경을 투명하게 만들면 남는 것이 없다
    cut = None if name.startswith("bg_") else run(bgremove(upload_output_as_input(img), name), name + ":cut")
    download(cut or img, dest)
    made.append(name)
    print(f"[{name}] saved  ({len(made)}/{len(JOBS)})", flush=True)

print(f"\n== done: {len(made)} made, {len(failed)} failed ==", flush=True)
if failed:
    print("FAILED:", " ".join(failed), flush=True)
