# -*- coding: utf-8 -*-
"""말로 짓는 인형극 — 데모 앱 그림 생성 (ComfyUI HTTP API · krea2 turbo)
배경 4장(가로) + 캐릭터/소품 9장(정사각, 흰 배경) → 캐릭터는 BiRefNet으로 배경 제거.
결과는 OUT 폴더에 이름별 PNG로 저장.
"""
import json, time, urllib.request, urllib.parse, os, sys, random

API = "http://127.0.0.1:8188"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "drawable")
os.makedirs(OUT, exist_ok=True)

STYLE = ("soft felt fabric and paper-craft 3D children's picture book illustration, cute, rounded shapes, "
         "warm pastel colors, clean, gentle lighting, no text, no letters, high quality")
BG_STYLE = "wide landscape, no characters, no people, no animals, " + STYLE
CUT_STYLE = "single subject centered, full body, front view, isolated on plain pure white background, no shadow, " + STYLE

JOBS = [
    # 배경 (가로)
    ("bg_start", 1344, 768, "cozy puppet theater stage with red velvet curtains made of felt, warm golden spotlight, small wooden stage floor, soft bokeh, " + BG_STYLE),
    ("bg_space", 1344, 768, "outer space made of soft felt fabric, deep purple and navy night sky, fluffy felt planets, glittering felt stars, a little moon, " + BG_STYLE),
    ("bg_sea",   1344, 768, "underwater world made of soft felt fabric, turquoise water, felt coral and seaweed, tiny bubbles, sunlight rays from above, sandy bottom, " + BG_STYLE),
    ("bg_dino",  1344, 768, "prehistoric dinosaur land made of soft felt fabric, green felt meadow, small felt volcano, palm trees, blue sky with fluffy felt clouds, " + BG_STYLE),
    # 캐릭터 · 소품 (정사각, 흰 배경 → 배경 제거)
    ("mascot",     1024, 1024, "cute round yellow felt mascot character with big shiny black eyes, rosy pink cheeks, small orange beak, two little orange antenna tufts on top, big friendly smile, " + CUT_STYLE),
    ("hero_glasses", 1024, 1024, "cute paper puppet child character, short brown hair, round glasses, light blue t-shirt with a small green dinosaur print, blue pants, standing, smiling, " + CUT_STYLE),
    ("hero_blue",  1024, 1024, "cute paper puppet child character, short brown hair, no glasses, royal blue t-shirt with a small green dinosaur print, blue pants, standing, smiling, " + CUT_STYLE),
    ("dino_trex",  1024, 1024, "cute green felt toy tyrannosaurus rex, big head, tiny arms, side view, smiling, " + CUT_STYLE),
    ("dino_long",  1024, 1024, "cute green felt toy brachiosaurus with a very long neck, side view, smiling, " + CUT_STYLE),
    ("dino_horn",  1024, 1024, "cute green felt toy triceratops with three horns and a frill, side view, smiling, " + CUT_STYLE),
    ("rocket",     1024, 1024, "cute red and cream felt toy rocket ship with yellow fins and a round window, upright, " + CUT_STYLE),
    ("turtle",     1024, 1024, "cute green felt toy sea turtle with a patterned shell, side view, smiling, " + CUT_STYLE),
    ("train",      1024, 1024, "cute red and blue felt toy steam train engine, side view, " + CUT_STYLE),
    # 2차 — 새 친구 카드 9장 · 선물 2장 (정사각 · 배경 제거)
    ("nc_alien",   1024, 1024, "cute green felt alien with three big eyes and little antennae, " + CUT_STYLE),
    ("nc_meteor",  1024, 1024, "cute felt meteor rock with a smiling face and a sparkly tail, " + CUT_STYLE),
    ("nc_wind",    1024, 1024, "cute felt cloud with puffed cheeks blowing wind, swirly wind lines, " + CUT_STYLE),
    ("nc_octopus", 1024, 1024, "cute pink felt octopus with big eyes and curly tentacles, " + CUT_STYLE),
    ("nc_shark",   1024, 1024, "cute friendly blue felt shark with a big smile, " + CUT_STYLE),
    ("nc_mermaid", 1024, 1024, "cute felt mermaid doll with a teal tail and wavy hair, smiling, " + CUT_STYLE),
    ("nc_babydino",1024, 1024, "cute yellow felt baby dinosaur hatching from a cracked egg, " + CUT_STYLE),
    ("nc_monkey",  1024, 1024, "cute brown felt monkey holding a banana, smiling, " + CUT_STYLE),
    ("nc_volcano", 1024, 1024, "cute small felt volcano with a friendly face and orange felt lava, " + CUT_STYLE),
    ("gift_book",  1024, 1024, "cute felt puzzle piece badge in green with a tiny star, " + CUT_STYLE),
    ("gift_crayon",1024, 1024, "cute felt rainbow crayon, chunky, with a rainbow arc behind it, " + CUT_STYLE),
]
CUTOUTS = {"nc_alien", "nc_meteor", "nc_wind", "nc_octopus", "nc_shark", "nc_mermaid", "nc_babydino", "nc_monkey", "nc_volcano", "gift_book", "gift_crayon", "mascot", "hero_glasses", "hero_blue", "dino_trex", "dino_long", "dino_horn", "rocket", "turtle", "train"}


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
                if st.get("status_str") == "error" or st.get("completed") is False and st.get("status_str") == "error":
                    print(f"[{label}] error (try {t+1}):", json.dumps(st.get("messages", []))[:400], flush=True)
                    break
                outs = h[pid].get("outputs", {})
                imgs = [i for o in outs.values() for i in o.get("images", [])]
                if imgs:
                    print(f"[{label}] done in {time.time()-t0:.0f}s → {imgs[0]['filename']}", flush=True)
                    return imgs[0]
                if st.get("completed"):
                    break
            if time.time() - t0 > 600:
                print(f"[{label}] timeout", flush=True)
                break
    return None


