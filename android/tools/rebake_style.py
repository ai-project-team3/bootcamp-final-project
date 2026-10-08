# -*- coding: utf-8 -*-
"""그림체 다시 굽기 — 앱에 넣어 둔 **세계 그림**을 다른 그림체로 한 벌 더 굽는다 (10-07 종훈 · 크레용).

펠트가 기본이다. 부모가 설정에서 그림체를 바꾸면 앱은 `이름_<그림체>` 그림이 있으면 그것을, 없으면 펠트를 쓴다.
그래서 여기서는 원래 그림과 **같은 대상 · 같은 크기 · 같은 종류(배경 / 오려 낸 것)** 로, 그림체 문장만 바꿔 굽는다.

결정 27 — 세계만 바뀐다. 도감 인형(body_ · hero_) · 오또 · 방 · 아이콘 · 부모 화면 그림은 굽지 않는다.

원래 대상 문장은 그림을 만든 `gen_*.py` 에서 읽는다(그 파일들은 열면 바로 그림을 뽑기 시작하므로 **실행하지 않고**
첫 함수 앞의 대입문만 따로 돌려 JOBS 를 얻는다). 문장에 박힌 「felt」 같은 그림체 말은 빼고 새 그림체 문장을 붙인다.

    python tools/rebake_style.py crayon --dry-run              # 무엇을 굽나 · 대상 문장을 못 찾은 그림
    python tools/rebake_style.py crayon [--only kit_,bg_park] [--seeds 2]
    python tools/rebake_style.py crayon pick kit_park_slide 12345   # 고른 한 장 → res/drawable-nodpi/kit_park_slide_crayon.webp
    python tools/rebake_style.py crayon refit                  # res/drawable/*_crayon.png 를 펠트판에 맞춰 webp 로 옮기기

- 후보는 tools/art_out/<그림체>/이름_<그림체>_<시드>.png (깃에 안 올라감). 이미 고른 그림은 건너뛴다 — 끊겨도 다시 돌리면 이어서
- 그림 서버 · 모델은 otto_art.py 와 같다(krea2 turbo · BiRefNet). 쓰는 법은 tools/ART.md 「그림체 다시 굽기」
"""
import argparse, ast, json, os, random, re, shutil, sys, time

TOOLS = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, TOOLS)
import otto_art  # noqa: E402

RES = os.path.join(TOOLS, "..", "app", "src", "main", "res")
DRAWABLES = [os.path.join(RES, d) for d in ("drawable", "drawable-nodpi")]
RECIPES = os.path.join(TOOLS, "art_recipes")

# ── 어떤 그림이 「세계」인가 (결정 27) ─────────────────────────────────
WORLD_PREFIX = ("kit_", "bg_", "prop_", "obj_", "coop_el_", "bud_", "nc_", "dino_", "dp_")
WORLD_NAMES = {"rocket", "train", "turtle"}
# 세계 앞머리지만 화면 틀이라 굽지 않는 것
NOT_WORLD = {"bg_shelf"}


def is_world(name):
    if name in NOT_WORLD or re.search(r"_(crayon|hanji|water)$", name):
        return False
    return name in WORLD_NAMES or name.startswith(WORLD_PREFIX)


# ── gen_*.py 에서 대상 문장 읽기 ─────────────────────────────────────
# 문장에 섞인 펠트 그림체 말 — 새 그림체에서는 뺀다 (긴 것부터)
FELT_WORDS = [
    r"made of soft felt fabric", r"made of felt", r"soft wool needle felt 3D toy", r"felt and wood craft", r"soft felt fabric",
    r"paper puppet", r"felt doll", r"felt toy", r"\bfelt\b", r"\bwool\b", r"\bfabric\b",
]


def scrub(text):
    for w in FELT_WORDS:
        text = re.sub(w, lambda m: "doll" if m.group(0) == "felt doll" else ("toy" if m.group(0) == "felt toy" else ""), text)
    text = re.sub(r"\s+,", ",", text)
    text = re.sub(r"\s{2,}", " ", text)
    return text.strip(" ,")


