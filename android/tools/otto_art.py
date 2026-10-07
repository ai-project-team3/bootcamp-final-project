# -*- coding: utf-8 -*-
"""오또 그림 공방 — 팀원 누구나 같은 그림체로 앱 그림을 뽑는 도구 (10-02).

그림체 · 모델 · 워크플로가 `gen_*.py` 마다 복사돼 있어서 한 사람만 그림을 만들 수 있었다.
여기 한 곳에 모으고, 뽑을 때마다 시드와 문장을 레시피로 남겨 **누가 다시 뽑아도 같은 그림**이 나오게 한다.
설치는 `tools/setup_comfyui.ps1`, 쓰는 법은 `tools/ART.md`.

    python tools/otto_art.py check                         # 그림 서버 · 모델이 준비됐나
    python tools/otto_art.py bg  이름 "장면 설명" [--count 3] [--seed N] [--size 1344x768] [--style room]
    python tools/otto_art.py cut 이름 "물건 설명" [--count 3] [--seed N] [--size 1024x1024] [--style room]
    python tools/otto_art.py pick 이름 시드                  # 고른 한 장을 res/drawable/이름.png 로 + 레시피 저장
    python tools/otto_art.py again 이름                     # 레시피대로 똑같이 다시 뽑기
    python tools/otto_art.py workflows                      # 브라우저용 워크플로 JSON 다시 쓰기

- 설명은 **영어로** 쓴다. 지금 그림들이 모두 영어 문장으로 만들어졌다. 그림체 문장은 자동으로 붙는다
- 결과는 `tools/art_out/` 에 쌓인다(깃에 안 올라감). 눈으로 고른 뒤 `pick` 한다
- 그림 서버 주소는 `COMFY_URL` 환경변수 (기본 http://127.0.0.1:8188)
"""
import argparse, json, os, random, re, shutil, sys, time, urllib.parse, urllib.request

API = os.environ.get("COMFY_URL", "http://127.0.0.1:8188").rstrip("/")
TOOLS = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(TOOLS, "art_out")
RECIPES = os.path.join(TOOLS, "art_recipes")
WORKFLOWS = os.path.join(TOOLS, "workflows")
DRAWABLE = os.path.join(TOOLS, "..", "app", "src", "main", "res", "drawable")

# ── 그림체 ─────────────────────────────────────────────────────────
# 앱 그림 대부분(gen_assets · gen_heroes · gen_icons · gen_diary …)이 쓴 문장 그대로다. 바꾸면 새 그림만 결이 달라진다
STYLES = {
    "felt": ("soft felt fabric and paper-craft 3D children's picture book illustration, cute, rounded shapes, "
             "warm pastel colors, clean, gentle lighting, no text, no letters, high quality"),
    # 오또의 방 · 타이틀 · 밤 (gen_room.py, 09-29) — 펠트 결과 바늘땀이 더 보이고 색이 정해져 있다
    "room": ("soft wool felt and fabric craft 3D children's picture book illustration, visible felt fibers and stitched edges, "
             "cute rounded shapes, warm pastel colors (cream, mustard yellow, coral, teal, sky blue), gentle soft lighting, "
             "cozy, no text, no letters, no numbers, high quality"),
    # 크레용 그림체 (10-07 종훈) — 세계 그림만 이 그림체로 한 벌 더 굽는다(`rebake_style.py` · ART.md 「그림체 다시 굽기」).
    # 도감 인형 · 오또 · 방 · 아이콘은 펠트 그대로(결정 27). 앱은 `이름_crayon` 이 있으면 그것을, 없으면 펠트를 쓴다
    "crayon": ("cute children's crayon drawing illustration on white paper, thick dark brown hand-drawn outlines, "
               "waxy crayon colouring with visible crayon strokes, simple flat rounded shapes, bright warm colours, "
               "friendly and playful, flat 2D, no shading, no 3D, no text, no letters, high quality"),
}
# 앞머리도 gen_assets.py 그대로. 흰 배경 문장이 앞에 있어야 배경 제거가 잘 된다 (assets/README.md 「cutout 의 성패는 생성 단계에서 갈린다」)
BG_HEAD = "wide landscape, no characters, no people, no animals, "
CUT_HEAD = "single subject centered, full body, front view, isolated on plain pure white background, no shadow, "
SIZES = {"bg": (1344, 768), "cut": (1024, 1024)}

