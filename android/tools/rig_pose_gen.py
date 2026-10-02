# -*- coding: utf-8 -*-
"""뼈대를 붙일 수 있는 자세로 뽑히는가 — 실험 (2026-09-28)

물음: 등장인물을 **정해진 자세로** 생성하면, 관절 자리를 찾지 않고도 자동으로 뼈대를 붙일 수 있나?
      사람형(정면 · 팔 벌린 A-포즈)과 네발형(옆모습 · 다리 넷이 떨어진 자세) 둘을 잰다.

방법: ControlNet 이 없으므로 **마네킹 실루엣을 출발 그림으로** 넣고 Krea2 img2img 로 펠트 인형을 입힌다
      (오또 로고 때 denoise 0.5 에서 글자 모양이 지켜진 것과 같은 방식). 세기를 몇 단계로 바꿔 본다.
      관절 자리는 **틀(TEMPLATES)에 좌표로 박혀 있다** — 재는 쪽(`rig_pose_check.py`)이 그대로 쓴다.

쓰는 법 (ComfyUI 가 켜져 있어야 한다)
  python tools/rig_pose_gen.py              # 틀 두 장 + 생성
  python tools/rig_pose_gen.py --templates  # 틀만 그린다
결과: build/rig_pose/
"""
import io, json, math, os, random, sys, time, urllib.parse, urllib.request
from PIL import Image, ImageDraw

API = os.environ.get("COMFY_URL", "http://127.0.0.1:8188").rstrip("/")  # 다른 PC 의 ComfyUI 는 COMFY_URL (ART.md)
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "build", "rig_pose")
os.makedirs(OUT, exist_ok=True)
N = 1024

STYLE = ("soft felt fabric and paper-craft 3D children's picture book illustration, cute, rounded shapes, "
         "warm pastel colors, clean, gentle lighting, no text, no letters, high quality")

SKIN, SHIRT, PANTS, HAIR, SHOE = (240, 200, 170), (90, 130, 200), (60, 80, 130), (90, 60, 40), (200, 90, 80)
GREEN, GREEN_D, SPOT = (120, 180, 110), (95, 150, 90), (230, 200, 120)

# ── 틀: 모양과 관절 자리 (1024 기준 좌표) ──────────────────────────
TEMPLATES = {
    "human": {
        "prompt": ("a cute 5 year old child puppet doll standing, front view, full body, both arms stretched out "
                   "diagonally downward away from the body in an A-pose, clear empty gap between each arm and the body, "
                   "short sleeve t-shirt, shorts, isolated on plain pure white background, no shadow, " + STYLE),
        # 관절 — 어깨(pivot) · 팔 끝 · 팔을 오려 낼 넓은 상자(몸통 바깥)
        "shoulder_L": (402, 392), "hand_L": (215, 575), "arm_box_L": (0, 360, 396, 720),
        "shoulder_R": (622, 392), "hand_R": (809, 575), "arm_box_R": (628, 360, 1024, 720),
        # 겨드랑이 틈을 볼 자리 — 몸통 바로 바깥 세로줄에서 이 높이 범위가 비어야 팔이 떨어진 것
        "gap_x_L": 388, "gap_x_R": 636, "gap_y": (500, 660),
    },
    "quad": {
        "prompt": ("a cute baby dinosaur puppet, side view facing right, full body, standing on four legs, "
                   "all four legs clearly separated with visible gaps between them, long tail, "
                   "isolated on plain pure white background, no shadow, " + STYLE),
        # 다리 넷 — 엉덩이(pivot) · 오려 낼 상자
        "legs": [(346, 628), (426, 628), (586, 628), (666, 628)],
        "leg_band_y": (660, 800),  # 이 높이에서는 몸통이 없고 다리만 있어야 한다
        "leg_half_w": 38,
        "tail_pivot": (300, 520), "tail_box": (0, 380, 300, 640),
    },
}
# 두 번째 네발형 — 가까운 다리와 먼 다리의 틈을 26 → 56 화소로 (09-28).
# 첫 틀은 세기 0.6 이상에서 모델이 옆모습을 자연스럽게 고치며 앞 · 뒤 다리를 둘씩 포갰다(다리 2개로 재짐)
TEMPLATES["quad2"] = dict(TEMPLATES["quad"], legs=[(310, 628), (420, 628), (580, 628), (690, 628)])


