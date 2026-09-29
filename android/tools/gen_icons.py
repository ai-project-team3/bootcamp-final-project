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

ICON = "single object centered, isolated on plain pure white background, no shadow, soft felt fabric and paper-craft 3D children's app icon, cute, rounded, warm pastel colors, clean, no text"
JOBS = [
    # 화면 · 카드 아이콘
    ("ic_mic",       1024, 1024, "cute felt microphone, " + ICON),
    ("ic_dials",     1024, 1024, "cute felt control panel with three colorful round knobs, " + ICON),
    ("ic_hand",      1024, 1024, "cute felt open hand, palm forward, " + ICON),
    ("ic_hammer",    1024, 1024, "cute felt toy squeaky hammer with a striped head, " + ICON),
    ("ic_feather",   1024, 1024, "cute soft felt feather, pastel yellow and pink, " + ICON),
    ("ic_magnifier", 1024, 1024, "cute felt magnifying glass with a wooden handle, " + ICON),
    ("ic_good",      1024, 1024, "cute round yellow felt smiley face badge, big happy smile, " + ICON),
    ("ic_bad",       1024, 1024, "cute felt crayon pencil with an eraser, orange, " + ICON),
    ("ic_color",     1024, 1024, "cute felt painter palette with colorful felt paint dots and a small brush, " + ICON),
    ("ic_size",      1024, 1024, "cute felt wooden ruler with big marks, " + ICON),
    ("ic_face",      1024, 1024, "three small round felt face stickers: happy, surprised, sleepy, arranged together, " + ICON),
    ("ic_bored",     1024, 1024, "round felt face with a bored flat mouth and half-closed eyes, light yellow, " + ICON),
    ("ic_angry",     1024, 1024, "round red felt face with angry eyebrows and a frown, " + ICON),
    ("ic_sad",       1024, 1024, "round light blue felt face with a sad mouth and a tear, " + ICON),
    ("ic_play",      1024, 1024, "two cute felt balloons tied together, red and yellow, " + ICON),
    ("ic_gift",      1024, 1024, "cute felt gift box with a big ribbon bow, pink and yellow, " + ICON),
    ("ic_invite",    1024, 1024, "cute felt envelope with a heart seal, " + ICON),
    ("ic_hair_short",1024, 1024, "cute felt short brown hair wig, front view, " + ICON),
    ("ic_hair_long", 1024, 1024, "cute felt long brown hair wig, front view, " + ICON),
    ("ic_hair_tied", 1024, 1024, "cute felt brown hair wig with a side ponytail and a red ribbon, front view, " + ICON),
    ("ic_shirt_red", 1024, 1024, "cute small red felt t-shirt, flat, " + ICON),
    ("ic_shirt_blue",1024, 1024, "cute small royal blue felt t-shirt, flat, " + ICON),
    ("ic_shirt_yellow",1024,1024,"cute small yellow felt t-shirt, flat, " + ICON),
    ("ic_glasses_none",1024,1024,"a cute felt closed eyes smiling face with no glasses, small round face, " + ICON),
    ("ic_glasses_round",1024,1024,"cute round felt eyeglasses, brown frame, " + ICON),
    ("ic_glasses_square",1024,1024,"cute square felt eyeglasses, brown frame, " + ICON),
    ("ic_speaker",   1024, 1024, "cute felt speaker icon with two sound waves, " + ICON),
    ("ic_books",     1024, 1024, "cute stack of three felt books, red blue yellow, " + ICON),
    ("ic_draw",      1024, 1024, "cute felt crayon drawing a wavy line, " + ICON),
    ("ic_next",      1024, 1024, "cute felt round arrow pointing right, orange, " + ICON),
    # 미션 소품 (장소별)
    ("prop_hose",    1024, 1024, "cute felt toy water cannon hose spraying a little water, blue and yellow, " + ICON),
    ("prop_sponge",  1024, 1024, "cute yellow felt sponge with soap bubbles, " + ICON),
    ("prop_mud",     1024, 1024, "cute brown felt mud splat blob with a grumpy little face, " + ICON),
    ("prop_seaweed", 1024, 1024, "cute green felt seaweed tangle blob, " + ICON),
    ("prop_shell",   1024, 1024, "cute open felt clam shell, pink, empty inside, " + ICON),
    ("prop_pearl",   1024, 1024, "cute glowing felt pearl ball with sparkles, white and pale pink, " + ICON),
    ("prop_pond",    1024, 1024, "cute small felt pond with a dry cracked bottom and a lily pad, top-down view, " + ICON),
    ("prop_smoke",   1024, 1024, "cute soft grey felt smoke puff, " + ICON),
    ("prop_splash",  1024, 1024, "cute blue felt water splash with three droplets, " + ICON),
    ("prop_umbrella",1024, 1024, "cute open felt umbrella, red with yellow dots, " + ICON),
    ("prop_sparkle", 1024, 1024, "three cute felt sparkle stars, yellow, " + ICON),
    ("prop_lava",    1024, 1024, "cute orange felt lava blob with a friendly face, " + ICON),
]
CUTOUTS = {j[0] for j in JOBS}

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