def load_module_values(path):
    """gen_*.py 를 실행하지 않고 첫 함수 앞의 import · 대입 · for 만 돌린다 (그 파일들은 열자마자 그림을 뽑는다)"""
    src = open(path, encoding="utf-8").read()
    tree = ast.parse(src)
    keep = []
    for node in tree.body:
        if isinstance(node, (ast.FunctionDef, ast.If)):
            break
        if isinstance(node, (ast.Import, ast.ImportFrom, ast.Assign, ast.AugAssign, ast.AnnAssign, ast.For)):
            keep.append(node)
    ns = {"__name__": "rebake_probe", "__file__": path}
    cwd = os.getcwd()
    try:
        os.chdir(TOOLS)
        exec(compile(ast.Module(body=keep, type_ignores=[]), path, "exec"), ns)
    finally:
        os.chdir(cwd)
    return ns


def strip_style(prompt, ns):
    """그 파일의 그림체 꼬리(STYLE · CUT_STYLE · ICON …)를 뗀 대상 문장"""
    tails = [v for k, v in ns.items() if k.isupper() and isinstance(v, str) and len(v) > 40]
    for t in sorted(tails, key=len, reverse=True):
        prompt = prompt.replace(t, "")
    return scrub(prompt)


def job(name, w, h, prompt, cut, src):
    return {"name": name, "kind": "cut" if cut else "bg", "size": [w, h], "text": prompt, "source": src}


def collect():
    """이름 → {kind, size, text(그림체 뺀 대상), source}"""
    jobs = {}

    def add(j):
        jobs.setdefault(j["name"], j)    # 먼저 읽은 파일이 이긴다 (아래 순서 = 최신 파이프라인 순)

    # 배경 조각 — KITS[묶음][조각] = 대상, 모두 오려 낸 것. 크기는 SIZES
    ns = load_module_values(os.path.join(TOOLS, "gen_kit.py"))
    for kit, pieces in ns["KITS"].items():
        for key, phrase in pieces.items():
            w, h = ns["SIZES"].get(key, (1024, 1024))
            add(job(f"kit_{kit}_{key}", w, h, scrub(phrase), True, "gen_kit.py"))
    # 같이 만들기 — JOBS[이름] = 대상 + ", ", 모두 오려 낸 것 1024²
    ns = load_module_values(os.path.join(TOOLS, "gen_coop.py"))
    for name, p in ns["JOBS"].items():
        add(job(name, 1024, 1024, scrub(p), True, "gen_coop.py"))
    # 목록형 — (이름, 폭, 높이, 문장[, 오려 내기])
    for f in ("gen_room.py", "gen_diary_more.py", "gen_diary.py", "gen_icons.py", "gen_heroes.py",
              "gen_extra.py", "gen_buddies.py", "gen_assets.py"):
        path = os.path.join(TOOLS, f)
        if not os.path.exists(path):
            continue
        ns = load_module_values(path)
        js = ns.get("JOBS")
        cutouts = ns.get("CUTOUTS") or set()
        items = js.items() if isinstance(js, dict) else []
        for name, v in items:            # gen_room: 이름 → (폭, 높이, 문장)
            w, h, p = v
            add(job(name, w, h, strip_style(p, ns), name in cutouts, f))
        if isinstance(js, list):
            for t in js:
                name, w, h, p = t[:4]
                cut = t[4] if len(t) > 4 else (name in cutouts or (not cutouts and w == h))
                add(job(name, w, h, strip_style(p, ns), cut, f))
    # otto_art 레시피(이 도구로 pick 한 그림)
    if os.path.isdir(RECIPES):
        for fn in os.listdir(RECIPES):
            if fn.endswith(".json"):
                r = json.load(open(os.path.join(RECIPES, fn), encoding="utf-8"))
                if r.get("style", "felt") == "felt":
                    add(job(r["name"], *r["size"], scrub(r["text"]), r["kind"] == "cut", "art_recipes"))
    return jobs


def bundled():
    names = set()
    for d in DRAWABLES:
        if os.path.isdir(d):
            for fn in os.listdir(d):
                m = re.match(r"^([a-z0-9_]+)\.(png|webp)$", fn)
                if m:
                    names.add(m.group(1))
    return names


# ── 굽기 · 고르기 ─────────────────────────────────────────────────────
def out_dir(style):
    return os.path.join(otto_art.OUT, style)


def picked(name, style):
    return any(os.path.exists(os.path.join(d, f"{name}_{style}.{e}")) for d in DRAWABLES for e in ("png", "webp"))


OUT_DIR = os.path.join(RES, "drawable-nodpi")
WEBP_Q = 85


def felt_of(name):
    """같은 이름의 펠트판 그림 (크레용판이 크기 · 자리를 맞출 기준)"""
    for d in DRAWABLES:
        for e in ("webp", "png"):
            p = os.path.join(d, f"{name}.{e}")
            if os.path.exists(p):
                return p
    return None