def draw_human():
    t = TEMPLATES["human"]
    im = Image.new("RGB", (N, N), (255, 255, 255)); d = ImageDraw.Draw(im)
    # 팔 — 몸 뒤에 먼저 (어깨에서 대각선 아래로)
    for s, h in [(t["shoulder_L"], t["hand_L"]), (t["shoulder_R"], t["hand_R"])]:
        d.line([s, h], fill=SKIN, width=62)
        sx = s[0] + (h[0] - s[0]) * 0.28; sy = s[1] + (h[1] - s[1]) * 0.28
        d.line([s, (sx, sy)], fill=SHIRT, width=74)            # 반팔 소매
        d.ellipse([h[0] - 40, h[1] - 40, h[0] + 40, h[1] + 40], fill=SKIN)
    d.rounded_rectangle([400, 360, 624, 630], 40, fill=SHIRT)          # 몸통
    d.rectangle([404, 610, 620, 700], fill=PANTS)                     # 반바지
    for x0 in (420, 534):                                             # 다리
        d.rounded_rectangle([x0, 690, x0 + 70, 900], 24, fill=SKIN)
        d.rounded_rectangle([x0 - 8, 880, x0 + 82, 935], 20, fill=SHOE)
    d.ellipse([402, 140, 622, 360], fill=SKIN)                         # 머리
    d.chord([392, 128, 632, 330], 180, 360, fill=HAIR)
    for ex in (470, 554):
        d.ellipse([ex - 10, 250, ex + 10, 272], fill=(40, 30, 30))
    d.arc([482, 285, 542, 320], 20, 160, fill=(170, 80, 70), width=5)
    return im


def draw_quad(kind="quad"):
    t = TEMPLATES[kind]
    im = Image.new("RGB", (N, N), (255, 255, 255)); d = ImageDraw.Draw(im)
    for i, (x, y) in enumerate(t["legs"]):                               # 다리 — 먼 쪽(0·2)은 조금 어둡게
        c = GREEN_D if i in (0, 2) else GREEN
        d.rounded_rectangle([x - 27, y - 30, x + 27, 800], 22, fill=c)
    d.polygon([(300, 470), (95, 505), (300, 585)], fill=GREEN)            # 꼬리
    d.ellipse([270, 390, 730, 650], fill=GREEN)                          # 몸통
    d.polygon([(640, 470), (700, 310), (770, 330), (720, 500)], fill=GREEN)  # 목
    d.ellipse([705, 225, 875, 345], fill=GREEN)                          # 머리
    d.ellipse([805, 262, 823, 282], fill=(40, 30, 30))
    for cx, cy in [(420, 450), (500, 430), (580, 460), (470, 520)]:
        d.ellipse([cx - 22, cy - 16, cx + 22, cy + 16], fill=SPOT)
    return im


def post(path, data):
    req = urllib.request.Request(API + path, data=json.dumps(data).encode(), headers={"Content-Type": "application/json"})
    return json.load(urllib.request.urlopen(req, timeout=60))


def get(path):
    return json.load(urllib.request.urlopen(API + path, timeout=60))


def upload(data, name):
    b = "----rig" + str(random.randint(1, 10**9))
    body = (f"--{b}\r\nContent-Disposition: form-data; name=\"image\"; filename=\"{name}\"\r\nContent-Type: image/png\r\n\r\n").encode() + data + \
           f"\r\n--{b}\r\nContent-Disposition: form-data; name=\"overwrite\"\r\n\r\ntrue\r\n--{b}--\r\n".encode()
    req = urllib.request.Request(API + "/upload/image", data=body, headers={"Content-Type": f"multipart/form-data; boundary={b}"})
    r = json.load(urllib.request.urlopen(req, timeout=120))
    return (r.get("subfolder", "") + "/" if r.get("subfolder") else "") + r["name"]


def fetch(img):
    q = urllib.parse.urlencode({"filename": img["filename"], "subfolder": img.get("subfolder", ""), "type": img.get("type", "output")})
    return urllib.request.urlopen(API + "/view?" + q, timeout=120).read()


