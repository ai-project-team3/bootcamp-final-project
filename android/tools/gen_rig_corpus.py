# -*- coding: utf-8 -*-
"""뼈대 시험용 **생성 캐릭터 모음** (09-30) — 서버(`/image kind=character`)와 같은 방식으로 뽑아 자동 뼈대를 시험한다.

서버와 같게 맞춘 것:
  - 회색 마네킹 틀에서 img2img (backend/app/image/templates/{human,quad,blob}.png) · 세기 human 0.85 · quad 0.8 · blob 0.9
  - 자세 문장 CHAR_POSE · 화풍 CHAR_STYLE · 금지어 CHAR_NEG (backend/app/image/comfy.py 그대로)
  - 오려 내기 · 640² · 발 93% 선 — 서버 코드(backend/app/image/character.py 의 cut_and_fit)를 그대로 부른다
다른 것: 모델. 서버는 SDXL + Lightning 인데 이 PC 에는 없어 krea2 turbo 로 그린다 → 화풍은 조금 다를 수 있다.

    python tools/gen_rig_corpus.py OUT_DIR [--only human|quad|blob] [--n N] [--set hard]
결과: OUT_DIR/<rig>__<번호>_<subject>.png (640 투명) · raw/ 에 원본
"""
import json
import os
import random
import sys
import time
import urllib.parse
import urllib.request

REPO = r"C:\dev\bootcamp-final-project\backend"
sys.path.insert(0, REPO)
from app.image.character import cut_and_fit  # noqa: E402  서버 오려 내기 그대로

API = "http://127.0.0.1:8188"
TEMPLATES = os.path.join(REPO, "app", "image", "templates")

# backend/app/image/comfy.py 에서 그대로 옮김 (09-29 조장 판)
CHAR_STYLE = (", cut paper collage, layered torn construction paper, flat 2d shapes, warm crayon-box colors, "
              "children's picture book character, isolated on plain pure white background, no shadow, no text")
CHAR_NEG = ("text, letters, watermark, photo, photorealistic, blurry, ugly, scary, dark, horror, "
            "background scenery, frame, border, card, backdrop, circle behind, colored background, "
            "multiple characters, nudity, blood, weapon, gore")
CHAR_POSE = {
    "human": "front view, full body, both arms stretched out diagonally downward away from the body in an A-pose",
    "quad": "side view facing right, full body, standing on four clearly separated legs",
    "blob": "front view, full body, simple round shape",
}
CHAR_DENOISE = {"human": 0.85, "quad": 0.8, "blob": 0.9}

# 아이가 말할 법한 등장인물 — 서버 LLM 이 만드는 subject 모양(영어 명사구)으로
SUBJECTS = {
    "human": ["little girl in a pink princess dress with a tiara", "friendly silver robot with round eyes and blue buttons",
              "small astronaut kid in a white space suit", "firefighter boy in a red uniform and yellow helmet",
              "brown teddy bear standing on two legs", "penguin standing upright with a red scarf",
              "pirate boy with a striped shirt and eye patch", "grandma with glasses and a flowered apron",
              "superhero girl with a purple cape and mask", "white rabbit standing on two legs in blue overalls"],
    "quad": ["baby green dinosaur with small spikes", "fluffy brown puppy with floppy ears", "orange striped cat",
             "white pony with a rainbow mane", "baby grey elephant with big ears", "little yellow lion cub with a fluffy mane"],
    "blob": ["pink octopus with curly tentacles", "happy snowman with a carrot nose", "blue jellyfish with long ribbons",
             "fluffy white cloud with a smiling face", "yellow star with rosy cheeks", "round orange goldfish"],
}


# 까다로운 모음 (--set hard) — 날개 · 긴 옷 · 든 물건 · 큰 모자 · 긴 목 · 다리 없는 몸
HARD = {
    "human": ["fairy girl with sparkly butterfly wings and a wand", "old wizard in a long starry robe and a tall pointy hat",
              "little boy holding a red balloon on a string", "chef with a very tall white hat and an apron",
              "ballerina girl in a puffy pink tutu", "knight kid in shiny armor with a round shield",
              "little bird standing like a person with wing hands", "girl with very long braided hair and a backpack"],
    "quad": ["small purple dragon with little wings", "baby giraffe with a very long neck", "green turtle with a round shell",
             "spotted dalmatian dog wagging its tail"],
    "blob": ["mermaid with a long teal tail", "friendly white ghost with wavy bottom", "snail with a spiral shell", "red crab with big claws"],
}


def post(path, data):
    req = urllib.request.Request(API + path, data=json.dumps(data).encode(), headers={"Content-Type": "application/json"})
    return json.load(urllib.request.urlopen(req, timeout=60))