def _box(im):
    return im.getchannel("A").point(lambda a: 255 if a > 8 else 0).getbbox() or (0, 0, im.width, im.height)


def shrink(src, dst, kind, felt=None):
    """펠트판과 같은 판 — 앱은 그림체만 바꿔 같은 자리 · 같은 비율로 그린다 (10-07 조장 리뷰 #291)

    - 배경: 펠트 배경과 같은 크기(없으면 1344×768) · 불투명 webp
    - 오려 낸 것: 펠트판과 같은 캔버스. 크레용 물체를 여백 없이 잘라, 펠트 물체가 차지한 상자 안에 맞춰
      가로 가운데 · 밑동 맞춤으로 놓는다 — 펠트판이 물체에 딱 맞게 잘려 있으면 크레용판도 그렇다
    - 투명도 유지 webp q85, drawable-nodpi (펠트 키트 조각과 같은 방식). getIdentifier 로 부르므로 PNG 는 앱 용량을 그대로 늘린다
    """
    from PIL import Image
    im = Image.open(src)
    fe = Image.open(felt) if felt else None
    if kind == "bg":
        size = fe.size if fe else (1344, 768)
        im.convert("RGB").resize(size, Image.LANCZOS).save(dst, "WEBP", quality=WEBP_Q, method=6)
        return
    im = im.convert("RGBA")
    im = im.crop(_box(im))
    if fe is None:
        k = 512 / max(im.size)
        im.resize((max(1, round(im.width * k)), max(1, round(im.height * k))), Image.LANCZOS).save(dst, "WEBP", quality=WEBP_Q, method=6)
        return
    fe = fe.convert("RGBA")
    x0, y0, x1, y1 = _box(fe)
    k = min((x1 - x0) / im.width, (y1 - y0) / im.height)
    im = im.resize((max(1, round(im.width * k)), max(1, round(im.height * k))), Image.LANCZOS)
    out = Image.new("RGBA", fe.size, (0, 0, 0, 0))
    out.alpha_composite(im, (round((x0 + x1 - im.width) / 2), y1 - im.height))
    out.save(dst, "WEBP", quality=WEBP_Q, method=6)


def refit(style):
    """예전 방식(res/drawable/*_<그림체>.png · 긴 변 640)으로 넣은 그림을 새 방식으로 옮긴다"""
    old = os.path.join(RES, "drawable")
    n = before = after = 0
    for fn in sorted(os.listdir(old)):
        m = re.match(rf"^(.+)_{style}\.png$", fn)
        if not m:
            continue
        name = m.group(1)
        felt = felt_of(name)
        if not felt:
            continue  # 펠트판이 없는 그림(gift · style 같은 화면 그림)은 세계 그림이 아니다
        rp = os.path.join(RECIPES, f"{name}_{style}.json")
        kind = json.load(open(rp, encoding="utf-8"))["kind"] if os.path.exists(rp) else ("bg" if name.startswith("bg_") else "cut")
        src, dst = os.path.join(old, fn), os.path.join(OUT_DIR, f"{name}_{style}.webp")
        shrink(src, dst, kind, felt)
        before += os.path.getsize(src); after += os.path.getsize(dst); n += 1
        os.remove(src)
    print(f"{n}장 · {before / 2**20:.1f}MB → {after / 2**20:.1f}MB (res/drawable-nodpi/*_{style}.webp)")
    return 0


def bake(jobs, style, seeds):
    if not otto_art.reachable():
        return 1
    od = out_dir(style)
    os.makedirs(od, exist_ok=True)
    t0, n = time.time(), 0
    for name, j in jobs.items():
        if picked(name, style):
            continue
        w, h = j["size"]
        prompt = otto_art.prompt_for(j["kind"], j["text"], style)
        for k in range(seeds):
            base = os.path.join(od, f"{name}_{style}_")
            if len([f for f in os.listdir(od) if f.startswith(f"{name}_{style}_") and f.endswith(".json")]) >= seeds:
                break
            seed = random.randint(1, 2**31 - 1)
            outs = otto_art.run(otto_art.workflow(j["kind"], prompt, w, h, seed, f"{name}_{style}_{seed}"), f"{name} #{k}")
            if not outs:
                continue
            img = outs.get("14") if j["kind"] == "cut" and "14" in outs else outs["9"]
            open(f"{base}{seed}.png", "wb").write(img)
            json.dump({"name": f"{name}_{style}", "of": name, "kind": j["kind"], "text": j["text"], "style": style,
                       "size": [w, h], "seed": seed, "prompt": prompt, "model": otto_art.UNET, "source": j["source"],
                       "made": time.strftime("%Y-%m-%d")},
                      open(f"{base}{seed}.json", "w", encoding="utf-8"), ensure_ascii=False, indent=2)
            n += 1
    print(f"\n{n}장 · {time.time() - t0:.0f}초 → {od}")
    print(f"눈으로 고른 뒤:  python tools/rebake_style.py {style} pick <이름> <시드>")
    return 0