def run(wf, label):
    pid = post("/prompt", {"prompt": wf})["prompt_id"]; t0 = time.time()
    while time.time() - t0 < 600:
        time.sleep(2)
        h = get("/history/" + pid)
        if pid in h:
            st = h[pid].get("status", {})
            if st.get("status_str") == "error":
                print(label, "error", json.dumps(st.get("messages", []))[:300]); return None
            imgs = [i for o in h[pid].get("outputs", {}).values() for i in o.get("images", [])]
            if imgs:
                print(f"{label} {time.time()-t0:.0f}s", flush=True); return imgs[0]
    print(label, "timeout"); return None


def img2img(prompt, image, seed, denoise, prefix):
    return {
        "1": {"class_type": "UNETLoader", "inputs": {"unet_name": "krea2_turbo_nvfp4.safetensors", "weight_dtype": "default"}},
        "2": {"class_type": "CLIPLoader", "inputs": {"clip_name": "qwen3vl_4b_fp8_scaled.safetensors", "type": "krea2"}},
        "3": {"class_type": "VAELoader", "inputs": {"vae_name": "qwen_image_vae.safetensors"}},
        "4": {"class_type": "CLIPTextEncode", "inputs": {"text": prompt, "clip": ["2", 0]}},
        "5": {"class_type": "ConditioningZeroOut", "inputs": {"conditioning": ["4", 0]}},
        "10": {"class_type": "LoadImage", "inputs": {"image": image}},
        "11": {"class_type": "VAEEncode", "inputs": {"pixels": ["10", 0], "vae": ["3", 0]}},
        "7": {"class_type": "KSampler", "inputs": {"model": ["1", 0], "positive": ["4", 0], "negative": ["5", 0], "latent_image": ["11", 0],
                                                    "seed": seed, "steps": 10, "cfg": 1, "sampler_name": "er_sde", "scheduler": "simple", "denoise": denoise}},
        "8": {"class_type": "VAEDecode", "inputs": {"samples": ["7", 0], "vae": ["3", 0]}},
        "9": {"class_type": "SaveImage", "inputs": {"images": ["8", 0], "filename_prefix": "rig/" + prefix}},
    }


def bgremove(image, prefix):
    return {
        "1": {"class_type": "LoadImage", "inputs": {"image": image}},
        "2": {"class_type": "LoadBackgroundRemovalModel", "inputs": {"bg_removal_name": "birefnet.safetensors"}},
        "3": {"class_type": "RemoveBackground", "inputs": {"bg_removal_model": ["2", 0], "image": ["1", 0]}},
        "3b": {"class_type": "InvertMask", "inputs": {"mask": ["3", 0]}},
        "4": {"class_type": "JoinImageWithAlpha", "inputs": {"image": ["1", 0], "alpha": ["3b", 0]}},
        "5": {"class_type": "SaveImage", "inputs": {"images": ["4", 0], "filename_prefix": "rig/" + prefix + "_cut"}},
    }


DENOISES = [0.5, 0.6, 0.7]
SEEDS = [11, 22]

if __name__ == "__main__":
    tpl = {"human": draw_human(), "quad": draw_quad(), "quad2": draw_quad("quad2")}
    only = [a for a in sys.argv[1:] if not a.startswith("--")]
    if only:
        tpl = {k: v for k, v in tpl.items() if k in only} | {k: v for k, v in tpl.items() if k not in only and False}
    for k, im in tpl.items():
        im.save(os.path.join(OUT, f"template_{k}.png"))
    print("틀 두 장 →", OUT)
    if "--templates" in sys.argv:
        sys.exit(0)
    dens = [0.55, 0.6] if only == ["quad2"] else DENOISES
    for k, im in tpl.items():
        buf = io.BytesIO(); im.save(buf, "PNG")
        name = upload(buf.getvalue(), f"rig_template_{k}.png")
        for dn in dens:
            for sd in SEEDS:
                tag = f"{k}_{int(dn*100)}_{sd}"
                if os.path.exists(os.path.join(OUT, tag + "_cut.png")):
                    continue
                img = run(img2img(TEMPLATES[k]["prompt"], name, sd, dn, tag), tag)
                if not img:
                    continue
                open(os.path.join(OUT, tag + ".png"), "wb").write(fetch(img))
                cut = run(bgremove(upload(fetch(img), f"rig_{tag}.png"), tag), tag + ":cut")
                if cut:
                    open(os.path.join(OUT, tag + "_cut.png"), "wb").write(fetch(cut))
    print("done")
