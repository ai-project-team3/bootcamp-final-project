# -*- coding: utf-8 -*-
"""말로 짓는 인형극 — 일기 모드 · 부모 협업 모드 그림 생성 (ComfyUI HTTP API · krea2 turbo)

gen_assets.py 와 **같은 모델 · 같은 스타일 문자열**을 쓴다. 그래야 새 그림이 기존 그림과 한 세트로 보인다.

무엇을 만드나
  - 일상 장소 배경 6장 (가로) — 일기 모드는 상상 세계가 아니라 아이의 실제 하루다.
    실제 앱은 아이가 말한 장소로 세션 중에 배경을 만든다(CLAUDE.md 규칙 8). 데모는 이 6장으로 흉내 낸다.
  - 일기 미션 소품 — 하루에 생긴 흔적(모래 · 물감)과 건넬 것(블록 · 그림책 · 반창고)
  - 등장인물 프리셋 — 아이가 안 그렸을 때 고르는 것
  - 부모 협업 모드 UI 배지 — 부모 띠의 버튼 두 개와 시작 버튼
  - 번갈아 짓기 표시 — 누가 지은 쪽인지 책에 남기는 작은 표시 (채점처럼 보이면 안 되므로 아주 작게)

쓰는 법
  python gen_diary.py            # 전부
  python gen_diary.py bg_playground prop_sand   # 이름을 주면 그것만
"""
import json, time, urllib.request, urllib.parse, os, sys, random

API = os.environ.get("COMFY_URL", "http://127.0.0.1:8188").rstrip("/")  # 다른 PC 의 ComfyUI 는 COMFY_URL (ART.md)
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "drawable")
os.makedirs(OUT, exist_ok=True)

# gen_assets.py 와 글자 하나까지 같은 스타일 — 한 세트로 보이게 하는 유일한 방법이다
STYLE = ("soft felt fabric and paper-craft 3D children's picture book illustration, cute, rounded shapes, "
         "warm pastel colors, clean, gentle lighting, no text, no letters, high quality")
BG_STYLE = "wide landscape, no characters, no people, no animals, " + STYLE
CUT_STYLE = "single subject centered, full body, front view, isolated on plain pure white background, no shadow, " + STYLE
# 일기 모드는 취침 전에 하루를 돌아보는 기능이라 배경을 늦은 오후 빛으로 묶는다
DAY_BG = "late afternoon golden hour light, long soft shadows, calm and cozy, " + BG_STYLE