def pick(style, name, seed, replace):
    src = os.path.join(out_dir(style), f"{name}_{style}_{seed}")
    if not os.path.exists(src + ".json"):
        sys.exit(f"{src}.json 이 없다 — art_out/{style}/ 에 있는 시드를 고른다")
    r = json.load(open(src + ".json", encoding="utf-8"))
    dst = os.path.join(OUT_DIR, f"{name}_{style}.webp")
    if os.path.exists(dst) and not replace:
        sys.exit(f"res/drawable-nodpi/{name}_{style}.webp 이 이미 있다 — 바꾸려면 --replace")
    shrink(src + ".png", dst, r["kind"], felt_of(name))
    os.makedirs(RECIPES, exist_ok=True)
    shutil.copyfile(src + ".json", os.path.join(RECIPES, f"{name}_{style}.json"))
    print(f"res/drawable-nodpi/{name}_{style}.webp ← {os.path.basename(src)}.png (펠트판에 맞춤)")
    print(f"레시피 tools/art_recipes/{name}_{style}.json")
    return 0


def main():
    p = argparse.ArgumentParser(description="세계 그림을 다른 그림체로 한 벌 더 굽기 (tools/ART.md 「그림체 다시 굽기」)")
    p.add_argument("style", choices=sorted(s for s in otto_art.STYLES if s not in ("felt", "room")))
    p.add_argument("cmd", nargs="?", default="bake", choices=["bake", "pick", "refit"])
    p.add_argument("name", nargs="?")
    p.add_argument("seed", nargs="?", type=int)
    p.add_argument("--only", help="이 이름 · 앞머리만 (쉼표로) — 예: kit_park_,bg_park")
    p.add_argument("--seeds", type=int, default=2, help="그림마다 후보 몇 장 (기본 2)")
    p.add_argument("--dry-run", action="store_true", help="굽지 않고 목록만")
    p.add_argument("--replace", action="store_true")
    a = p.parse_args()
    if a.cmd == "refit":
        return refit(a.style)
    if a.cmd == "pick":
        if not a.name or a.seed is None:
            sys.exit("pick 이름 시드")
        return pick(a.style, a.name, a.seed, a.replace)

    have = bundled()
    world = sorted(n for n in have if is_world(n))
    known = collect()
    jobs = {n: known[n] for n in world if n in known}
    unmapped = [n for n in world if n not in known]
    if a.only:
        keys = [k.strip() for k in a.only.split(",") if k.strip()]
        jobs = {n: j for n, j in jobs.items() if any(n == k or n.startswith(k) for k in keys)}
    todo = {n: j for n, j in jobs.items() if not picked(n, a.style)}

    if a.dry_run:
        for n, j in jobs.items():
            mark = "고름" if n not in todo else "    "
            print(f"{mark} {n:32s} {j['kind']:3s} {j['size'][0]}x{j['size'][1]}  {j['text']}  [{j['source']}]")
        by = {}
        for n in jobs:
            by[n.split("_")[0]] = by.get(n.split("_")[0], 0) + 1
        print(f"\n세계 그림 {len(world)}장 · 대상 문장 찾음 {len(jobs)}장 · 남은 것 {len(todo)}장 · 그림체 {a.style}")
        print("  " + " · ".join(f"{k} {v}" for k, v in sorted(by.items())))
        print(f"  후보 {a.seeds}장씩이면 약 {len(todo) * a.seeds * 50 / 3600:.1f}시간 (한 장 약 50초 · 4060 기준)")
        if unmapped and not a.only:
            print(f"\n대상 문장을 못 찾은 세계 그림 {len(unmapped)}장 — otto_art.py 로 직접 뽑는다:")
            print("  " + " ".join(unmapped))
        return 0
    return bake(todo, a.style, a.seeds)


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    sys.exit(main())
