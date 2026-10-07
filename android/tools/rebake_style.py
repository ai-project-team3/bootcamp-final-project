# -*- coding: utf-8 -*-
"""그림체 다시 굽기 — 앱에 넣어 둔 **세계 그림**을 다른 그림체로 한 벌 더 굽는다 (10-07 종훈 · 크레용).

펠트가 기본이다. 부모가 설정에서 그림체를 바꾸면 앱은 `이름_<그림체>` 그림이 있으면 그것을, 없으면 펠트를 쓴다.
그래서 여기서는 원래 그림과 **같은 대상 · 같은 크기 · 같은 종류(배경 / 오려 낸 것)** 로, 그림체 문장만 바꿔 굽는다.

결정 27 — 세계만 바뀐다. 도감 인형(body_ · hero_) · 오또 · 방 · 아이콘 · 부모 화면 그림은 굽지 않는다.

원래 대상 문장은 그림을 만든 `gen_*.py` 에서 읽는다(그 파일들은 열면 바로 그림을 뽑기 시작하므로 **실행하지 않고**
첫 함수 앞의 대입문만 따로 돌려 JOBS 를 얻는다). 문장에 박힌 「felt」 같은 그림체 말은 빼고 새 그림체 문장을 붙인다.

    python tools/rebake_style.py crayon --dry-run              # 무엇을 굽나 · 대상 문장을 못 찾은 그림
    python tools/rebake_style.py crayon [--only kit_,bg_park] [--seeds 2]
    python tools/rebake_style.py crayon pick kit_park_slide 12345   # 고른 한 장 → res/drawable/kit_park_slide_crayon.png

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


def shrink(src, dst, kind):
    """shrink_assets.py 와 같은 크기 — 오려 낸 것 긴 변 640 · 배경 1344×768"""
    from PIL import Image
    im = Image.open(src)
    if kind == "bg":
        im = im.convert("RGB").resize((1344, 768), Image.LANCZOS)
    elif max(im.size) > 640:
        k = 640 / max(im.size)
        im = im.resize((round(im.width * k), round(im.height * k)), Image.LANCZOS)
    im.save(dst, optimize=True)


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
    dst = os.path.join(DRAWABLES[0], f"{name}_{style}.png")
    if os.path.exists(dst) and not replace:
        sys.exit(f"res/drawable/{name}_{style}.png 이 이미 있다 — 바꾸려면 --replace")
    shrink(src + ".png", dst, r["kind"])
    os.makedirs(RECIPES, exist_ok=True)
    shutil.copyfile(src + ".json", os.path.join(RECIPES, f"{name}_{style}.json"))
    print(f"res/drawable/{name}_{style}.png ← {os.path.basename(src)}.png (줄임)")
    print(f"레시피 tools/art_recipes/{name}_{style}.json")
    return 0


def main():
    p = argparse.ArgumentParser(description="세계 그림을 다른 그림체로 한 벌 더 굽기 (tools/ART.md 「그림체 다시 굽기」)")
    p.add_argument("style", choices=sorted(s for s in otto_art.STYLES if s not in ("felt", "room")))
    p.add_argument("cmd", nargs="?", default="bake", choices=["bake", "pick"])
    p.add_argument("name", nargs="?")
    p.add_argument("seed", nargs="?", type=int)
    p.add_argument("--only", help="이 이름 · 앞머리만 (쉼표로) — 예: kit_park_,bg_park")
    p.add_argument("--seeds", type=int, default=2, help="그림마다 후보 몇 장 (기본 2)")
    p.add_argument("--dry-run", action="store_true", help="굽지 않고 목록만")
    p.add_argument("--replace", action="store_true")
    a = p.parse_args()
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
