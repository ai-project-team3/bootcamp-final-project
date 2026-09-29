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

HAIR={"short":"short brown hair","long":"long brown hair","tied":"brown hair tied in a ponytail"}
SHIRT={"red":"red","blue":"royal blue","yellow":"yellow"}
GL={"none":"no glasses","round":"round glasses","square":"square glasses"}
JOBS=[]
for h in HAIR:
    for c in SHIRT:
        for g in GL:
            JOBS.append((f"hero_{h}_{c}_{g}", 1024, 1024,
                f"cute paper puppet child character, {HAIR[h]}, {GL[g]}, {SHIRT[c]} t-shirt with a small green dinosaur print, blue pants, standing straight, arms at sides, smiling, " + CUT_STYLE))
JOBS += [
    ("prop_fire",  1024, 1024, "cute felt flame fire with a friendly little face, orange and yellow, " + CUT_STYLE),
    ("prop_cloud", 1024, 1024, "cute fluffy felt rain cloud with a smiling face, light grey, " + CUT_STYLE),
    ("prop_well",  1024, 1024, "cute felt stone water well with a little wooden roof and a bucket, " + CUT_STYLE),
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