# ── 모델 (tools/setup_comfyui.ps1 이 받는 것과 같은 파일 이름) ─────────
UNET = "krea2_turbo_nvfp4.safetensors"
CLIP = "qwen3vl_4b_fp8_scaled.safetensors"
VAE = "qwen_image_vae.safetensors"
BIREFNET = "birefnet.safetensors"
MODELS = {"diffusion_models": UNET, "text_encoders": CLIP, "vae": VAE, "background_removal": BIREFNET}


def prompt_for(kind, text, style):
    """「설명, 앞머리 + 그림체」 — gen_assets.py 의 JOBS 와 같은 순서"""
    return text.strip().rstrip(",") + ", " + (BG_HEAD if kind == "bg" else CUT_HEAD) + STYLES[style]


def workflow(kind, prompt, w, h, seed, prefix):
    """krea2 turbo 8스텝. 잘라낼 그림이면 같은 작업 안에서 BiRefNet 으로 배경까지 뺀다 (원본 · 잘라낸 것 둘 다 저장)"""
    wf = {
        "1": {"class_type": "UNETLoader", "inputs": {"unet_name": UNET, "weight_dtype": "default"}},
        "2": {"class_type": "CLIPLoader", "inputs": {"clip_name": CLIP, "type": "krea2"}},
        "3": {"class_type": "VAELoader", "inputs": {"vae_name": VAE}},
        "4": {"class_type": "CLIPTextEncode", "inputs": {"text": prompt, "clip": ["2", 0]}},
        "5": {"class_type": "ConditioningZeroOut", "inputs": {"conditioning": ["4", 0]}},
        "6": {"class_type": "EmptyLatentImage", "inputs": {"width": w, "height": h, "batch_size": 1}},
        "7": {"class_type": "KSampler", "inputs": {"model": ["1", 0], "positive": ["4", 0], "negative": ["5", 0], "latent_image": ["6", 0],
                                                    "seed": seed, "steps": 8, "cfg": 1, "sampler_name": "er_sde", "scheduler": "simple", "denoise": 1}},
        "8": {"class_type": "VAEDecode", "inputs": {"samples": ["7", 0], "vae": ["3", 0]}},
        "9": {"class_type": "SaveImage", "inputs": {"images": ["8", 0], "filename_prefix": "otto_art/" + prefix}},
    }
    if kind == "cut":
        wf.update({
            "10": {"class_type": "LoadBackgroundRemovalModel", "inputs": {"bg_removal_name": BIREFNET}},
            "11": {"class_type": "RemoveBackground", "inputs": {"bg_removal_model": ["10", 0], "image": ["8", 0]}},
            "12": {"class_type": "InvertMask", "inputs": {"mask": ["11", 0]}},
            "13": {"class_type": "JoinImageWithAlpha", "inputs": {"image": ["8", 0], "alpha": ["12", 0]}},
            "14": {"class_type": "SaveImage", "inputs": {"images": ["13", 0], "filename_prefix": "otto_art/" + prefix + "_cut"}},
        })
    return wf


# ── ComfyUI HTTP ───────────────────────────────────────────────────
def get(path, timeout=30):
    return json.load(urllib.request.urlopen(API + path, timeout=timeout))


def post(path, data):
    req = urllib.request.Request(API + path, data=json.dumps(data).encode(), headers={"Content-Type": "application/json"})
    return json.load(urllib.request.urlopen(req, timeout=60))


def fetch(img):
    q = urllib.parse.urlencode({"filename": img["filename"], "subfolder": img.get("subfolder", ""), "type": img.get("type", "output")})
    return urllib.request.urlopen(API + "/view?" + q, timeout=120).read()


