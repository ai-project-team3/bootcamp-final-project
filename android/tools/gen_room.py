# -*- coding: utf-8 -*-
"""오또의 방 · 타이틀 · 밤 · 기능 소개 그림 (09-29) — ComfyUI HTTP API · krea2 turbo.

gen_assets.py 와 같은 파이프라인(krea2 → 물건은 BiRefNet 으로 배경 제거).
결과는 인자로 준 폴더에 저장하고, 눈으로 고른 뒤 res/drawable 로 옮긴다.

    python tools/gen_room.py OUT_DIR [이름 ...] [--seeds N]
"""
import json, time, urllib.request, urllib.parse, os, sys, random

API = "http://127.0.0.1:8188"
STYLE = ("soft wool felt and fabric craft 3D children's picture book illustration, visible felt fibers and stitched edges, "
         "cute rounded shapes, warm pastel colors (cream, mustard yellow, coral, teal, sky blue), gentle soft lighting, "
         "cozy, no text, no letters, no numbers, high quality")
BG = "wide landscape, empty, no characters, no people, no animals, " + STYLE
CUT = "single object centered, whole object visible, front view, isolated on plain pure white background, no shadow, no floor, " + STYLE

JOBS = {
    "room_bg": (1344, 768, "interior of a cozy child's playroom seen straight from the front, back wall with cream and butter-yellow vertical striped "
                           "felt wallpaper, a thin wooden baseboard, warm honey wooden plank floor covering the lower quarter of the image, "
                           "a round soft pink felt rug on the floor in the center-left, a few tiny felt bunting flags along the top edge, "
                           "the room is empty with no furniture and nothing on the walls, " + BG),
    "room_window": (1024, 1024, "a cute arched window with a thick warm wooden frame and a cross-shaped mullion, through the glass a bright blue sky, "
                                "a smiling felt sun and fluffy felt clouds, short coral felt curtains tied at both sides, a little flower pot on the sill, " + CUT),
    "room_theater": (1024, 1024, "a small wooden puppet theater booth for children, red velvet felt curtains opened at both sides, a dark stage opening, "
                                 "a golden felt star and mustard scalloped banner on the top, painted wooden base with little stars, " + CUT),
    "room_sofa": (1344, 768, "a cozy small rounded sky-blue felt sofa for two, soft plump seat, one pink cushion and one mustard yellow cushion, "
                             "stubby wooden legs, " + CUT),
    "room_shelf": (768, 1152, "a tall wooden bookshelf with three shelves full of chunky colorful felt picture books standing upright and some leaning, "
                              "a tiny felt plant and a toy star on the top, " + CUT),
    "title_bg": (1344, 768, "a grand puppet theater stage seen from the audience, deep red velvet felt curtains drawn open to both sides and a scalloped "
                            "red valance at the top with gold trim, warm golden spotlight glowing on the empty center of the stage, "
                            "wooden stage floor at the bottom, magical and inviting, " + BG),
    "night_bg": (1344, 768, "a cozy child's bedroom at night, navy blue felt night sky through a big window with a smiling "
                            "crescent felt moon and little felt stars, a warm small lamp glow, a soft bed with a quilt on the right, calm sleepy "
                            "mood, dark blue and lavender tones, " + BG),
    "feat_talk": (1024, 1024, "a cute chunky teal felt microphone toy with little sound wave shapes around it, " + CUT),
    "feat_book": (1024, 1024, "a cute open felt picture book with a colorful felt drawing of a rainbow and a star popping out of the pages, " + CUT),
    "feat_shelf": (1024, 1024, "a cute small stack of three chunky colorful felt books with a little star on top, " + CUT),
    # 09-29 오또의 방 이름표 · 확인 창 모드 아이콘
    "icon_diary": (1024, 1024, "a cute smiling felt sun peeking over a small open felt diary notebook with a crayon drawing, " + CUT),
    "icon_story": (1024, 1024, "a cute felt theater drama mask pair, one smiling mask in coral and one happy mask in mustard, tied with a teal ribbon, " + CUT),
    "icon_coop": (1024, 1024, "a big felt adult hand and a small felt child hand holding a red felt heart together, " + CUT),
    # 09-29 부모 영역 메뉴 · 비밀번호
    "pi_record": (1024, 1024, "a cute felt notebook with a teal cover and a small pencil, a little star sticker on the cover, " + CUT),
    "pi_coop": (1024, 1024, "two overlapping cute felt speech bubbles, one coral and one teal, with tiny hearts, " + CUT),
    "pi_achieve": (1024, 1024, "a cute felt gold medal with a star in the middle hanging on a coral and teal ribbon, " + CUT),
    "pi_settings": (1024, 1024, "a cute chunky felt gear cog in mustard yellow with a small wrench, " + CUT),
    "pi_account": (1024, 1024, "a cute felt name badge card with a simple person silhouette and a small heart, " + CUT),
    "pi_lock": (1024, 1024, "a cute chunky felt padlock in teal with a golden keyhole and a small golden key beside it, " + CUT),
    "pi_home": (1024, 1024, "a cute small felt house with a red roof, a round window and a door, " + CUT),
}
CUTOUTS = {k for k in JOBS if k.startswith(("room_window", "room_theater", "room_sofa", "room_shelf", "feat_", "icon_", "pi_"))}


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
        "9": {"class_type": "SaveImage", "inputs": {"images": ["8", 0], "filename_prefix": "room/" + prefix}},
    }