JOBS = [
    # ── 일상 장소 배경 6장 (아이가 말한 곳에 맞춰 고른다) ──────────────
    ("bg_playground", 1344, 768,
     "a children's playground made of soft felt fabric, felt slide, swings, sandbox, small climbing frame, "
     "felt grass and a low fence, " + DAY_BG),
    ("bg_daycare", 1344, 768,
     "a cozy kindergarten classroom made of soft felt fabric, low wooden tables and tiny chairs, "
     "felt building blocks on a rug, crayon drawings pinned on the wall, a bookshelf with picture books, " + DAY_BG),
    ("bg_grandma", 1344, 768,
     "a warm grandmother's living room made of soft felt fabric, patterned floor cushion, low table with a teapot, "
     "an old wooden cabinet, potted plant by the window, " + DAY_BG),
    ("bg_home", 1344, 768,
     "a cozy family living room made of soft felt fabric, soft sofa, round rug, toy basket, "
     "a small table lamp, window with curtains, " + DAY_BG),
    ("bg_park", 1344, 768,
     "a neighborhood park made of soft felt fabric, big felt tree, wooden bench, walking path, "
     "flower beds, a small pond in the distance, " + DAY_BG),
    ("bg_mart", 1344, 768,
     "a small friendly grocery store aisle made of soft felt fabric, shelves with felt fruit and snack boxes, "
     "a tiny shopping cart, bright clean floor, " + DAY_BG),

    # ── 미션 1 흔적 — 하루에 묻은 것 (장소에 맞춰 바뀐다) ──────────────
    ("prop_sand", 1024, 1024,
     "a small pile of pale yellow felt sand grains, loose scattered sand clump, " + CUT_STYLE),
    ("prop_paint", 1024, 1024,
     "a small colorful felt paint smudge blob, bright blue and red poster paint stain shape, " + CUT_STYLE),

    # ── 미션 2 건넬 것 — 아이가 말한 것에서 나온다 ────────────────────
    ("prop_block", 1024, 1024,
     "a single cute felt toy building block cube, primary red and yellow, rounded corners, " + CUT_STYLE),
    ("prop_picturebook", 1024, 1024,
     "a cute small felt picture book, closed, colorful cover with a tiny star, " + CUT_STYLE),
    ("prop_bandaid", 1024, 1024,
     "a cute felt adhesive bandage plaster with a tiny heart on it, " + CUT_STYLE),

    # ── 등장인물 프리셋 — 아이가 안 그렸을 때 고른다 ──────────────────
    ("dp_teacher", 1024, 1024,
     "cute felt doll of a kind kindergarten teacher, warm smile, short hair, simple apron over a blouse, "
     "upper body, " + CUT_STYLE),
    ("dp_friend_g", 1024, 1024,
     "cute felt doll of a small girl child friend, two short pigtails, yellow t-shirt, smiling, "
     "upper body, " + CUT_STYLE),
    ("dp_friend_b", 1024, 1024,
     "cute felt doll of a small boy child friend, short dark hair, green t-shirt, smiling, "
     "upper body, " + CUT_STYLE),

    # ── 부모 협업 모드 ────────────────────────────────────────────
    ("ic_coop", 1024, 1024,
     "cute felt badge icon showing a grown-up hand and a small child hand holding one speech bubble together, "
     "warm coral and cream felt, round badge, " + CUT_STYLE),
    ("ic_ask_again", 1024, 1024,
     "cute felt badge icon of a circular refresh arrow around a small speech bubble, soft blue felt, "
     "round badge, " + CUT_STYLE),
    ("ic_my_turn", 1024, 1024,
     "cute felt badge icon of a raised hand with a small speech bubble, soft yellow felt, round badge, " + CUT_STYLE),

    # ── 번갈아 짓기 표시 — 아주 작게 · 채점처럼 보이면 안 된다 (협업 설계 §6) ──
    ("mk_child", 1024, 1024,
     "tiny cute felt sticker of a small child face in a circle, minimal, flat, " + CUT_STYLE),
    ("mk_parent", 1024, 1024,
     "tiny cute felt sticker of a grown-up face in a circle, minimal, flat, " + CUT_STYLE),
]

CUTOUTS = {name for name, w, h, _ in JOBS if w == h}


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
                    print(f"[{label}] error (try {t+1}):", json.dumps(st.get("messages", []))[:400], flush=True)
                    break
                outs = h[pid].get("outputs", {})
                imgs = [i for o in outs.values() for i in o.get("images", [])]
                if imgs:
                    print(f"[{label}] done in {time.time()-t0:.0f}s -> {imgs[0]['filename']}", flush=True)
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
    q = urllib.parse.urlencode({"filename": img["filename"], "subfolder": img.get("subfolder", ""), "type": "output"})
    data = urllib.request.urlopen(API + "/view?" + q, timeout=120).read()
    boundary = "----puppet" + str(random.randint(1, 10**9))
    name = img["filename"]
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"image\"; filename=\"{name}\"\r\nContent-Type: image/png\r\n\r\n").encode() + data + f"\r\n--{boundary}\r\nContent-Disposition: form-data; name=\"overwrite\"\r\n\r\ntrue\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(API + "/upload/image", data=body, headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})
    r = json.load(urllib.request.urlopen(req, timeout=120))
    return (r.get("subfolder", "") + "/" if r.get("subfolder") else "") + r["name"]


for _ in range(60):
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
        print(f"[{name}] skip (already exists)", flush=True)
        continue
    img = run(txt2img(prompt, w, h, name, random.randint(1, 2**31)), name)
    if not img:
        failed.append(name); continue
    if name in CUTOUTS:
        up = upload_output_as_input(img)
        cut = run(bgremove(up, name), name + ":cut")
        if cut:
            img = cut
        else:
            print(f"[{name}] cutout failed - saving with background", flush=True)
    download(img, dest)
    made.append(name)
    print(f"[{name}] saved -> {dest}", flush=True)

print(f"\n== done: {len(made)} made, {len(failed)} failed ==", flush=True)
if made:
    print("made:", " ".join(made), flush=True)
if failed:
    print("FAILED:", " ".join(failed), flush=True)
