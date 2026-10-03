# -*- coding: utf-8 -*-
"""주인공을 **부품으로** 만든다 — 몸 9장 + 머리 3장 + 안경 2장 (ComfyUI HTTP API · krea2 turbo)

왜 이렇게 바꿨나 (9/21)
  전에는 머리 3 x 옷 3 x 안경 3 x 하의 3 = **완성본 81장**을 따로 뽑아 갈아 끼웠다.
  그런데 81장이 각각 **독립적으로 생성된 그림**이라, 토글 하나를 바꾸면 그 하나만 바뀌는 게 아니라
  **캐릭터가 통째로 다른 아이로 바뀌었다**:
    - 머리를 길게 바꾸면 얼굴이 여자아이가 된다
    - 옷 색을 노랑으로 바꾸면 하의가 치마가 된다
    - 안경을 바꾸면 몸 비율이 달라진다
  아이가 고른 것은 "머리를 길게" 이지 "다른 아이" 가 아니다.

지금 방식
  몸    : 하의 3 x 옷 색 3 = 9장. **머리는 짧게 - 안경 없음**으로 고정한 같은 아이
  머리  : 짧아 - 길어 - 묶었어 3장 (투명 배경, 머리카락만)
  안경  : 동글 - 네모 2장 (투명 배경, 안경만. "없음"은 아무것도 안 얹는다)
  눈    : 앱이 벡터로 그린다 (전부터 그랬다)

  얹는 순서: 몸 -> 머리 -> 안경 -> 눈

주의: 부품은 서로 자리가 맞아야 한다. 같은 크기(1024)로 뽑고
      `tools/check_parts.py` 로 겹쳐 보며 좌표를 잡는다.

쓰는 법
  python gen_hero_parts.py               # 없는 것만
  python gen_hero_parts.py body_blue_pants
"""
import json, time, urllib.request, urllib.parse, os, sys, random

API = os.environ.get("COMFY_URL", "http://127.0.0.1:8188").rstrip("/")  # 다른 PC 의 ComfyUI 는 COMFY_URL (ART.md)
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "drawable")
os.makedirs(OUT, exist_ok=True)

STYLE = ("soft felt fabric and paper-craft 3D children's picture book illustration, cute, rounded shapes, "
         "warm pastel colors, clean, gentle lighting, no text, no letters, high quality")
CUT_STYLE = "single subject centered, full body, front view, isolated on plain pure white background, no shadow, " + STYLE

STYLE2 = STYLE
CUT = "single subject centered, isolated on plain pure white background, no shadow, " + STYLE

# 같은 아이로 보이게 얼굴/자세/비율을 말로 못 박는다
CHILD = ("a cute 5 year old child puppet doll, plain simple round face, small dot eyes, tiny smile, "
         "rosy cheeks, very short dark brown hair, standing straight facing front, arms straight down at sides, "
         "symmetrical, full body from head to feet")

SHIRTS = {"red": "a red t-shirt with a small green dinosaur print",
          "blue": "a royal blue t-shirt with a small green dinosaur print",
          "yellow": "a yellow t-shirt with a small green dinosaur print"}
# "long blue trousers" 만 적었더니 다리 구분 없는 **통짜 긴 치마**처럼 나왔다 —
# 치마와 구분이 안 됐다. 두 다리가 갈라진 것을 말로 못 박는다 (9/21)
BOTTOMS = {
    "pants": "long blue trousers with two separate legs clearly divided, each leg visible, small bare feet peeking out below",
    "skirt": "a blue pleated skirt with bare legs and small feet below",
    "shorts": "blue shorts with bare legs and small feet below",
}

JOBS = []
for c, cdesc in SHIRTS.items():
    for b, bdesc in BOTTOMS.items():
        JOBS.append((f"body_{c}_{b}", 1024, 1024,
                     f"{CHILD}, wearing {cdesc} and {bdesc}, no glasses, " + CUT))

HAIRS = {
    "short": "short dark brown hair, simple rounded bob shape",
    "long": "long dark brown hair falling past the shoulders",
    "tied": "dark brown hair tied into a ponytail with a small yellow band",
}
for k, desc in HAIRS.items():
    JOBS.append((f"hair_{k}", 1024, 1024,
                 f"{desc}, a wig piece made of felt fabric, hair only, no face, no head, no skin, "
                 "hollow empty space where the face would be, front view, " + CUT))

GLASSES = {
    "round": "a pair of round eyeglasses with thin brown frames",
    "square": "a pair of square eyeglasses with thin brown frames",
}
for k, desc in GLASSES.items():
    JOBS.append((f"gl_{k}", 1024, 1024,
                 f"{desc}, front view, flat, just the eyeglasses alone, no face, no head, no person, " + CUT))


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