def bgremove(filename, prefix):
    return {
        "1": {"class_type": "LoadImage", "inputs": {"image": filename}},
        "2": {"class_type": "LoadBackgroundRemovalModel", "inputs": {"bg_removal_name": "birefnet.safetensors"}},
        "3": {"class_type": "RemoveBackground", "inputs": {"bg_removal_model": ["2", 0], "image": ["1", 0]}},
        "3b": {"class_type": "InvertMask", "inputs": {"mask": ["3", 0]}},
        "4": {"class_type": "JoinImageWithAlpha", "inputs": {"image": ["1", 0], "alpha": ["3b", 0]}},
        "5": {"class_type": "SaveImage", "inputs": {"images": ["4", 0], "filename_prefix": "room/" + prefix + "_cut"}},
    }


def run(wf, label):
    pid = post("/prompt", {"prompt": wf})["prompt_id"]
    t0 = time.time()
    while time.time() - t0 < 600:
        time.sleep(2)
        h = get("/history/" + pid)
        if pid in h:
            if h[pid].get("status", {}).get("status_str") == "error":
                print(f"[{label}] error", json.dumps(h[pid]["status"].get("messages", []))[:400], flush=True)
                return None
            imgs = [i for o in h[pid].get("outputs", {}).values() for i in o.get("images", [])]
            if imgs:
                print(f"[{label}] {time.time()-t0:.0f}s", flush=True)
                return imgs[0]
    return None


def fetch(img):
    q = urllib.parse.urlencode({"filename": img["filename"], "subfolder": img.get("subfolder", ""), "type": img.get("type", "output")})
    return urllib.request.urlopen(API + "/view?" + q, timeout=120).read()


def upload(img):
    data = fetch(img)
    b = "----room" + str(random.randint(1, 10**9))
    body = (f"--{b}\r\nContent-Disposition: form-data; name=\"image\"; filename=\"{img['filename']}\"\r\nContent-Type: image/png\r\n\r\n").encode() + data \
        + f"\r\n--{b}\r\nContent-Disposition: form-data; name=\"overwrite\"\r\n\r\ntrue\r\n--{b}--\r\n".encode()
    req = urllib.request.Request(API + "/upload/image", data=body, headers={"Content-Type": f"multipart/form-data; boundary={b}"})
    r = json.load(urllib.request.urlopen(req, timeout=120))
    return (r.get("subfolder", "") + "/" if r.get("subfolder") else "") + r["name"]


if __name__ == "__main__":
    out = sys.argv[1]
    args = sys.argv[2:]
    seeds = 1
    if "--seeds" in args:
        i = args.index("--seeds")
        seeds = int(args[i + 1])
        args = args[:i] + args[i + 2:]
    for _ in range(90):
        try:
            get("/system_stats")
            break
        except Exception:
            time.sleep(5)
    os.makedirs(out, exist_ok=True)
    for name in (args or list(JOBS)):
        w, h, p = JOBS[name]
        for k in range(seeds):
            img = run(txt2img(p, w, h, name, random.randint(1, 2**31)), f"{name}#{k}")
            if not img:
                continue
            if name in CUTOUTS:
                cut = run(bgremove(upload(img), name), name + " cut")
                if cut:
                    img = cut
            open(os.path.join(out, f"{name}_{k}.png"), "wb").write(fetch(img))
    print("ALL DONE", flush=True)
