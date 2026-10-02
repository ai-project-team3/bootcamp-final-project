# -*- coding: utf-8 -*-
"""머리만 바꾼 몸을 만든다 — 이미 있는 몸에서 img2img (ComfyUI HTTP API)

왜 img2img 인가 (9/21)
  머리를 **투명 조각으로 얹는** 방법을 먼저 해 봤다. 구조는 맞았지만 조각마다
  얼굴 구멍의 크기와 모양이 달라 **얼굴 위로 어두운 테두리가 지나갔다.**
  (`gen_hero_parts.py` 로 뽑은 hair_*.png · `check_parts.py` 로 확인)

  그래서 방향을 바꿨다. `gen_hero_parts.py` 가 만든 **몸 9장은 이미 같은 아이**다
  (얼굴/자세/비율을 프롬프트로 못 박았다). 그 그림을 **출발점으로 삼아** 머리만 바꾸면
  얼굴이 유지된다 — 새로 뽑는 것보다 정체성이 훨씬 잘 남는다.

  denoise 는 0.55 — 너무 낮으면 머리가 안 바뀌고, 너무 높으면 다른 아이가 된다.

만드는 것
  body_{색}_{하의}_long   9장
  body_{색}_{하의}_tied   9장
  (짧은 머리는 원본 `body_{색}_{하의}` 가 그대로 쓰인다)

안경은 덮어 얹는다 (`gl_round` · `gl_square`) — 얼굴이 9장 모두 같으니 좌표 하나면 된다.

쓰는 법
  python gen_hero_hair.py
  python gen_hero_hair.py body_blue_pants_long
"""
import json, time, urllib.request, urllib.parse, os, sys, random

API = os.environ.get("COMFY_URL", "http://127.0.0.1:8188").rstrip("/")  # 다른 PC 의 ComfyUI 는 COMFY_URL (ART.md)
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "drawable")
os.makedirs(OUT, exist_ok=True)

STYLE = ("soft felt fabric and paper-craft 3D children's picture book illustration, cute, rounded shapes, "
         "warm pastel colors, clean, gentle lighting, no text, no letters, high quality")
CUT_STYLE = "single subject centered, full body, front view, isolated on plain pure white background, no shadow, " + STYLE

CUT = "single subject centered, isolated on plain pure white background, no shadow, " + STYLE

# 바꾸는 것은 **머리뿐** 이라고 못 박는다. 얼굴 · 옷 · 하의는 건드리지 않는다
KEEP = ("keep the exact same child, same plain round face, same small dot eyes, same tiny smile, "
        "same body, same pose, same t-shirt with the small green dinosaur print on the chest, "
        "same bottom, only the hairstyle changes")
HAIRS = {
    "long": "long dark brown hair falling past the shoulders with a straight fringe",
    "tied": "dark brown hair tied into a ponytail on one side with a small yellow band",
}
COLORS = ["red", "blue", "yellow"]
BOTTOMS = ["pants", "skirt", "shorts"]

# (만들 이름, 출발점 그림, 프롬프트)
JOBS = []
for c in COLORS:
    for b in BOTTOMS:
        for h, hdesc in HAIRS.items():
            JOBS.append((f"body_{c}_{b}_{h}", f"body_{c}_{b}",
                         f"a cute 5 year old child puppet doll with {hdesc}, {KEEP}, " + CUT))

# 0.55 로 해 봤더니 티셔츠의 공룡 무늬가 사라졌다 — 더 낮춘다 (9/21)
DENOISE = 0.42


def post(path, data):
    req = urllib.request.Request(API + path, data=json.dumps(data).encode(), headers={"Content-Type": "application/json"})
    return json.load(urllib.request.urlopen(req, timeout=60))


def get(path):
    return json.load(urllib.request.urlopen(API + path, timeout=60))


def img2img(prompt, image_name, prefix, seed):
    """출발점 그림을 조금만 바꾼다 — 얼굴을 살리려고 denoise 를 낮게 둔다"""
    return {
        "1": {"class_type": "UNETLoader", "inputs": {"unet_name": "krea2_turbo_nvfp4.safetensors", "weight_dtype": "default"}},
        "2": {"class_type": "CLIPLoader", "inputs": {"clip_name": "qwen3vl_4b_fp8_scaled.safetensors", "type": "krea2"}},
        "3": {"class_type": "VAELoader", "inputs": {"vae_name": "qwen_image_vae.safetensors"}},
        "4": {"class_type": "CLIPTextEncode", "inputs": {"text": prompt, "clip": ["2", 0]}},
        "5": {"class_type": "ConditioningZeroOut", "inputs": {"conditioning": ["4", 0]}},
        "10": {"class_type": "LoadImage", "inputs": {"image": image_name}},
        "11": {"class_type": "VAEEncode", "inputs": {"pixels": ["10", 0], "vae": ["3", 0]}},
        "7": {"class_type": "KSampler", "inputs": {"model": ["1", 0], "positive": ["4", 0], "negative": ["5", 0], "latent_image": ["11", 0],
                                                    "seed": seed, "steps": 10, "cfg": 1, "sampler_name": "er_sde", "scheduler": "simple", "denoise": DENOISE}},
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

def upload_file(path, name):
    """로컬 PNG 를 ComfyUI 입력으로 올린다 (img2img 출발점)"""
    data = open(path, "rb").read()
    boundary = "----puppet" + str(random.randint(1, 10**9))
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"image\"; filename=\"{name}\"\r\nContent-Type: image/png\r\n\r\n").encode() + data + f"\r\n--{boundary}\r\nContent-Disposition: form-data; name=\"overwrite\"\r\n\r\ntrue\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(API + "/upload/image", data=body, headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})
    r = json.load(urllib.request.urlopen(req, timeout=120))
    return (r.get("subfolder", "") + "/" if r.get("subfolder") else "") + r["name"]


only = set(sys.argv[1:])
made, failed = [], []
for name, base, prompt in JOBS:
    if only and name not in only:
        continue
    dest = os.path.join(OUT, name + ".png")
    if os.path.exists(dest) and not only:
        continue
    src_path = os.path.join(OUT, base + ".png")
    if not os.path.exists(src_path):
        print(f"[{name}] 출발점 {base} 이 없다"); failed.append(name); continue
    up = upload_file(src_path, base + "_src.png")
    img = run(img2img(prompt, up, name, random.randint(1, 2**31)), name)
    if not img:
        failed.append(name); continue
    cut = run(bgremove(upload_output_as_input(img), name), name + ":cut")
    download(cut or img, dest)
    made.append(name)
    print(f"[{name}] saved  ({len(made)}/{len(JOBS)})", flush=True)

print("")
print("== done: %d made, %d failed ==" % (len(made), len(failed)), flush=True)
if failed:
    print("FAILED:", " ".join(failed), flush=True)
