"""안경 그림에서 **테두리만 남기고** 렌즈 속을 비운다 (9/22).

왜 필요한가
    `gl_round.png` · `gl_square.png` 는 생성된 그림이라 렌즈 안쪽이 **불투명한 흰색**(240,240,240)이고,
    접힌 **안경다리**가 렌즈 뒤에 같이 그려져 있다. 화면 크기 기준으로 66~69%가 그 둘이다.

    이걸 얼굴 위에 얹으면
      - 눈 자리에 커다란 흰 원 두 개가 생긴다 (아이 얼굴이 아니라 인형 눈처럼 보인다)
      - 베이지색 안경다리가 얼굴과 머리카락 위로 삐져나온다

    안경은 **테두리와 코받침만** 있으면 된다. 렌즈는 비어 있어야 그 아래 눈이 보인다.

무엇을 남기나
    테두리는 짙은 갈색 계열(r ≲ 170)이고, 렌즈 속(흰색)과 안경다리(베이지)는 밝다(r ≳ 215).
    그래서 **밝기 하나로 가른다.** 경계는 부드럽게 깎아 톱니가 생기지 않게 한다.

쓰는 법
    python tools/fix_glasses.py            # 고친다 (원본은 *_orig.png 로 남긴다)
    python tools/fix_glasses.py --check    # 고치지 않고 지금 상태만 본다
"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image

DRAWABLE = Path(__file__).resolve().parent.parent / "app/src/main/res/drawable"
NAMES = ("gl_round", "gl_square")

# 이 밝기 아래는 테두리라 남기고, 위는 렌즈 속·안경다리라 지운다. 사이는 부드럽게 깎는다
KEEP_BELOW = 170
DROP_ABOVE = 215


def opaque_ratio(im: Image.Image) -> float:
    px = im.load()
    w, h = im.size
    n = hit = 0
    for y in range(0, h, 3):
        for x in range(0, w, 3):
            n += 1
            if px[x, y][3] > 40:
                hit += 1
    return hit / n if n else 0.0


def strip_lens(im: Image.Image) -> Image.Image:
    im = im.convert("RGBA")
    px = im.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            bright = max(r, g, b)
            if bright <= KEEP_BELOW:
                keep = 1.0
            elif bright >= DROP_ABOVE:
                keep = 0.0
            else:
                keep = (DROP_ABOVE - bright) / (DROP_ABOVE - KEEP_BELOW)
            px[x, y] = (r, g, b, int(a * keep))
    return im


def main() -> int:
    check = "--check" in sys.argv
    for name in NAMES:
        src = DRAWABLE / f"{name}.png"
        if not src.exists():
            print(f"없음: {src}")
            return 1
        im = Image.open(src).convert("RGBA")
        before = opaque_ratio(im)
        if check:
            print(f"{name}: {im.size} 불투명 {before:.0%}")
            continue

        # ⚠️ 백업을 drawable/ 에 두면 안드로이드가 리소스로 컴파일한다. 밖에 둔다
        backup = Path(__file__).resolve().parent / "orig" / f"{name}.png"
        backup.parent.mkdir(exist_ok=True)
        if not backup.exists():
            im.save(backup)

        out = strip_lens(im)
        after = opaque_ratio(out)
        out.save(src)
        print(f"{name}: 불투명 {before:.0%} → {after:.0%}  (원본 {backup.name})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
