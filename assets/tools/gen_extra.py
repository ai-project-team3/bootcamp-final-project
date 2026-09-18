# -*- coding: utf-8 -*-
"""추가로 필요한 그림만 만든다 (gen_icons.py와 같은 그래프 · 같은 그림체).

- bg_snow    : 프리셋에 없는 장소("눈 오는 데")를 말했을 때 새로 만드는 배경 (구현대본 §6)
- prop_*     : 어른 전용 칸의 소원 카드 3장 (구현대본 §2 · S3 해결)

사용: python gen_extra.py            (없는 것만)
      python gen_extra.py bg_snow    (이름을 주면 그것만)
"""
import json, time, urllib.request, urllib.parse, os, sys, random

API = "http://127.0.0.1:8188"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "drawable")
os.makedirs(OUT, exist_ok=True)

STYLE = ("soft felt fabric and paper-craft 3D children's picture book illustration, cute, rounded shapes, "
         "warm pastel colors, clean, gentle lighting, no text, no letters, high quality")
BG_STYLE = "wide landscape, no characters, no people, no animals, " + STYLE
ICON = ("single object centered, isolated on plain pure white background, no shadow, "
        "soft felt fabric and paper-craft 3D children's app icon, cute, rounded, warm pastel colors, clean, no text")

JOBS = [
    # (이름, 폭, 높이, 프롬프트, 배경제거 여부)
    ("bg_snow", 1344, 768,
     "snowy winter plain with soft rolling white hills, snow covered pine trees, "
     "a few gentle snowflakes falling, pale blue sky, " + BG_STYLE, False),
    ("prop_necklace", 1024, 1024,
     "cute felt necklace made of small glowing yellow stars on a string, " + ICON, True),
    ("prop_lamp", 1024, 1024,
     "cute felt lantern glowing warm yellow, small star on top, " + ICON, True),
    ("prop_pocket", 1024, 1024,
     "cute small felt pouch bag with one yellow star peeking out, " + ICON, True),

    # ── v0.8 배경마다 올려 두는 상호작용 오브젝트 (미리 만들어 두고 적재적소에 배치)
    ("obj_rock", 1024, 1024, "cute round grey felt boulder rock with a little moss, " + ICON, True),
    ("obj_cloud", 1024, 1024, "cute fluffy white felt cloud with a sleepy smile, " + ICON, True),
    ("obj_tree", 1024, 1024, "cute round green felt tree with a brown trunk, " + ICON, True),
    ("obj_star", 1024, 1024, "cute glowing yellow felt star with a tiny smile, " + ICON, True),
    ("obj_wind", 1024, 1024, "cute swirling light blue felt wind gust with curly lines, " + ICON, True),
    ("obj_planet", 1024, 1024, "cute small orange felt planet with a pink ring, " + ICON, True),
    ("obj_coral", 1024, 1024, "cute pink and orange felt coral branch, " + ICON, True),
    ("obj_seaweed", 1024, 1024, "cute tall wavy green felt seaweed, " + ICON, True),
    ("obj_bubble", 1024, 1024, "three cute shiny transparent blue felt bubbles, " + ICON, True),
    ("obj_bush", 1024, 1024, "cute round green felt bush with small red berries, " + ICON, True),
    ("obj_pine", 1024, 1024, "cute snow covered green felt pine tree, " + ICON, True),
    ("obj_snowman", 1024, 1024, "cute small felt snowman with a red scarf, " + ICON, True),
    ("obj_flower", 1024, 1024, "cute yellow felt flower with green leaves, " + ICON, True),

    # ── v0.8 대화에서 나온 미션 물체
    ("prop_ink", 1024, 1024, "cute dark purple felt ink splat blob, " + ICON, True),
    ("prop_banana", 1024, 1024, "cute yellow felt banana peel, " + ICON, True),
    ("prop_leaf", 1024, 1024, "cute pile of three green and orange felt leaves, " + ICON, True),
    ("prop_broom", 1024, 1024, "cute small felt broom with a wooden handle, " + ICON, True),
    ("prop_gem", 1024, 1024, "cute shiny light blue felt gemstone, sparkling, " + ICON, True),
    ("prop_strawberry", 1024, 1024, "cute red felt strawberry with green leaves, " + ICON, True),
    ("prop_note", 1024, 1024, "cute colorful felt music notes, " + ICON, True),
    ("prop_heart", 1024, 1024, "cute puffy pink felt heart, " + ICON, True),

    # ── v0.8 부모 모드 그림체 견본 4종 (같은 대상 · 트리케라톱스)
    ("style_felt", 1024, 1024, "a cute triceratops, soft wool needle felt 3D toy, warm pastel, plain white background, no text", False),
    ("style_crayon", 1024, 1024, "a cute triceratops, child's crayon drawing on white paper, wobbly lines, bright colors, no text", False),
    ("style_hanji", 1024, 1024, "a cute triceratops, traditional Korean folk tale illustration, soft watercolor and colored pencil on hanji paper, muted colors, no text", False),
    ("style_water", 1024, 1024, "a cute triceratops, gentle watercolor character illustration, soft washes, white background, no text", False),

    # ── v0.9 함께하는 사람 (첫 화면에서 고름) — 얼굴 · 상반신 카드
    ("ic_p_mom", 1024, 1024, "cute felt doll portrait of a smiling young korean mother with brown hair tied back, upper body, " + ICON, True),
    ("ic_p_dad", 1024, 1024, "cute felt doll portrait of a smiling young korean father with short black hair and glasses, upper body, " + ICON, True),
    ("ic_p_aunt", 1024, 1024, "cute felt doll portrait of a cheerful young korean woman with a bob haircut and a yellow sweater, upper body, " + ICON, True),
    ("ic_p_grandma", 1024, 1024, "cute felt doll portrait of a kind korean grandmother with gray curly hair, upper body, " + ICON, True),
    ("ic_p_grandpa", 1024, 1024, "cute felt doll portrait of a kind korean grandfather with gray hair and a cardigan, upper body, " + ICON, True),
    ("ic_p_friend", 1024, 1024, "cute felt doll portrait of a happy korean kindergarten girl with pigtails, upper body, " + ICON, True),

    # ── v0.8 책장
    ("bg_shelf", 1344, 768, "cozy empty wooden bookshelf with three shelves, front view, warm light, felt and wood craft, children's room, "
     "no books, no text, " + STYLE, False),
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
    open(dest, "wb").write(urllib.request.urlopen(API + "/view?" + q, timeout=120).read())


def upload_output_as_input(img):
    q = urllib.parse.urlencode({"filename": img["filename"], "subfolder": img.get("subfolder", ""), "type": "output"})
    data = urllib.request.urlopen(API + "/view?" + q, timeout=120).read()
    boundary = "----puppet" + str(random.randint(1, 10 ** 9))
    name = img["filename"]
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"image\"; filename=\"{name}\"\r\nContent-Type: image/png\r\n\r\n").encode() \
        + data + f"\r\n--{boundary}\r\nContent-Disposition: form-data; name=\"overwrite\"\r\n\r\ntrue\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(API + "/upload/image", data=body, headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})
    r = json.load(urllib.request.urlopen(req, timeout=120))
    return (r.get("subfolder", "") + "/" if r.get("subfolder") else "") + r["name"]


for _ in range(90):
    try:
        get("/system_stats")
        break
    except Exception:
        time.sleep(5)
else:
    print("server not reachable", flush=True)
    sys.exit(1)

only = set(sys.argv[1:])
for name, w, h, prompt, cut in JOBS:
    if only and name not in only:
        continue
    dest = os.path.join(OUT, name + ".png")
    if os.path.exists(dest):
        print(f"[{name}] exists, skip", flush=True)
        continue
    img = run(txt2img(prompt, w, h, name, seed=random.randint(1, 2 ** 31)), name)
    if not img:
        continue
    if cut:
        inp = upload_output_as_input(img)
        c = run(bgremove(inp, name), name + " cut")
        if c:
            download(c, dest)
            continue
        print(f"[{name}] bg removal failed - saving raw", flush=True)
    download(img, dest)
print("ALL DONE", flush=True)