def download(img, dest):
    q = urllib.parse.urlencode({"filename": img["filename"], "subfolder": img.get("subfolder", ""), "type": img.get("type", "output")})
    data = urllib.request.urlopen(API + "/view?" + q, timeout=120).read()
    open(dest, "wb").write(data)


def upload_output_as_input(img):
    # 서버 출력 파일을 input으로 다시 올린다 (LoadImage용)
    q = urllib.parse.urlencode({"filename": img["filename"], "subfolder": img.get("subfolder", ""), "type": "output"})
    data = urllib.request.urlopen(API + "/view?" + q, timeout=120).read()
    boundary = "----puppet" + str(random.randint(1, 10**9))
    name = img["filename"]
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"image\"; filename=\"{name}\"\r\nContent-Type: image/png\r\n\r\n").encode() + data + f"\r\n--{boundary}\r\nContent-Disposition: form-data; name=\"overwrite\"\r\n\r\ntrue\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(API + "/upload/image", data=body, headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})
    r = json.load(urllib.request.urlopen(req, timeout=120))
    return (r.get("subfolder", "") + "/" if r.get("subfolder") else "") + r["name"]


# 서버가 뜰 때까지 기다린다
for _ in range(60):
    try:
        get("/system_stats"); break
    except Exception:
        time.sleep(5)
else:
    print("server not reachable", flush=True); sys.exit(1)

only = set(sys.argv[1:])
for name, w, h, prompt in JOBS:
    if only and name not in only:
        continue
    dest = os.path.join(OUT, name + ".png")
    if os.path.exists(dest):
        print(f"[{name}] exists, skip", flush=True)
        continue
    img = run(txt2img(prompt, w, h, name, seed=random.randint(1, 2**31)), name)
    if not img:
        continue
    if name in CUTOUTS:
        inp = upload_output_as_input(img)
        cut = run(bgremove(inp, name), name + " cut")
        if cut:
            download(cut, dest)
            continue
        print(f"[{name}] bg removal failed — saving raw", flush=True)
    download(img, dest)
print("ALL DONE", flush=True)