def run(wf, label):
    """작업을 넣고 끝날 때까지 기다린다. 노드 번호 → PNG 바이트. 실패하면 None"""
    pid = post("/prompt", {"prompt": wf})["prompt_id"]
    t0 = time.time()
    while time.time() - t0 < 900:
        time.sleep(2)
        h = get("/history/" + pid)
        if pid not in h:
            continue
        st = h[pid].get("status", {})
        if st.get("status_str") == "error":
            print(f"  [{label}] 실패: {json.dumps(st.get('messages', []), ensure_ascii=False)[:400]}")
            return None
        outs = {k: v["images"][0] for k, v in h[pid].get("outputs", {}).items() if v.get("images")}
        if outs:
            print(f"  [{label}] {time.time() - t0:.0f}초")
            return {k: fetch(img) for k, img in outs.items()}
    print(f"  [{label}] 15분이 지나도 안 끝났다")
    return None


def reachable():
    try:
        return get("/system_stats", timeout=5)
    except Exception as e:
        print(f"그림 서버({API})에 닿지 않는다: {e}")
        print("  ComfyUI 를 켰나? 켜는 법은 tools/ART.md 「켜기」. 다른 PC 를 쓰면 COMFY_URL 을 맞춘다")
        return None


# ── 명령 ───────────────────────────────────────────────────────────
NAME = re.compile(r"^[a-z][a-z0-9_]*$")


def check_name(name):
    if not NAME.match(name):
        sys.exit(f"이름 「{name}」은 안 된다 — 안드로이드 그림 이름은 영어 소문자 · 숫자 · _ 만 (예: bg_park, coop_el_zoo)")


def cmd_check(_):
    stats = reachable()
    if not stats:
        return 1
    dev = (stats.get("devices") or [{}])[0]
    print(f"그림 서버 {API} — {dev.get('name', '?')} · VRAM {dev.get('vram_total', 0) / 2**30:.1f}GB")
    ok = True
    for folder, fname in MODELS.items():
        try:
            have = get("/models/" + folder)
        except Exception:
            have = []
        mark = "OK " if fname in have else "없음"
        ok &= fname in have
        print(f"  [{mark}] models/{folder}/{fname}")
    for node in ("LoadBackgroundRemovalModel", "RemoveBackground"):
        try:
            found = node in get("/object_info/" + node)
        except Exception:
            found = False
        ok &= found
        print(f"  [{'OK ' if found else '없음'}] 노드 {node}")
    print("준비 끝 — 그림을 뽑을 수 있다" if ok else "빠진 것이 있다 — tools/setup_comfyui.ps1 을 다시 돌리거나 ComfyUI 를 업데이트한다")
    return 0 if ok else 1


def make(kind, name, text, style, w, h, seeds):
    os.makedirs(OUT, exist_ok=True)
    prompt = prompt_for(kind, text, style)
    made = []
    for seed in seeds:
        label = f"{name} · 시드 {seed}"
        outs = run(workflow(kind, prompt, w, h, seed, f"{name}_{seed}"), label)
        if not outs:
            continue
        base = os.path.join(OUT, f"{name}_{seed}")
        open(base + ".png", "wb").write(outs["9"])
        if kind == "cut" and "14" in outs:
            open(base + "_cut.png", "wb").write(outs["14"])
        # 고르기 전 레시피 — pick 하면 art_recipes/ 로 옮겨 깃에 남는다
        json.dump({"name": name, "kind": kind, "text": text, "style": style, "size": [w, h], "seed": seed,
                   "prompt": prompt, "model": UNET, "made": time.strftime("%Y-%m-%d")},
                  open(base + ".json", "w", encoding="utf-8"), ensure_ascii=False, indent=2)
        made.append(base + ("_cut.png" if kind == "cut" else ".png"))
    if made:
        print(f"\n{len(made)}장 → {OUT}")
        for m in made:
            print("  " + os.path.basename(m))
        print(f"마음에 드는 것을 골라:  python tools/otto_art.py pick {name} <시드>")
    return 0 if made else 1


def size_of(kind, s):
    if not s:
        return SIZES[kind]
    m = re.match(r"^(\d+)x(\d+)$", s)
    if not m:
        sys.exit("--size 는 1344x768 꼴로")
    w, h = int(m.group(1)), int(m.group(2))
    if w % 16 or h % 16:
        sys.exit("--size 의 가로 · 세로는 16의 배수로")
    return w, h


