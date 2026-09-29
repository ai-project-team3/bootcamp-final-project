# -*- coding: utf-8 -*-
"""오또 표정 얼굴 (09-29) — ComfyUI FLUX.1 Kontext 로 지금 얼굴(otto_face_talk)의 **표정만** 바꾼다.

design/tools/make_poses.py 와 같은 워크플로. 결과는 BiRefNet 으로 배경을 딴 PNG.

    python tools/gen_faces.py OUT_DIR [이름 ...] [--seeds N]
"""
import json
import os
import random
import sys
import time
import urllib.parse
import urllib.request

from PIL import Image

API = "http://127.0.0.1:8188"
DRAW = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "drawable")
REF = os.path.join(DRAW, "otto_face_talk.png")
# 전신 자세(apose)는 전신 그림에서 시작하고, 「같은 틀(얼굴 가까이)」 대신 전신을 유지하라고 한다
REF_FULL = os.environ.get("OTTO_REF_FULL") or os.path.join(DRAW, "mascot.png")
FULL = {"apose", "side"}
KEEP_FULL = ("Keep the exact same kitten character: orange and cream fluffy fur, teal-green felt hood with cat ears and blue trim, "
             "round yellow gold button, pink paw pads, striped orange tail, big glossy eyes. Same soft plush 3D render style and "
             "lighting. Full body, centered, plain pure white background.")

KEEP = ("Keep the exact same kitten character, same framing and crop (head, hood and paws close-up), orange and cream fluffy fur, "
        "teal-green felt hood with cat ears and blue trim, round yellow gold button, pink paw pads, same soft plush 3D render style "
        "and lighting, plain pure white background.")

FACES = {
    "happy": "Change only the facial expression: the kitten is overjoyed, eyes happily closed into upward curved crescents, "
             "big open laughing smile, rosy cheeks, both paws raised in celebration.",
    "surprised": "Change only the facial expression: the kitten is amazed and surprised, eyes wide open and round with sparkles, "
                 "small round open 'O' mouth, ears perked up, both paws near its cheeks.",
    "sad": "Change only the facial expression to clearly sad: eyebrows tilted up in the middle in a worried shape, big glossy eyes "
           "filled with tears with one small tear drop on the cheek, mouth turned down in a small pout, ears drooping to the sides, "
           "both paws held together near its chest, no smile.",
    "curious": "Change only the facial expression and head angle: the kitten is curious, head tilted to one side, one ear up, "
               "big interested eyes looking up, small closed smile, one paw touching its chin.",
    "proud": "Change only the facial expression: the kitten is proud and delighted, sparkling star-shaped highlights in its eyes, "
             "confident wide smile, blushing cheeks, giving a thumbs-up style paw.",
    # 09-29 뼈대 시험 — 전신 A-포즈 (팔을 몸에서 떼고 꼬리는 옆으로)
    "apose": "Change the pose to a full body neutral standing A-pose seen straight from the front: both arms hanging straight down "
             "along the sides of the body, each arm angled about 20 degrees outward so there is a clear empty gap between the arm and "
             "the body all the way from the armpit to the paw, paws open and facing forward at hip height, exactly two legs standing "
             "straight and slightly apart with exactly two feet on the ground, the striped tail sticking out sideways to the right at "
             "hip height, calm small smile, eyes open looking forward. Show the whole body from ears to feet.",
    # 09-29 옆모습 걷기용 — 오른쪽을 보는 옆모습. 다리 둘이 앞뒤로 떨어져 보이게, 꼬리는 뒤로
    "side": "Turn the kitten into a full side profile view facing to the right, standing upright on two legs like a person. "
            "Show one arm hanging straight down at the side with the paw near the hip, the two legs straight and slightly apart "
            "front and back with a clear gap between them, the striped tail sticking out straight behind to the left. "
            "The head also in profile facing right, eye and cheek visible, hood with cat ears. Show the whole body from ears to feet.",
}


def post(path, data):
    req = urllib.request.Request(API + path, data=json.dumps(data).encode(), headers={"Content-Type": "application/json"})
    return json.load(urllib.request.urlopen(req, timeout=60))


def get(path):
    return json.load(urllib.request.urlopen(API + path, timeout=60))