def get(path):
    return json.load(urllib.request.urlopen(API + path, timeout=60))


def upload(path, name):
    data = open(path, "rb").read()
    b = "----rig" + str(random.randint(1, 10**9))
    body = (f"--{b}\r\nContent-Disposition: form-data; name=\"image\"; filename=\"{name}\"\r\nContent-Type: image/png\r\n\r\n").encode() + data \
        + f"\r\n--{b}\r\nContent-Disposition: form-data; name=\"overwrite\"\r\n\r\ntrue\r\n--{b}--\r\n".encode()
    req = urllib.request.Request(API + "/upload/image", data=body, headers={"Content-Type": f"multipart/form-data; boundary={b}"})
    return json.load(urllib.request.urlopen(req, timeout=120))["name"]


def workflow(subject, rig, template, seed):
    return {
        "1": {"class_type": "UNETLoader", "inputs": {"unet_name": "krea2_turbo_nvfp4.safetensors", "weight_dtype": "default"}},
        "2": {"class_type": "CLIPLoader", "inputs": {"clip_name": "qwen3vl_4b_fp8_scaled.safetensors", "type": "krea2"}},
        "3": {"class_type": "VAELoader", "inputs": {"vae_name": "qwen_image_vae.safetensors"}},
        "4": {"class_type": "CLIPTextEncode", "inputs": {"text": f"a cute {subject} puppet, {CHAR_POSE[rig]}{CHAR_STYLE}", "clip": ["2", 0]}},
        "5": {"class_type": "ConditioningZeroOut", "inputs": {"conditioning": ["4", 0]}},
        "10": {"class_type": "LoadImage", "inputs": {"image": template}},
        "11": {"class_type": "VAEEncode", "inputs": {"pixels": ["10", 0], "vae": ["3", 0]}},
        "7": {"class_type": "KSampler", "inputs": {"model": ["1", 0], "positive": ["4", 0], "negative": ["5", 0], "latent_image": ["11", 0],
                                                    "seed": seed, "steps": 8, "cfg": 1, "sampler_name": "er_sde", "scheduler": "simple",
                                                    "denoise": CHAR_DENOISE[rig]}},
        "8": {"class_type": "VAEDecode", "inputs": {"samples": ["7", 0], "vae": ["3", 0]}},
        "9": {"class_type": "SaveImage", "inputs": {"images": ["8", 0], "filename_prefix": "rigcorpus/" + rig}},
    }


def run(wf):
    pid = post("/prompt", {"prompt": wf})["prompt_id"]
    t0 = time.time()
    while time.time() - t0 < 600:
        time.sleep(2)
        h = get("/history/" + pid)
        if pid in h:
            if h[pid].get("status", {}).get("status_str") == "error":
                print("error", json.dumps(h[pid]["status"].get("messages", []))[:300], flush=True)
                return None
            imgs = [i for o in h[pid].get("outputs", {}).values() for i in o.get("images", [])]
            if imgs:
                q = urllib.parse.urlencode({"filename": imgs[0]["filename"], "subfolder": imgs[0].get("subfolder", ""), "type": "output"})
                return urllib.request.urlopen(API + "/view?" + q, timeout=120).read()
    return None


if __name__ == "__main__":
    out = sys.argv[1]
    args = sys.argv[2:]
    only = args[args.index("--only") + 1] if "--only" in args else None
    subjects = HARD if "--set" in args and args[args.index("--set") + 1] == "hard" else SUBJECTS
    n = int(args[args.index("--n") + 1]) if "--n" in args else 99
    for _ in range(90):
        try:
            get("/system_stats"); break
        except Exception:
            time.sleep(5)
    os.makedirs(os.path.join(out, "raw"), exist_ok=True)
    tpl = {r: upload(os.path.join(TEMPLATES, f"{r}.png"), f"rigtpl_{r}.png") for r in CHAR_POSE}
    for rig, subs in subjects.items():
        if only and rig != only:
            continue
        for i, subject in enumerate(subs[:n]):
            t0 = time.time()
            png = run(workflow(subject, rig, tpl[rig], random.randint(1, 2**31)))
            if not png:
                continue
            slug = subject.split(" with ")[0].replace(" ", "_")[:28]
            open(os.path.join(out, "raw", f"{rig}__{i:02d}_{slug}.png"), "wb").write(png)
            try:
                cut = cut_and_fit(png)
                open(os.path.join(out, f"{rig}__{i:02d}_{slug}.png"), "wb").write(cut)
                print(f"[{rig} {i}] {subject} {time.time() - t0:.0f}s", flush=True)
            except Exception as e:
                print(f"[{rig} {i}] {subject} 오려 내기 실패: {e}", flush=True)
    print("ALL DONE", flush=True)