def cmd_make(a):
    check_name(a.name)
    if not reachable():
        return 1
    w, h = size_of(a.kind, a.size)
    seeds = [a.seed] if a.seed is not None else [random.randint(1, 2**31 - 1) for _ in range(a.count)]
    return make(a.kind, a.name, a.text, a.style, w, h, seeds)


def cmd_pick(a):
    check_name(a.name)
    base = os.path.join(OUT, f"{a.name}_{a.seed}")
    if not os.path.exists(base + ".json"):
        sys.exit(f"{base}.json 이 없다 — art_out/ 에 있는 시드를 고른다")
    r = json.load(open(base + ".json", encoding="utf-8"))
    src = base + ("_cut.png" if r["kind"] == "cut" else ".png")
    dst = os.path.join(DRAWABLE, a.name + ".png")
    if os.path.exists(dst) and not a.replace:
        sys.exit(f"res/drawable/{a.name}.png 이 이미 있다 — 바꾸려면 --replace")
    shutil.copyfile(src, dst)
    os.makedirs(RECIPES, exist_ok=True)
    shutil.copyfile(base + ".json", os.path.join(RECIPES, a.name + ".json"))
    print(f"res/drawable/{a.name}.png ← {os.path.basename(src)}")
    print(f"레시피 tools/art_recipes/{a.name}.json")
    print("다음: python tools/shrink_assets.py  (앱에 넣기 전에 크기를 줄인다)")
    return 0


def cmd_again(a):
    check_name(a.name)
    path = os.path.join(RECIPES, a.name + ".json")
    if not os.path.exists(path):
        sys.exit(f"레시피가 없다: tools/art_recipes/{a.name}.json — 이 도구로 pick 한 그림만 다시 뽑을 수 있다")
    r = json.load(open(path, encoding="utf-8"))
    if not reachable():
        return 1
    w, h = r["size"]
    return make(r["kind"], r["name"], r["text"], r["style"], w, h, [r["seed"]])


def cmd_workflows(_):
    """ComfyUI 화면에 끌어다 놓는 API 형식 워크플로 — 4번 노드 문장의 영어 설명 부분만 바꿔 쓴다"""
    os.makedirs(WORKFLOWS, exist_ok=True)
    for kind in ("bg", "cut"):
        w, h = SIZES[kind]
        text = "a sunny park with a pond and a little bridge" if kind == "bg" else "a cute red felt toy fire truck"
        wf = workflow(kind, prompt_for(kind, text, "felt"), w, h, 12345, "browser_" + kind)
        path = os.path.join(WORKFLOWS, f"otto_{kind}.json")
        json.dump(wf, open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
        print(path)
    return 0


def main():
    p = argparse.ArgumentParser(description="오또 그림 공방 — 같은 그림체로 앱 그림 뽑기 (tools/ART.md)")
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("check", help="그림 서버 · 모델 확인").set_defaults(f=cmd_check)
    for kind, what in (("bg", "배경 (가로)"), ("cut", "오려 낸 물건 · 인물 (배경 제거)")):
        s = sub.add_parser(kind, help=what)
        s.add_argument("name")
        s.add_argument("text", help="영어로 쓴 장면 · 물건 설명 (그림체 문장은 자동으로 붙는다)")
        s.add_argument("--count", type=int, default=3, help="몇 장 뽑아 고를까 (기본 3)")
        s.add_argument("--seed", type=int, help="이 시드 하나만")
        s.add_argument("--size", help="가로x세로, 16의 배수")
        s.add_argument("--style", choices=sorted(STYLES), default="felt")
        s.set_defaults(f=cmd_make, kind=kind)
    s = sub.add_parser("pick", help="고른 한 장을 앱 그림으로")
    s.add_argument("name")
    s.add_argument("seed", type=int)
    s.add_argument("--replace", action="store_true", help="같은 이름의 그림을 바꾼다")
    s.set_defaults(f=cmd_pick)
    s = sub.add_parser("again", help="레시피대로 다시 뽑기")
    s.add_argument("name")
    s.set_defaults(f=cmd_again)
    sub.add_parser("workflows", help="브라우저용 워크플로 JSON 쓰기").set_defaults(f=cmd_workflows)
    a = p.parse_args()
    sys.exit(a.f(a))


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    main()