def upload_ref(path=REF):
    im = Image.open(path).convert("RGBA")
    bg = Image.new("RGBA", im.size, (255, 255, 255, 255))
    bg.alpha_composite(im)
    tmp = os.path.join(os.environ.get("TEMP", "."), "otto_face_ref.png")
    bg.convert("RGB").resize((1024, 1024), Image.LANCZOS).save(tmp)
    data = open(tmp, "rb").read()
    b = "----face" + str(random.randint(1, 10**9))
    body = (f"--{b}\r\nContent-Disposition: form-data; name=\"image\"; filename=\"otto_face_ref.png\"\r\nContent-Type: image/png\r\n\r\n").encode() + data \
        + f"\r\n--{b}\r\nContent-Disposition: form-data; name=\"overwrite\"\r\n\r\ntrue\r\n--{b}--\r\n".encode()
    req = urllib.request.Request(API + "/upload/image", data=body, headers={"Content-Type": f"multipart/form-data; boundary={b}"})
    return json.load(urllib.request.urlopen(req, timeout=120))["name"]


def workflow(ref, instruction, prefix, seed, keep=KEEP):
    return {
        "1": {"class_type": "UnetLoaderGGUF", "inputs": {"unet_name": "flux1-kontext-dev-Q4_K_M.gguf"}},
        "2": {"class_type": "DualCLIPLoader", "inputs": {"clip_name1": "clip_l.safetensors", "clip_name2": "t5xxl_fp8_e4m3fn_scaled.safetensors", "type": "flux"}},
        "3": {"class_type": "VAELoader", "inputs": {"vae_name": "ae.safetensors"}},
        "4": {"class_type": "LoadImage", "inputs": {"image": ref}},
        "5": {"class_type": "FluxKontextImageScale", "inputs": {"image": ["4", 0]}},
        "6": {"class_type": "VAEEncode", "inputs": {"pixels": ["5", 0], "vae": ["3", 0]}},
        "7": {"class_type": "CLIPTextEncode", "inputs": {"text": f"{instruction} {keep}", "clip": ["2", 0]}},
        "8": {"class_type": "ReferenceLatent", "inputs": {"conditioning": ["7", 0], "latent": ["6", 0]}},
        "9": {"class_type": "FluxGuidance", "inputs": {"conditioning": ["8", 0], "guidance": 2.5}},
        "10": {"class_type": "ConditioningZeroOut", "inputs": {"conditioning": ["7", 0]}},
        "11": {"class_type": "KSampler", "inputs": {"model": ["1", 0], "positive": ["9", 0], "negative": ["10", 0], "latent_image": ["6", 0],
                                                    "seed": seed, "steps": 20, "cfg": 1.0, "sampler_name": "euler", "scheduler": "simple", "denoise": 1.0}},
        "12": {"class_type": "VAEDecode", "inputs": {"samples": ["11", 0], "vae": ["3", 0]}},
        "13": {"class_type": "LoadBackgroundRemovalModel", "inputs": {"bg_removal_name": "birefnet.safetensors"}},
        "14": {"class_type": "RemoveBackground", "inputs": {"bg_removal_model": ["13", 0], "image": ["12", 0]}},
        "15": {"class_type": "InvertMask", "inputs": {"mask": ["14", 0]}},
        "16": {"class_type": "JoinImageWithAlpha", "inputs": {"image": ["12", 0], "alpha": ["15", 0]}},
        "17": {"class_type": "SaveImage", "inputs": {"images": ["16", 0], "filename_prefix": prefix}},
    }


def run(wf, label):
    pid = post("/prompt", {"prompt": wf})["prompt_id"]
    t0 = time.time()
    while time.time() - t0 < 900:
        time.sleep(3)
        h = get("/history/" + pid)
        if pid in h:
            if h[pid].get("status", {}).get("status_str") == "error":
                print(f"[{label}] error", json.dumps(h[pid]["status"].get("messages", []))[:400], flush=True)
                return None
            imgs = [i for o in h[pid].get("outputs", {}).values() for i in o.get("images", [])]
            if imgs:
                print(f"[{label}] {time.time() - t0:.0f}s", flush=True)
                return imgs[0]
    return None


def fetch(img):
    q = urllib.parse.urlencode({"filename": img["filename"], "subfolder": img.get("subfolder", ""), "type": img.get("type", "output")})
    return urllib.request.urlopen(API + "/view?" + q, timeout=120).read()


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
    ref = upload_ref()
    ref_full = None
    for name in (args or list(FACES)):
        full = name in FULL
        if full and ref_full is None:
            ref_full = upload_ref(REF_FULL)
        for k in range(seeds):
            img = run(workflow(ref_full if full else ref, FACES[name], f"otto_face/{name}", random.randint(1, 2**31),
                               KEEP_FULL if full else KEEP), f"{name}#{k}")
            if img:
                open(os.path.join(out, f"{name}_{k}.png"), "wb").write(fetch(img))
    print("ALL DONE", flush=True)
