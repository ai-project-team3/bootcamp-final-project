# -*- coding: utf-8 -*-
"""오또 디자인 시스템(.pen)을 만든다.

pen.dev 파일은 JSON이라 손으로 고치기보다 이 스크립트를 고쳐서 다시 만든다.
    python design/tools/build_design_system.py
결과: design/otto_design_system.pen
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "otto_design_system.pen")

TEXTURE = "assets/felt_texture.png"
MASCOT = "assets/mascot.png"
BG_SAMPLE = "assets/bg_park.png"

KID_FONT = "Jua"
PARENT_FONT = "Noto Sans KR"

# ── 토큰 ─────────────────────────────────────────────
COLORS = [
    # 무대 — 차분하게
    ("wool", "#FFF7EC", "기본 배경 · 흰 양모"),
    ("wool-cream", "#F3E7D3", "카드 · 말풍선 · 오또 배"),
    ("stage-wood", "#D9A56B", "무대 바닥 · 책장"),
    ("stage-wood-deep", "#A8733F", "나무 그림자"),
    ("curtain", "#B8322E", "벨벳 커튼 · 책 표지"),
    ("curtain-deep", "#7A1E1E", "커튼 주름"),
    ("ink", "#3A2A20", "글자 · 아이콘"),
    ("ink-soft", "#8A735F", "보조 글자 · 비활성"),
    # 펠트 강조 — 누르는 것 · 브랜드
    ("felt-coral", "#E8604C", "주 버튼 · 로고 「오」"),
    ("felt-mustard", "#F2B232", "진행 · 보상 · 로고 「또」"),
    ("felt-teal", "#4FAF98", "마이크 전용 · 오또 후드"),
    ("felt-sky", "#5B8ED6", "같이 만들기 · 후드 안감"),
    ("kitten", "#F2955A", "오또 털 · 작은 포인트"),
    ("cheek", "#F48C92", "좋아 · 칭찬 · 발바닥"),
    ("white", "#FFFFFF", "아이콘 · 부모 카드"),
]
NUMBERS = [
    ("radius-s", 12), ("radius-m", 20), ("radius-l", 28), ("radius-full", 999),
    ("space-1", 4), ("space-2", 8), ("space-3", 12), ("space-4", 16), ("space-6", 24), ("space-8", 32),
    ("touch-hero", 96), ("touch-kid", 80), ("touch-kid-min", 64), ("touch-parent", 48),
]

SHADOW_SOFT = {"type": "shadow", "shadowType": "outer", "color": "#3A2A2033",
               "offset": {"x": 0, "y": 5}, "blur": 10, "spread": 0}
SHADOW_PRESSED = {"type": "shadow", "shadowType": "outer", "color": "#3A2A2026",
                  "offset": {"x": 0, "y": 2}, "blur": 4, "spread": 0}
FUZZ = {"align": "outside", "thickness": 1.5, "fill": "#FFFFFF59"}

_seq = [0]


def nid(prefix):
    _seq[0] += 1
    return f"{prefix}{_seq[0]}"


def c(name):
    return f"$--{name}"


# ── 기본 도형 ────────────────────────────────────────
def text(content, size, color="ink", font=KID_FONT, weight="400", **kw):
    n = {"type": "text", "id": kw.pop("id", nid("t")), "content": content, "fill": c(color),
         "fontFamily": font, "fontSize": size, "fontWeight": weight}
    n.update(kw)
    return n


def icon(name, size, color="white", **kw):
    n = {"type": "icon_font", "id": kw.pop("id", nid("i")), "iconFontFamily": "lucide",
         "iconFontName": name, "width": size, "height": size, "fill": c(color)}
    n.update(kw)
    return n


def image(url, w, h, x=0, y=0, radius=0, opacity=None, name="image"):
    n = {"type": "rectangle", "id": nid("img"), "name": name, "x": x, "y": y, "width": w, "height": h,
         "cornerRadius": radius, "fill": {"type": "image", "enabled": True, "url": url, "mode": "fill"}}
    if opacity is not None:
        n["opacity"] = opacity
    return n


def center(children, w, h, gap=6, x=0, y=0, direction="vertical"):
    return {"type": "frame", "id": nid("ctr"), "name": "content", "x": x, "y": y, "width": w, "height": h,
            "layout": direction, "justifyContent": "center", "alignItems": "center", "gap": gap,
            "children": children}


def felt(name, w, h, color, radius, children=(), *, reusable=False, stitch=True, shadow=True,
         texture=0.9, x=0, y=0, id=None, stroke=None):
    """펠트 조각: 단색 + 양모 결 + 보송한 가장자리 + 바느질선 + 부드러운 그림자."""
    kids = [image(TEXTURE, w, h, radius=radius, opacity=texture, name="felt-texture")]
    if stitch:
        inset = 5
        kids.append({"type": "frame", "id": nid("st"), "name": "stitch", "x": inset, "y": inset,
                     "width": w - inset * 2, "height": h - inset * 2,
                     "cornerRadius": max(radius - inset, 0) if isinstance(radius, (int, float)) else radius,
                     "stroke": {"align": "inside", "thickness": 1.5, "fill": "#FFFFFF73"}, "layout": "none"})
    kids += list(children)
    n = {"type": "frame", "id": id or nid("f"), "name": name, "x": x, "y": y, "width": w, "height": h,
         "fill": c(color) if not color.startswith("#") else color, "cornerRadius": radius,
         "clip": False, "layout": "none", "stroke": stroke or FUZZ, "children": kids}
    if shadow:
        n["effect"] = SHADOW_SOFT
    if reusable:
        n["reusable"] = True
    return n


def ref(comp, x, y, overrides=None, **kw):
    n = {"type": "ref", "id": nid("r"), "ref": comp["id"], "x": x, "y": y}
    if overrides:
        n["descendants"] = overrides
    n.update(kw)
    return n


def board(name, x, y, w, h, children, color="wool"):
    return {"type": "frame", "id": nid("b"), "name": name, "x": x, "y": y, "width": w, "height": h,
            "fill": c(color), "clip": True, "layout": "none", "children": children}


def label(s, x, y, size=14, color="ink-soft", font=PARENT_FONT):
    return text(s, size, color, font=font, x=x, y=y)


# ── 컴포넌트 · 아이 ──────────────────────────────────
def mic():
    return felt("🎤 마이크 (기본)", 96, 96, "felt-teal", 48, reusable=True,
                children=[center([icon("mic", 44)], 96, 96)])


def mic_listening(mic_comp):
    return {"type": "frame", "id": nid("f"), "name": "🎤 마이크 (듣는 중)", "width": 160, "height": 160,
            "layout": "none", "reusable": True, "children": [
                {"type": "ellipse", "id": nid("e"), "name": "파동 2", "x": 0, "y": 0, "width": 160, "height": 160,
                 "fill": "#4FAF9826"},
                {"type": "ellipse", "id": nid("e"), "name": "파동 1", "x": 16, "y": 16, "width": 128, "height": 128,
                 "fill": "#4FAF9847"},
                ref(mic_comp, 32, 32),
            ]}


def go():
    return felt("➡️ 다음", 80, 80, "felt-coral", 40, reusable=True,
                children=[center([icon("arrow-right", 40)], 80, 80)])


def icon_kid(name="volume-2"):
    ic_id = nid("ik")
    comp = felt("◯ 아이 아이콘 버튼", 64, 64, "wool-cream", 32, reusable=True,
                children=[center([icon(name, 30, "ink", id=ic_id)], 64, 64)])
    comp["_icon"] = ic_id
    return comp


def tool(selected=False):
    color = "felt-mustard" if selected else "wool-cream"
    ic = "white" if selected else "ink"
    ic_id = nid("tl")
    comp = felt("🔨 미션 도구" + (" (고름)" if selected else ""), 64, 64, color, 32, reusable=True,
                children=[center([icon("hand", 30, ic, id=ic_id)], 64, 64)])
    comp["_icon"] = ic_id
    return comp


def choice(selected=False):
    lbl_id, pic_id = nid("cl"), nid("cp")
    kids = [
        {"type": "rectangle", "id": pic_id, "name": "그림 자리", "x": 15, "y": 14, "width": 120, "height": 108,
         "cornerRadius": 18, "fill": c("white")},
        center([text("공룡 숲", 20, id=lbl_id)], 150, 36, x=0, y=124),
    ]
    stroke = {"align": "inside", "thickness": 5, "fill": c("felt-coral")} if selected else None
    if selected:
        kids.append(felt("고름 표시", 36, 36, "felt-coral", 18, stitch=False, shadow=False, x=124, y=-10,
                         children=[center([icon("check", 22)], 36, 36)]))
    comp = felt("🃏 그림 카드" + (" (고름)" if selected else ""), 150, 166, "wool-cream", 28,
                reusable=True, children=kids, stroke=stroke)
    if selected:
        comp["effect"] = {"type": "shadow", "shadowType": "outer", "color": "#E8604C40",
                          "offset": {"x": 0, "y": 8}, "blur": 16, "spread": 0}
    comp["_label"], comp["_pic"] = lbl_id, pic_id
    return comp


def yes():
    return felt("💗 좋아", 132, 132, "cheek", 36, reusable=True,
                children=[center([icon("heart", 52), text("좋아", 22, "white")], 132, 132)])


def again():
    return felt("🔁 다시", 132, 132, "wool-cream", 36, reusable=True,
                children=[center([icon("rotate-ccw", 48, "ink"), text("다시", 22)], 132, 132)])


def bubble():
    txt_id = nid("bt")
    return {"type": "frame", "id": nid("f"), "name": "💬 마스코트 말풍선", "width": 520, "height": 96,
            "layout": "none", "reusable": True, "_text": txt_id, "children": [
                image(MASCOT, 118, 118, x=-10, y=-24, name="마스코트 (누르면 다시 듣기)"),
                felt("다시 듣기 표시", 30, 30, "felt-teal", 15, stitch=False, x=74, y=60,
                     children=[center([icon("volume-2", 16)], 30, 30)]),
                felt("말풍선", 404, 72, "wool", 24, stitch=False, x=112, y=12, children=[
                    {"type": "rectangle", "id": nid("tail"), "name": "꼬리", "x": -8, "y": 44,
                     "width": 18, "height": 18, "cornerRadius": 4, "fill": c("wool")},
                    center([text("공룡이 어디로 갈까? 골라 줘!", 22, id=txt_id)], 404, 72, x=0, y=0),
                ]),
            ]}


def progress(done=3, total=6):
    kids = [{"type": "rectangle", "id": nid("yarn"), "name": "털실", "x": 10, "y": 13, "width": 220, "height": 3,
             "cornerRadius": 2, "fill": c("stage-wood-deep")}]
    step = 220 / (total - 1)
    for i in range(total):
        filled = i < done
        kids.append(felt(f"방울 {i + 1}", 26, 26, "felt-mustard" if filled else "wool-cream", 13,
                         stitch=False, shadow=filled, x=round(i * step - 3 + 10 - 10), y=1))
    return {"type": "frame", "id": nid("f"), "name": "🧶 진행 (털실 방울)", "width": 240, "height": 28,
            "layout": "none", "reusable": True, "children": kids}


def parent_gate():
    return {"type": "frame", "id": nid("f"), "name": "🔒 부모 문 (2초 길게 누르기)", "width": 40, "height": 40,
            "cornerRadius": 20, "fill": "#3A2A201A", "layout": "none", "reusable": True,
            "children": [center([icon("lock", 18, "ink-soft")], 40, 40)]}


def hotspot_hint():
    return {"type": "frame", "id": nid("f"), "name": "✨ 만질 수 있어요 (안 만지면 반짝)", "width": 56, "height": 56,
            "layout": "none", "reusable": True, "children": [
                {"type": "ellipse", "id": nid("e"), "x": 0, "y": 0, "width": 56, "height": 56, "fill": "#F2B23233"},
                center([icon("sparkles", 30, "felt-mustard")], 56, 56),
            ]}


def mode_tile(color, ic, title):
    ic_id, t_id = nid("mi"), nid("mt")
    comp = felt(f"🎭 모드 타일 · {title}", 164, 200, color, 32, reusable=True, children=[
        center([icon(ic, 72, id=ic_id), text(title, 22, "white", id=t_id)], 164, 200, gap=14)])
    comp["_icon"], comp["_title"] = ic_id, t_id
    return comp


def crayon(color):
    return felt("🖍 크레용", 48, 48, color, 24, stitch=False, reusable=True)


# ── 컴포넌트 · 부모 ──────────────────────────────────
def p_button(primary=True):
    t_id = nid("pb")
    n = {"type": "frame", "id": nid("f"), "name": "부모 버튼 · " + ("주" if primary else "보조"),
         "width": 200, "height": 52, "cornerRadius": 16, "reusable": True,
         "fill": c("felt-coral") if primary else c("white"),
         "layout": "horizontal", "justifyContent": "center", "alignItems": "center",
         "children": [text("동의하고 시작", 16, "white" if primary else "ink", font=PARENT_FONT,
                           weight="700", id=t_id)]}
    if not primary:
        n["stroke"] = {"align": "inside", "thickness": 1.5, "fill": "#3A2A2026"}
    n["_text"] = t_id
    return n


def p_row():
    return {"type": "frame", "id": nid("f"), "name": "부모 목록 줄", "width": 360, "height": 56,
            "cornerRadius": 16, "fill": c("white"), "reusable": True, "layout": "horizontal",
            "alignItems": "center", "justifyContent": "space_between", "padding": [0, 16],
            "children": [text("효과음 · 진동", 16, font=PARENT_FONT, weight="500"),
                         p_toggle_inline()]}


def p_toggle_inline(on=True):
    return {"type": "frame", "id": nid("tg"), "name": "토글", "width": 52, "height": 32, "cornerRadius": 16,
            "fill": c("felt-coral") if on else "#3A2A2026", "layout": "none", "children": [
                {"type": "ellipse", "id": nid("e"), "x": 24 if on else 4, "y": 4, "width": 24, "height": 24,
                 "fill": c("white")}]}


def p_pinkey():
    return {"type": "frame", "id": nid("f"), "name": "PIN 키", "width": 72, "height": 56, "cornerRadius": 16,
            "fill": c("wool-cream"), "reusable": True, "layout": "horizontal", "justifyContent": "center",
            "alignItems": "center", "children": [text("1", 24, font=PARENT_FONT, weight="600")]}


def p_card():
    return {"type": "frame", "id": nid("f"), "name": "부모 카드", "width": 360, "height": 140, "cornerRadius": 24,
            "fill": c("white"), "reusable": True, "effect": SHADOW_PRESSED, "layout": "vertical",
            "padding": [20, 20], "gap": 8, "children": [
                text("오늘 아이가 한 말", 13, "ink-soft", font=PARENT_FONT, weight="500"),
                text("\"공룡이 수영장에서 첨벙첨벙 했어!\"", 18, font=PARENT_FONT, weight="700"),
                text("9월 28일 · 공룡 숲 이야기", 13, "ink-soft", font=PARENT_FONT)]}


# ── 컴포넌트 · 마주 보고 대화 (Buddy.ai · Moxie · Miko 참고) ────────────
STATE = {  # 오또 발밑 물결: 몸짓과 함께 쓰는 두 번째 신호
    "talk": ("#F48C92", "말하는 중", "message-circle"),
    "listen": ("#4FAF98", "듣는 중 (이때만 녹음)", "ear"),
    "think": ("#F2B232", "생각하는 중", "ellipsis"),
}


def halo(state):
    colr, nm, _ = STATE[state]
    return {"type": "frame", "id": nid("f"), "name": f"🌊 발밑 물결 · {nm}", "width": 240, "height": 44,
            "layout": "none", "reusable": True, "children": [
                {"type": "ellipse", "id": nid("e"), "x": 0, "y": 0, "width": 240, "height": 44, "fill": colr + "33"},
                {"type": "ellipse", "id": nid("e"), "x": 30, "y": 6, "width": 180, "height": 32, "fill": colr + "59"},
                {"type": "ellipse", "id": nid("e"), "x": 70, "y": 12, "width": 100, "height": 20, "fill": colr + "8C"},
            ]}


def state_badge(state):
    colr, nm, ic = STATE[state]
    return felt(f"💬 상태 표시 · {nm}", 44, 44, colr, 22, stitch=False, reusable=True,
                children=[center([icon(ic, 24)], 44, 44)])


def voice_pill():
    bars = [10, 22, 34, 18, 28, 12, 24, 16]
    kids = []
    for i, h in enumerate(bars):
        kids.append({"type": "rectangle", "id": nid("bar"), "x": 20 + i * 12, "y": 28 - h // 2, "width": 6,
                     "height": h, "cornerRadius": 3, "fill": c("white")})
    return felt("🎙 아이 목소리 (들리는 만큼 출렁)", 136, 56, "felt-teal", 28, stitch=False, reusable=True,
                children=kids)


def echo_card():
    t_id = nid("ec")
    comp = felt("🔁 되받아 말하기 (들은 말 확인)", 300, 64, "wool", 24, stitch=False, reusable=True, children=[
        felt("귀", 40, 40, "felt-teal", 20, stitch=False, shadow=False, x=12, y=12,
             children=[center([icon("ear", 22)], 40, 40)]),
        text("\"빨간 공룡이구나!\"", 22, id=t_id, x=64, y=18)])
    comp["_text"] = t_id
    return comp


def choice_bubble(selected=False):
    lbl = nid("cb")
    kids = [{"type": "ellipse", "id": nid("pic"), "name": "그림 자리", "x": 14, "y": 12, "width": 100, "height": 100,
             "fill": c("white")},
            center([text("공룡 숲", 18, id=lbl)], 128, 28, x=0, y=114)]
    stroke = {"align": "inside", "thickness": 5, "fill": c("felt-coral")} if selected else None
    comp = felt("🫧 고르기 방울" + (" (고름)" if selected else ""), 128, 150, "wool-cream", 64,
                reusable=True, children=kids, stroke=stroke)
    comp["_label"] = lbl
    return comp


def page_slots(done=3, total=6):
    kids = []
    for i in range(total):
        filled = i < done
        kids.append(felt(f"쪽 {i + 1}", 28, 22, "felt-mustard" if filled else "wool-cream", 6, stitch=False,
                         shadow=filled, x=i * 36, y=0))
    return {"type": "frame", "id": nid("f"), "name": "📖 진행 · 책 쪽 칸 6개", "width": 28 + (total - 1) * 36,
            "height": 22, "layout": "none", "reusable": True, "children": kids}


def home_btn():
    return felt("🏠 방으로", 56, 56, "wool-cream", 28, reusable=True,
                children=[center([icon("house", 26, "ink")], 56, 56)])



# ── 오또의 방 (Toca · Pok Pok · Khan Kids 참고) ──────────────────────
def room_objects():
    """방 물건 = 메뉴. 기능이 연결된 물건만 반짝임 자리를 둔다."""
    wall_stripes = [{"type": "rectangle", "id": nid("wp"), "name": "벽지 줄", "x": 20 + i * 64, "y": 0, "width": 22,
                     "height": 262, "fill": "#F3E7D380"} for i in range(13)]
    window = felt("🪟 창문 = 오늘 이야기", 150, 120, "stage-wood", 20, x=36, y=34, children=[
        {"type": "rectangle", "id": nid("sky"), "name": "하늘", "x": 12, "y": 12, "width": 126, "height": 96,
         "cornerRadius": 12, "fill": "#CFE6EE"},
        felt("해", 44, 44, "felt-mustard", 22, stitch=False, shadow=False, x=76, y=22),
        {"type": "rectangle", "id": nid("cl"), "name": "구름", "x": 24, "y": 62, "width": 60, "height": 22,
         "cornerRadius": 11, "fill": c("white")},
        {"type": "rectangle", "id": nid("mul"), "name": "창살", "x": 73, "y": 12, "width": 4, "height": 96,
         "fill": c("stage-wood")}])
    theater = felt("🎭 작은 인형극 무대 = 이야기 만들기", 196, 186, "stage-wood-deep", 24, x=392, y=76, children=[
        felt("무대 안", 164, 116, "curtain-deep", 14, stitch=False, shadow=False, x=16, y=34),
        felt("막 왼쪽", 50, 116, "curtain", 12, stitch=False, shadow=False, x=16, y=34),
        felt("막 오른쪽", 50, 116, "curtain", 12, stitch=False, shadow=False, x=130, y=34),
        felt("간판", 110, 26, "felt-mustard", 13, stitch=False, shadow=False, x=43, y=6,
             children=[center([icon("star", 16)], 110, 26)]),
        felt("무대 바닥", 164, 20, "stage-wood", 6, stitch=False, shadow=False, x=16, y=150)])
    sofa = felt("🛋 소파 = 같이 만들기", 190, 96, "felt-sky", 30, x=22, y=210, children=[
        felt("쿠션 1", 64, 52, "cheek", 20, stitch=False, x=24, y=-18),
        felt("쿠션 2", 64, 52, "felt-mustard", 20, stitch=False, x=100, y=-18)])
    shelf_books = []
    colors = ["felt-coral", "felt-teal", "felt-mustard", "felt-sky", "cheek", "kitten"]
    for r in range(3):
        for k in range(4):
            shelf_books.append({"type": "rectangle", "id": nid("bk"), "name": "책", "x": 14 + k * 26,
                                "y": 18 + r * 70, "width": 20, "height": 52 - (k % 2) * 8 + (k % 2) * 8,
                                "cornerRadius": 4, "fill": c(colors[(r * 4 + k) % 6])})
    shelf = felt("📚 책장 = 내 책", 134, 236, "stage-wood", 18, x=640, y=46, children=[
        *[{"type": "rectangle", "id": nid("sh"), "name": "선반", "x": 8, "y": 72 + r * 70, "width": 118,
           "height": 6, "cornerRadius": 3, "fill": c("stage-wood-deep")} for r in range(3)], *shelf_books])
    return wall_stripes, window, theater, sofa, shelf


# ── 별 모으기 진행 (지금 앱 ProgressTrack을 펠트로) ─────────────────────
import math as _m


def star_geom(s, inner=0.48, pad=0):
    cx = cy = s / 2
    R = s / 2 - pad
    r = R * inner
    pts = []
    for i in range(10):
        a = -_m.pi / 2 + i * _m.pi / 5
        rad = R if i % 2 == 0 else r
        pts.append((cx + rad * _m.cos(a), cy + rad * _m.sin(a) + s * 0.03))
    return "M" + " L".join(f"{x:.2f} {y:.2f}" for x, y in pts) + " Z"


def star(s, fill, x=0, y=0, stroke=None, name="별", opacity=None):
    n = {"type": "path", "id": nid("star"), "name": name, "x": x, "y": y, "width": s, "height": s,
         "geometry": star_geom(s, pad=1.5), "fill": fill}
    if stroke:
        n["stroke"] = {"align": "center", "thickness": stroke[1], "join": "round", "cap": "round", "fill": stroke[0]}
    if opacity is not None:
        n["opacity"] = opacity
    return n


def star_progress(done, total=6):
    TW, TH, TY = 300, 26, 17          # 털실 길 (트랙)
    S = 60                            # 끝의 큰 별
    complete = done >= total
    fill_w = max(TH, TW * done / total)
    kids = [
        # 트랙: 크림 펠트 + 바느질선 + 안쪽 그늘
        felt("길", TW, TH, "wool-cream", TH // 2, stitch=False, shadow=False, x=0, y=TY, children=[
            {"type": "rectangle", "id": nid("in"), "name": "안쪽 그늘", "x": 2, "y": 2, "width": TW - 4, "height": 5,
             "cornerRadius": 3, "fill": "#3A2A2014"}]),
    ]
    if done > 0:
        kids.append(felt("차오른 만큼", fill_w, TH, "felt-mustard", TH // 2, stitch=False, shadow=False, x=0, y=TY,
                         children=[
                             {"type": "rectangle", "id": nid("hl"), "name": "윤기", "x": 6, "y": 4, "width": max(fill_w - 12, 4),
                              "height": 5, "cornerRadius": 3, "fill": "#FFFFFF66"},
                             {"type": "rectangle", "id": nid("sh"), "name": "지나가는 빛 (1.6초마다)",
                              "x": max(fill_w * 0.42, 4), "y": 3, "width": 12, "height": TH - 4, "cornerRadius": 7,
                              "fill": "#FFFFFF59"}]))
    # 쪽마다 작은 별 (지나간 쪽은 흰 별, 남은 쪽은 흐린 별)
    for i in range(1, total):
        px = TW * i / total - 8
        passed = i <= done
        kids.append(star(18, c("white") if passed else "#8A735F40", x=px - 1, y=TY + 4,
                         name=f"{i}쪽 별" + (" (지남)" if passed else "")))
    # 끝의 큰 별
    sx, sy = TW - 8, 0
    if complete:
        kids += [{"type": "ellipse", "id": nid("glow"), "name": "빛 (커졌다 작아졌다)", "x": sx - 12, "y": sy - 12,
                  "width": S + 24, "height": S + 24, "fill": "#F2B23240"},
                 star(S, c("felt-mustard"), x=sx, y=sy, stroke=("#C98A12", 2.5), name="큰 별 (완성)"),
                 star(S * 0.62, "#FFFFFF4D", x=sx + S * 0.19, y=sy + S * 0.2, name="윤기"),
                 icon("sparkles", 18, "felt-mustard", x=sx + S - 2, y=sy - 8)]
    else:
        part = 0.25 + 0.6 * done / total
        kids += [star(S, c("white"), x=sx, y=sy, stroke=("#D9C8A8", 2.5), name="큰 별 (빈)"),
                 star(S * part, c("felt-mustard"), x=sx + S * (1 - part) / 2, y=sy + S * (1 - part) / 2 + 1,
                      name="안쪽에 조금씩 차오름")]
    label_ = "완성" if complete else f"{done}/{total}"
    n = {"type": "frame", "id": nid("f"), "name": f"⭐ 별 모으기 진행 · {label_}", "width": TW + S - 8 + 14,
         "height": S, "layout": "none", "clip": False, "reusable": True, "children": kids}
    return n


# ── 05 화면 흐름도 ───────────────────────────────────────────
KIND = {  # 누가 보는 화면인가
    "parent": ("white", "ink"),          # 보호자가 보는 화면
    "kid": ("wool-cream", "ink"),        # 아이가 보는 화면
    "hub": ("felt-teal", "white"),       # 갈림길 · 모이는 곳
    "danger": ("#FBE3DF", "ink"),        # 되돌릴 수 없는 일
}


def node(x, y, w, h, title, sub="", kind="kid", new=False, name=None):
    fillc, txt = KIND[kind]
    fill = c(fillc) if not fillc.startswith("#") else fillc
    kids = [text(title, 15, txt, font=PARENT_FONT, weight="700", x=14, y=12, width=w - 28,
                 textGrowth="fixed-width")]
    if sub:
        kids.append(text(sub, 12, txt if kind == "hub" else "ink-soft", font=PARENT_FONT, x=14, y=36,
                         width=w - 28, textGrowth="fixed-width", lineHeight=1.4))
    if new:
        kids.append({"type": "frame", "id": nid("nw"), "name": "새로 생김", "x": w - 44, "y": -9, "width": 40,
                     "height": 18, "cornerRadius": 9, "fill": c("felt-mustard"), "layout": "horizontal",
                     "justifyContent": "center", "alignItems": "center",
                     "children": [text("NEW", 10, "white", font=PARENT_FONT, weight="700")]})
    n = {"type": "frame", "id": nid("n"), "name": name or title, "x": x, "y": y, "width": w, "height": h,
         "cornerRadius": 16, "fill": fill, "layout": "none", "effect": SHADOW_PRESSED, "children": kids}
    if kind == "parent":
        n["stroke"] = {"align": "inside", "thickness": 1.5, "fill": "#3A2A2026"}
    if kind == "danger":
        n["stroke"] = {"align": "inside", "thickness": 1.5, "fill": c("felt-coral")}
    return n


def arrow(x1, y1, x2, y2, color="ink-soft", label_=None):
    """가로 또는 세로 화살표 (꺾이면 가로 → 세로 두 토막)."""
    out = []
    col_ = c(color)
    if y1 == y2 or x1 == x2:
        segs = [(x1, y1, x2, y2)]
    else:
        segs = [(x1, y1, x2, y1), (x2, y1, x2, y2)]
    for (a, b, cc, d) in segs:
        if b == d:
            out.append({"type": "rectangle", "id": nid("ar"), "name": "선", "x": min(a, cc), "y": b - 1,
                        "width": abs(cc - a), "height": 2.5, "fill": col_})
        else:
            out.append({"type": "rectangle", "id": nid("ar"), "name": "선", "x": a - 1, "y": min(b, d),
                        "width": 2.5, "height": abs(d - b), "fill": col_})
    a, b, cc, d = segs[-1]
    if b == d:
        right = cc > a
        g = "M0 0 L10 5 L0 10 Z" if right else "M10 0 L0 5 L10 10 Z"
        out.append({"type": "path", "id": nid("ah"), "name": "화살촉", "x": cc - (10 if right else 0), "y": d - 5,
                    "width": 10, "height": 10, "geometry": g, "fill": col_})
    else:
        down = d > b
        g = "M0 0 L10 0 L5 10 Z" if down else "M0 10 L10 10 L5 0 Z"
        out.append({"type": "path", "id": nid("ah"), "name": "화살촉", "x": cc - 5, "y": d - (10 if down else 0),
                    "width": 10, "height": 10, "geometry": g, "fill": col_})
    if label_:
        mx, my = (segs[0][0] + segs[0][2]) / 2, segs[0][1]
        out.append(text(label_, 11, "ink-soft", font=PARENT_FONT, x=mx - 22, y=my - 18))
    return out


def arrow3(x1, y1, xm, y2, x2, color="ink-soft"):
    """가로 → 세로 → 가로로 꺾이는 화살표 (갈라지거나 모일 때)."""
    col_ = c(color)
    out = []
    if x1 != xm:
        out.append({"type": "rectangle", "id": nid("ar"), "name": "선", "x": min(x1, xm), "y": y1 - 1,
                    "width": abs(xm - x1), "height": 2.5, "fill": col_})
    if y1 != y2:
        out.append({"type": "rectangle", "id": nid("ar"), "name": "선", "x": xm - 1, "y": min(y1, y2),
                    "width": 2.5, "height": abs(y2 - y1) + 1, "fill": col_})
    return out + arrow(xm, y2, x2, y2, color)


def lane(title, y, h, note=""):
    kids = [{"type": "rectangle", "id": nid("ln"), "name": "구역", "x": 0, "y": y, "width": 2440, "height": h,
             "fill": "#F3E7D366", "cornerRadius": 24},
            text(title, 22, "ink", font=PARENT_FONT, weight="700", x=28, y=y + 20, width=160,
                 textGrowth="fixed-width")]
    if note:
        kids.append(text(note, 12, "ink-soft", font=PARENT_FONT, x=28, y=y + 84, width=156,
                         textGrowth="fixed-width", lineHeight=1.4))
    return kids


def flow_board():
    K = []
    W, H = 188, 112
    K += [text("05 화면 흐름도 — 처음 설치부터 종료까지", 36, x=28, y=28),
          text("저장하는 정보는 보호자 정보(로그인 계정)뿐. 아이 이름 · 나이는 받지 않는다. 녹음 · 그림 · 책은 폰 안에만.",
               14, "ink-soft", font=PARENT_FONT, x=30, y=80)]
    lx = 1420
    for i, (k, nm) in enumerate([("parent", "보호자가 보는 화면"), ("kid", "아이가 보는 화면"),
                                 ("hub", "갈림길 · 모이는 곳"), ("danger", "되돌릴 수 없음")]):
        K.append(node(lx + i * 196, 30, 180, 44, nm, kind=k))
    K.append(node(lx + 784, 30, 160, 44, "지금 앱에 없음", kind="kid", new=True))

    # ① 처음 한 번
    Y = 130
    K += lane("처음 한 번", Y, 330, "보호자가 먼저 본다\n(Duolingo ABC · Khan Kids)")
    y = Y + 40
    xs = [200, 420, 780, 1000, 1220, 1440, 1660]
    K.append(node(xs[0], y, W, H, "① 스플래시", "커튼이 열리며 오또 로고"))
    login = node(xs[1], y, 300, 250, "② 로그인 / 회원가입", "보호자 계정 하나 · 아이 정보는 받지 않음", "parent", new=True)
    for i, (nm, colr, tc) in enumerate([("카카오로 시작", "#FEE500", "ink"), ("네이버로 시작", "#03C75A", "white"),
                                        ("Google로 시작", "white", "ink"), ("이메일로 시작", "wool-cream", "ink")]):
        login["children"].append({"type": "frame", "id": nid("lb"), "name": nm, "x": 14, "y": 66 + i * 44,
                                  "width": 272, "height": 36, "cornerRadius": 12,
                                  "fill": colr if colr.startswith("#") else c(colr),
                                  "stroke": {"align": "inside", "thickness": 1, "fill": "#3A2A201F"},
                                  "layout": "horizontal", "justifyContent": "center", "alignItems": "center",
                                  "children": [text(nm, 13, tc, font=PARENT_FONT, weight="700")]})
    K.append(login)
    K.append(node(xs[2], y, W, H, "③ 보호자 동의", "만 14세 미만 법정대리인 동의 · 개인정보처리방침", "parent"))
    K.append(node(xs[3], y, W, H, "④ 마이크 켜기", "고지 → 권한 요청 · 아이가 권한 창을 보지 않게", "parent"))
    K.append(node(xs[4], y, W, H, "⑤ 아이에게 건네기", "준비 끝 · 부모 PIN은 만들지 않음", "parent"))
    K.append(node(xs[5], y, W, H, "⑥ 튜토리얼", "방에서 눌러 보기 → 말해 보기 연습", new=True))
    K.append(node(xs[6], y, W, H, "⑦ 기능 소개", "해 본 것을 세 가지로 정리: 말하면 → 그림책 → 책장", new=True))
    K += arrow(xs[0] + W, y + H / 2, xs[1], y + H / 2)
    K += arrow(xs[1] + 300, y + H / 2, xs[2], y + H / 2, label_="새 계정")
    for a_, b_ in [(2, 3), (3, 4), (4, 5), (5, 6)]:
        K += arrow(xs[a_] + W, y + H / 2, xs[b_], y + H / 2)
    K.append(text("이미 계정이 있으면 → ⑧ 실행으로 바로", 12, "felt-teal", font=PARENT_FONT, weight="700",
                  x=xs[1] + 14, y=y + 262))
    K.append(text("처음 한 번이 끝나면 → ⑨ 오또의 방", 12, "felt-teal", font=PARENT_FONT, weight="700",
                  x=xs[6], y=y + H + 12))

    # ② 매일
    Y2 = 490
    K += lane("매일", Y2, 520, "선물이 방에 쌓임 (Khan Kids)\n\n하루 한도는 부모만 연장 (Lingokids)")
    y2 = Y2 + 40
    K.append(node(200, y2 + 162, W, H, "⑧ 실행", "자동 로그인 → 바로 오또의 방\n(프로필 고르기 없음)"))
    K.append(node(420, y2 + 150, 200, 136, "⑨ 오또의 방", "오또가 추천 물건을 가리킴 · 5~8초 조용하면 물건 이름을 말함",
                  "hub", new=True))
    K += arrow(200 + W, y2 + 218, 420, y2 + 218)
    mx = 700
    story = node(mx, y2, 420, 176, "🎭 인형극 무대 = 이야기 만들기", "", "kid")
    steps = ["함께 하는 사람", "★ 주인공 고르기·만들기", "장소", "사건", "까닭", "그림판", "이야기 잇기",
             "같이 갈 친구", "소리 녹음", "확인", "매듭"]
    for i, sname in enumerate(steps):
        hi = sname.startswith("★")
        story["children"].append({"type": "frame", "id": nid("st"), "name": sname, "x": 14 + (i % 4) * 100,
                                  "y": 42 + (i // 4) * 42, "width": 94, "height": 34, "cornerRadius": 10,
                                  "fill": c("felt-mustard") if hi else c("white"), "layout": "horizontal",
                                  "justifyContent": "center", "alignItems": "center",
                                  "children": [text(sname.replace("★ ", ""), 10 if hi else 11, "white" if hi else "ink",
                                                    font=PARENT_FONT, weight="700" if hi else "500")]})
    K.append(story)
    K.append(text("★ 캐릭터 커스텀은 여기서 — 도감에서 고르거나 새로 만듦", 12, "felt-mustard", font=PARENT_FONT,
                  weight="700", x=mx, y=y2 + 184))
    K.append(node(mx, y2 + 216, 420, 80, "🪟 창문 = 오늘 이야기", "오늘 있었던 일로 기승전결 네 걸음 + 꼬리질문"))
    K.append(node(mx, y2 + 310, 420, 80, "🛋 소파 = 같이 만들기", "부모가 미리 넣어 둔 질문 + 화면 아래 부모 띠"))
    for yy in (y2 + 88, y2 + 256, y2 + 350):
        K += arrow3(620, y2 + 218, 660, yy, mx)
    jx = 1180
    K.append(node(jx, y2 + 150, 170, 136, "⑩ 책 만드는 중", "제목 짓기 질문으로 기다림을 채움", "hub"))
    for yy in (y2 + 88, y2 + 256, y2 + 350):
        K += arrow3(mx + 420, yy, jx - 30, y2 + 218, jx)
    seq = [("⑪ 책 읽기", "6~8쪽 · 만지면 반응 · 미션 2개"), ("⑫ 친구 평가", "또 만날래 / 안녕"),
           ("⑬ 선물", "받은 선물이 오또의 방 장식이 됨"), ("⑭ 책장에 꽂기", "새 책이 내려와 꽂힘")]
    px = jx + 170 + 40
    for i, (t_, s_) in enumerate(seq):
        K.append(node(px + i * 210, y2 + 170, 180, H, t_, s_, new=(i == 2)))
        K += arrow((jx + 170) if i == 0 else px + (i - 1) * 210 + 180, y2 + 218, px + i * 210, y2 + 218)
    endx = px + 3 * 210 + 90
    K += [{"type": "rectangle", "id": nid("ar"), "name": "선", "x": endx - 1, "y": y2 + 266, "width": 2.5,
           "height": 184, "fill": c("felt-teal")},
          {"type": "rectangle", "id": nid("ar"), "name": "선", "x": 520, "y": y2 + 448, "width": endx - 520,
           "height": 2.5, "fill": c("felt-teal")}]
    K += arrow(520, y2 + 450, 520, y2 + 286, color="felt-teal")
    K.append(text("다 끝나면 오또의 방으로 돌아옴", 13, "felt-teal", font=PARENT_FONT, weight="700", x=1300, y=y2 + 424))
    K.append(node(2180, y2 + 10, 220, 120, "⑮ 하루 한도에 닿으면", "어느 단계에서든 · 오또가 하품하며 \"오늘은 여기까지\" · 연장은 태어난 해 확인으로만",
                  new=True))
    K.append(node(2180, y2 + 330, 220, 70, "⑯ 종료", "다시 열면 ⑧부터"))

    # ③ 어디서든 — 부모 영역
    Y3 = 1040
    K += lane("어디서든 — 부모 영역", Y3, 470, "왼쪽 위 🔒\n2초 길게 누르기\n(Sago · Pok Pok)")
    y3 = Y3 + 50
    K.append(node(200, y3 + 150, W, H, "🔒 부모 문", "어느 아이 화면에서든"))
    K.append(node(420, y3 + 150, W, H, "태어난 해 확인", "PIN 대신 · 틀려도 잠그지 않음", "parent"))
    K.append(node(640, y3 + 130, 200, 136, "부모 영역", "닫으면 원래 화면으로", "hub"))
    K += arrow(200 + W, y3 + 198, 420, y3 + 198)
    K += arrow(420 + W, y3 + 198, 640, y3 + 198)
    menu = [("기록", "6축 카드 · \"오늘 아이가 한 말 그대로\""), ("같이 만들기 질문", "템플릿을 탭해서 채움"),
            ("설정", "하루 한도 · 그림체 · 효과음 · 부모 PIN(선택)"), ("신고 · 동의 철회", "")]
    for i, (t_, s_) in enumerate(menu):
        K.append(node(920, y3 + i * 100, 220, 84, t_, s_, "parent"))
        K += arrow3(840, y3 + 198, 880, y3 + i * 100 + 42, 920)
    K.append(node(1220, y3 + 130, 240, 136, "계정", "로그인 정보 (카카오 · 네이버 · Google · 이메일) · 로그아웃 · 회원 탈퇴 · 데이터 삭제",
                  "parent", new=True))
    K += arrow(840, y3 + 198, 1220, y3 + 198)
    d = [("탈퇴 안내", "지워지는 것: 계정 · 기록 / 폰 안의 책 · 그림 · 녹음도 지울지 고름", "parent"),
         ("본인 다시 확인", "비밀번호 또는 소셜 로그인 다시 하기", "parent"),
         ("삭제", "서버: 보호자 계정 즉시 삭제 / 폰: 고른 데이터 삭제", "danger"),
         ("① 스플래시로", "처음 설치한 상태", "kid")]
    for i, (t_, s_, k_) in enumerate(d):
        K.append(node(1540 + i * 220, y3 + 150, 196, 110, t_, s_, k_, new=(i < 3)))
        K += arrow((1460 if i == 0 else 1540 + (i - 1) * 220 + 196), y3 + 205, 1540 + i * 220, y3 + 205,
                   color="felt-coral" if i else "ink-soft")
    K.append(text("로그아웃은 데이터를 지우지 않고 ③ 로그인으로", 12, "ink-soft", font=PARENT_FONT, x=1220, y=y3 + 280))

    # ④ 예외
    Y4 = 1540
    K += lane("예외", Y4, 190)
    ex = [("마이크 거부", "고르기 방울만으로 진행 · 부모 영역에 \"마이크를 켜 주세요\""),
          ("못 알아들음", "한 번 더 쉽게 묻기 → 고르기 방울"),
          ("오프라인", "책장 · 만든 책 읽기만 · 새 책은 오또가 \"조금 뒤에 하자\""),
          ("로그인 만료", "아이에게는 \"어른 불러 줘\" 음성 → 부모가 ③에서 다시 로그인"),
          ("앱 삭제 후 다시 설치", "로그인하면 기록은 돌아옴 · 폰 안의 책 · 녹음은 돌아오지 않음")]
    for i, (t_, s_) in enumerate(ex):
        K.append(node(200 + i * 440, Y4 + 50, 400, 110, t_, s_, "parent" if i in (3, 4) else "kid",
                      new=i in (2, 3, 4)))
    return board("05 화면 흐름도", 0, 2240, 2440, 1760, K)


# ── 06 화면 — 흐름도 순서대로 ────────────────────────────────────
A = "assets/"


def wall(h=360, floor=None):
    kids = [{"type": "rectangle", "id": nid("wp"), "name": "벽지 줄", "x": 20 + i * 64, "y": 0, "width": 22,
             "height": h, "fill": "#F3E7D380"} for i in range(13)]
    if floor:
        kids.append(felt("바닥", 800, 360 - floor, "stage-wood", 0, stitch=False, shadow=False, x=0, y=floor))
    return kids


def wtext(s, size, color="ink", font=PARENT_FONT, weight="400", x=0, y=0, w=300, lh=1.45):
    return text(s, size, color, font=font, weight=weight, x=x, y=y, width=w, textGrowth="fixed-width",
                lineHeight=lh)


def pbtn(label_, x, y, w=300, h=48, kind="primary"):
    fill = {"primary": c("felt-coral"), "secondary": c("white"), "ghost": "#00000000",
            "disabled": "#3A2A201A"}[kind]
    tc = {"primary": "white", "secondary": "ink", "ghost": "ink-soft", "disabled": "ink-soft"}[kind]
    n = {"type": "frame", "id": nid("pb"), "name": f"버튼 · {label_}", "x": x, "y": y, "width": w, "height": h,
         "cornerRadius": 14, "fill": fill, "layout": "horizontal", "justifyContent": "center", "alignItems": "center",
         "children": [text(label_, 15, tc, font=PARENT_FONT, weight="700")]}
    if kind == "secondary":
        n["stroke"] = {"align": "inside", "thickness": 1.5, "fill": "#3A2A2026"}
    return n


def checkbox(x, y, on=True, big=False):
    s = 26 if big else 22
    n = {"type": "frame", "id": nid("cb"), "name": "체크", "x": x, "y": y, "width": s, "height": s, "cornerRadius": 7,
         "fill": c("felt-coral") if on else c("white"), "layout": "horizontal", "justifyContent": "center",
         "alignItems": "center", "children": [icon("check", s - 8)] if on else []}
    if not on:
        n["stroke"] = {"align": "inside", "thickness": 1.5, "fill": "#3A2A2040"}
    return n


def parent_screen(left_title, left_sub, right, pic=MASCOT, pic_size=150):
    """보호자 화면: 왼쪽 38% 오또 · 제목, 오른쪽 흰 카드."""
    return [{"type": "rectangle", "id": nid("bg"), "name": "배경", "x": 0, "y": 0, "width": 800, "height": 360,
             "fill": c("wool")},
            image(pic, pic_size, pic_size, x=150 - pic_size // 2 + 10, y=34, name="오또"),
            wtext(left_title, 20, weight="700", x=32, y=196, w=250, lh=1.35),
            wtext(left_sub, 13, "ink-soft", x=32, y=262, w=250),
            {"type": "frame", "id": nid("card"), "name": "보호자 카드", "x": 304, "y": 20, "width": 476, "height": 320,
             "cornerRadius": 24, "fill": c("white"), "effect": SHADOW_PRESSED, "layout": "none", "children": right}]


def kakao_btn(x, y, w):
    return {"type": "frame", "id": nid("kk"), "name": "카카오 로그인 (공식: #FEE500 · 모서리 12 · 심볼 필수)", "x": x, "y": y,
            "width": w, "height": 48, "cornerRadius": 12, "fill": "#FEE500", "layout": "none", "children": [
                icon("message-circle", 20, x=16, y=14, id=nid("ks")),
                {"type": "frame", "id": nid("kl"), "name": "레이블", "x": 0, "y": 0, "width": w, "height": 48,
                 "layout": "horizontal", "justifyContent": "center", "alignItems": "center",
                 "children": [text("카카오 로그인", 15, font=PARENT_FONT, weight="600", fill="#000000D9")]}]}


def naver_btn(x, y, w):
    return {"type": "frame", "id": nid("nv"), "name": "네이버 로그인 (#03C75A)", "x": x, "y": y, "width": w, "height": 48,
            "cornerRadius": 12, "fill": "#03C75A", "layout": "none", "children": [
                text("N", 18, "white", font=PARENT_FONT, weight="900", x=19, y=11),
                {"type": "frame", "id": nid("nl"), "name": "레이블", "x": 0, "y": 0, "width": w, "height": 48,
                 "layout": "horizontal", "justifyContent": "center", "alignItems": "center",
                 "children": [text("네이버 로그인", 15, "white", font=PARENT_FONT, weight="600")]}]}


def google_btn(x, y, w):
    return {"type": "frame", "id": nid("gg"), "name": "Google 로그인 (흰 바탕 · #747775 테두리 · 공식 G 로고로 교체)",
            "x": x, "y": y, "width": w, "height": 48, "cornerRadius": 12, "fill": "#FFFFFF",
            "stroke": {"align": "inside", "thickness": 1, "fill": "#747775"}, "layout": "none", "children": [
                text("G", 18, font=PARENT_FONT, weight="700", fill="#4285F4", x=18, y=11),
                {"type": "frame", "id": nid("gl"), "name": "레이블", "x": 0, "y": 0, "width": w, "height": 48,
                 "layout": "horizontal", "justifyContent": "center", "alignItems": "center",
                 "children": [text("Google로 계속하기", 15, font=PARENT_FONT, weight="600", fill="#1F1F1F")]}]}


def keypad(x, y, key_w=76, key_h=50, gap=8):
    kids = []
    keys = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫"]
    for i, k in enumerate(keys):
        if not k:
            continue
        kx, ky = x + (i % 3) * (key_w + gap), y + (i // 3) * (key_h + gap)
        kids.append({"type": "frame", "id": nid("key"), "name": f"키 {k}", "x": kx, "y": ky, "width": key_w,
                     "height": key_h, "cornerRadius": 14, "fill": c("wool-cream"), "layout": "horizontal",
                     "justifyContent": "center", "alignItems": "center",
                     "children": [icon("delete", 22, "ink") if k == "⌫" else
                                  text(k, 22, font=PARENT_FONT, weight="600")]})
    return kids


def pin_dots(x, y, filled):
    return [{"type": "ellipse", "id": nid("dot"), "x": x + i * 30, "y": y, "width": 16, "height": 16,
             "fill": c("felt-coral") if i < filled else "#3A2A2026"} for i in range(4)]


def kid_close(x=12, y=12, ic="house"):
    return felt(f"◯ {ic}", 56, 56, "wool-cream", 28, x=x, y=y, children=[center([icon(ic, 26, "ink")], 56, 56)])


def round_kid(ic, x, y, color="wool-cream", ic_color="ink", s=64):
    return felt(f"◯ {ic}", s, s, color, s // 2, x=x, y=y, children=[center([icon(ic, s * 0.45, ic_color)], s, s)])


def say(x, y, w=260):
    """오또가 소리로 말하는 중 — 글 대신 소리 표시 (아이 화면)."""
    return felt("🔊 오또 목소리", 44, 44, "cheek", 22, stitch=False, x=x, y=y,
                children=[center([icon("volume-2", 22)], 44, 44)])


# ── 품질 보강: 보호자 화면 틀 · CLAP · 타이틀 ────────────────────────────
ONBOARD_STEPS = ["계정 만들기", "보호자 동의", "마이크 켜기", "부모 비밀번호"]


def wordmark(x, y, s=34):
    return [text("오", s, "felt-coral", x=x, y=y), text("또", s, "felt-mustard", x=x + s * 0.95, y=y)]


def parent_shell(right, step=None, ctx_title="", ctx_sub="", ctx_icon=None):
    """보호자 화면: 왼쪽 34% 브랜드 · 단계 표시, 오른쪽 흰 카드. 마스코트 없음."""
    left = [{"type": "rectangle", "id": nid("bg"), "name": "배경", "x": 0, "y": 0, "width": 800, "height": 360,
             "fill": c("wool")},
            felt("왼쪽 판", 272, 360, "wool-cream", 0, stitch=False, shadow=False, texture=0.55),
            *wordmark(28, 22)]
    if step is not None:
        left.append(text(f"보호자 설정  {step + 1} / {len(ONBOARD_STEPS)}", 12, "ink-soft", font=PARENT_FONT,
                         weight="600", x=30, y=74))
        for i, nm in enumerate(ONBOARD_STEPS):
            yy = 112 + i * 52
            done, cur = i < step, i == step
            if i < len(ONBOARD_STEPS) - 1:
                left.append({"type": "rectangle", "id": nid("sl"), "name": "잇는 선", "x": 43, "y": yy + 30,
                             "width": 2, "height": 22, "fill": c("felt-teal") if done else "#3A2A2026"})
            circ = {"type": "frame", "id": nid("sc"), "name": f"단계 {i + 1}", "x": 30, "y": yy, "width": 28,
                    "height": 28, "cornerRadius": 14, "layout": "horizontal", "justifyContent": "center",
                    "alignItems": "center",
                    "fill": c("felt-teal") if done else (c("felt-coral") if cur else c("white")),
                    "children": [icon("check", 16) if done else
                                 text(str(i + 1), 13, "white" if cur else "ink-soft", font=PARENT_FONT, weight="700")]}
            if not (done or cur):
                circ["stroke"] = {"align": "inside", "thickness": 1.5, "fill": "#3A2A2033"}
            left += [circ, text(nm, 15 if cur else 14, "ink" if (cur or done) else "ink-soft", font=PARENT_FONT,
                                weight="700" if cur else "500", x=70, y=yy + 4)]
        left.append(wtext("아이의 녹음 · 그림 · 책은 폰 밖으로 나가지 않아요", 11, "ink-soft", x=30, y=318, w=220))
    else:
        if ctx_icon:
            left.append(felt(ctx_icon, 64, 64, "felt-teal", 32, x=30, y=96,
                             children=[center([icon(ctx_icon, 30)], 64, 64)]))
        left += [wtext(ctx_title, 20, weight="700", x=30, y=180, w=220, lh=1.35),
                 wtext(ctx_sub, 13, "ink-soft", x=30, y=238, w=220)]
    card = {"type": "frame", "id": nid("card"), "name": "보호자 카드", "x": 296, "y": 20, "width": 484, "height": 320,
            "cornerRadius": 24, "fill": c("white"), "effect": SHADOW_PRESSED, "layout": "none", "children": right}
    return left + [card]


def clap_splash():
    letters = [("C", "felt-coral", 0), ("L", "felt-mustard", -18), ("A", "felt-teal", 6), ("P", "felt-sky", -10)]
    kids = [{"type": "rectangle", "id": nid("bg"), "name": "배경", "x": 0, "y": 0, "width": 800, "height": 360,
             "fill": c("wool")},
            image(TEXTURE, 800, 360, opacity=0.5, name="양모 결"),
            {"type": "ellipse", "id": nid("sh"), "name": "바닥 그림자", "x": 200, "y": 250, "width": 400, "height": 30,
             "fill": "#3A2A2014"}]
    for i, (ch, col_, dy) in enumerate(letters):
        kids.append(felt(f"글자 {ch} (통통 튀어 오름 · {150 + i * 110}ms)", 104, 116, col_, 30, x=184 + i * 110,
                         y=112 + dy, children=[center([text(ch, 76, "white")], 104, 116)]))
    return kids


def title_splash():
    kids = [felt("무대 뒤 벽", 800, 360, "curtain-deep", 0, stitch=False, shadow=False, texture=0.6),
            {"type": "ellipse", "id": nid("spot"), "name": "조명", "x": 170, "y": -40, "width": 460, "height": 420,
             "fill": "#FFE9B84D"},
            felt("무대 바닥", 800, 64, "stage-wood", 0, stitch=False, shadow=False, x=0, y=296),
            {"type": "rectangle", "id": nid("edge"), "name": "무대 앞 가장자리", "x": 0, "y": 296, "width": 800,
             "height": 8, "fill": c("stage-wood-deep")}]
    for side in (0, 1):
        x0 = 0 if side == 0 else 630
        kids.append(felt("커튼 (열리는 중)", 170, 330, "curtain", 0, stitch=False, x=x0, y=0, children=[
            *[{"type": "rectangle", "id": nid("fold"), "name": "주름", "x": 14 + k * 38, "y": 0, "width": 12,
               "height": 330, "cornerRadius": 6, "fill": "#7A1E1E59"} for k in range(4)],
            felt("묶는 끈", 170, 18, "felt-mustard", 9, stitch=False, x=0, y=196)]))
    kids.append({"type": "frame", "id": nid("val"), "name": "커튼 위 주름 장식", "x": 0, "y": 0, "width": 800, "height": 46,
                 "layout": "none", "children": [
                     felt("띠", 800, 30, "curtain", 0, stitch=False, shadow=False),
                     *[{"type": "ellipse", "id": nid("sc"), "name": "물결", "x": -20 + k * 60, "y": 10, "width": 64,
                        "height": 34, "fill": c("curtain")} for k in range(15)]]})
    kids += [image(MASCOT, 200, 200, x=178, y=110, name="오또 (손 흔들며 \"안녕! 나는 오또야\")"),
             felt("오", 112, 112, "felt-coral", 32, x=408, y=70, children=[center([text("오", 80, "white")], 112, 112)]),
             felt("또", 112, 112, "felt-mustard", 32, x=528, y=82, children=[center([text("또", 80, "white")], 112, 112)]),
             text("말로 만드는 그림책", 22, "wool", x=440, y=214),
             felt("누르면 시작 (맥동)", 150, 40, "wool", 20, stitch=False, x=444, y=250,
                  children=[center([icon("pointer", 16, "felt-coral"), text("눌러서 시작", 15)], 150, 40,
                                   direction="horizontal", gap=6)])]
    return kids


# ── 모드마다 다른 배경 ────────────────────────────────────────────
MODES = {
    "story": ("이야기 만들기", "인형극 무대"),
    "diary": ("오늘 이야기", "아침 창가"),
    "coop": ("같이 만들기", "저녁 거실"),
}


def backdrop(mode):
    if mode == "story":   # 인형극 무대 — 방의 인형극 무대 안으로 들어온 느낌
        k = [felt("무대 뒤 벽", 800, 360, "curtain-deep", 0, stitch=False, shadow=False, texture=0.6),
             {"type": "ellipse", "id": nid("spot"), "name": "조명", "x": 60, "y": -60, "width": 420, "height": 460,
              "fill": "#FFE9B840"},
             felt("무대 바닥", 800, 50, "stage-wood", 0, stitch=False, shadow=False, x=0, y=310)]
        for x0 in (0, 752):
            k.append(felt("옆 커튼", 48, 330, "curtain", 0, stitch=False, x=x0, y=0, children=[
                {"type": "rectangle", "id": nid("fold"), "name": "주름", "x": 16, "y": 0, "width": 10, "height": 330,
                 "cornerRadius": 5, "fill": "#7A1E1E59"}]))
        k.append({"type": "frame", "id": nid("val"), "name": "커튼 위 물결", "x": 0, "y": 0, "width": 800, "height": 30,
                  "layout": "none", "clip": False, "children": [
                      *[{"type": "ellipse", "id": nid("sc"), "name": "물결", "x": -20 + i * 60, "y": -14, "width": 64,
                         "height": 34, "fill": c("curtain")} for i in range(15)]]})
        return k
    if mode == "diary":   # 아침 창가 — 오늘 있었던 일
        k = [{"type": "rectangle", "id": nid("wall"), "name": "하늘색 벽", "x": 0, "y": 0, "width": 800, "height": 360,
              "fill": "#DDEEF1"},
             image(TEXTURE, 800, 360, opacity=0.45, name="양모 결"),
             {"type": "ellipse", "id": nid("sun"), "name": "햇살", "x": 560, "y": -120, "width": 360, "height": 300,
              "fill": "#FFF1B866"},
             felt("밝은 나무 바닥", 800, 56, "#E8C79A", 0, stitch=False, shadow=False, x=0, y=304),
             {"type": "rectangle", "id": nid("line"), "name": "깃발 줄", "x": 0, "y": 76, "width": 800, "height": 2,
              "fill": c("stage-wood-deep")}]
        cols = ["felt-coral", "felt-mustard", "felt-teal", "felt-sky", "cheek"]
        for i in range(17):
            k.append({"type": "path", "id": nid("flag"), "name": "펠트 깃발", "x": 8 + i * 48, "y": 77, "width": 26,
                      "height": 26, "geometry": "M0 0 L26 0 L13 26 Z", "fill": c(cols[i % 5])})
        return k
    # coop — 저녁 거실, 둘이 나란히
    k = [{"type": "rectangle", "id": nid("wall"), "name": "복숭아색 벽", "x": 0, "y": 0, "width": 800, "height": 360,
          "fill": "#F6DCC8"},
         image(TEXTURE, 800, 360, opacity=0.45, name="양모 결"),
         {"type": "ellipse", "id": nid("lamp"), "name": "스탠드 불빛", "x": -120, "y": -80, "width": 420, "height": 380,
          "fill": "#FFE3A866"},
         felt("소파 등받이", 800, 90, "felt-sky", 30, stitch=False, x=-20, y=196),
         felt("소파 앉는 곳", 820, 60, "#4B7CC4", 20, stitch=False, x=-10, y=250)]
    return k


def parent_band(q):
    return {"type": "frame", "id": nid("band"), "name": "부모 띠 (부모가 읽고 아이에게 물어봄)", "x": 0, "y": 292,
            "width": 800, "height": 68, "cornerRadius": 22, "fill": "#3A2A20E6", "layout": "none", "children": [
                felt("부모님 표시", 96, 30, "felt-sky", 15, stitch=False, shadow=False, x=20, y=19,
                     children=[center([icon("users", 14), text("부모님", 13, "white", font=PARENT_FONT, weight="700")],
                                      96, 30, direction="horizontal", gap=6)]),
                text(q, 18, "white", font=PARENT_FONT, weight="600", x=132, y=22)]}


def screens(C, P):
    S = {}

    S["⓪ CLAP"] = clap_splash()
    S["① 스플래시"] = title_splash()

    # ② 보호자 환영
    right = [wtext("보호자가 먼저 설정해 주세요", 22, weight="700", x=32, y=32, w=412),
             wtext("로그인 · 동의 · 마이크 설정을 마치면 아이에게 넘겨 주세요. 3분 정도 걸려요.", 14, "ink-soft",
                   x=32, y=74, w=412),
             *[wtext(f"{i + 1}. {t_}", 14, x=32, y=130 + i * 28, w=412)
               for i, t_ in enumerate(["보호자 계정 만들기", "개인정보 동의", "마이크 켜기 · 부모 비밀번호"])],
             pbtn("보호자예요 — 2초 길게 눌러 시작", 32, 240, 412, 52)]
    S["② 보호자 환영"] = [
        {"type": "rectangle", "id": nid("bg"), "name": "배경", "x": 0, "y": 0, "width": 800, "height": 360,
         "fill": c("wool")},
        image(MASCOT, 200, 200, x=48, y=90, name="오또 (폰을 어른에게 내미는 몸짓)"),
        felt("말풍선 (소리)", 220, 56, "wool-cream", 24, stitch=False, x=40, y=26,
             children=[center([icon("volume-2", 18, "felt-coral"), text("엄마 아빠 불러 줄래?", 17)], 220, 56,
                              direction="horizontal", gap=8)]),
        {"type": "frame", "id": nid("card"), "name": "보호자 카드", "x": 304, "y": 20, "width": 476, "height": 320,
         "cornerRadius": 24, "fill": c("white"), "effect": SHADOW_PRESSED, "layout": "none", "children": right}]

    # ③ 로그인 / 회원가입
    bw = 380
    right = [wtext("시작하기", 22, weight="700", x=48, y=26, w=380),
             wtext("보호자 계정 하나로 시작해요. 아이 이름 · 나이는 받지 않아요.", 13, "ink-soft", x=48, y=60, w=380),
             kakao_btn(48, 96, bw), naver_btn(48, 152, bw), google_btn(48, 208, bw),
             text("이메일로 시작하기", 14, "ink-soft", font=PARENT_FONT, weight="600", x=182, y=270),
             wtext("계속하면 이용약관과 개인정보처리방침에 동의하게 됩니다.", 11, "ink-soft", x=48, y=296, w=380)]
    S["③ 로그인 / 회원가입"] = parent_shell(right, step=0)

    # ③-1 이메일
    right = [wtext("이메일로 시작하기", 20, weight="700", x=40, y=28, w=400),
             *[item for i, (lb, ph) in enumerate([("이메일", "parent@example.com"), ("비밀번호", "8자 이상 · 영문 + 숫자")])
               for item in (text(lb, 13, "ink-soft", font=PARENT_FONT, weight="600", x=40, y=80 + i * 84),
                            {"type": "frame", "id": nid("in"), "name": f"입력 · {lb}", "x": 40, "y": 102 + i * 84,
                             "width": 396, "height": 48, "cornerRadius": 12, "fill": c("wool"),
                             "stroke": {"align": "inside", "thickness": 1.5, "fill": "#3A2A2026"}, "layout": "none",
                             "children": [text(ph, 15, "ink-soft", font=PARENT_FONT, x=16, y=14)]})],
             pbtn("다음", 40, 256, 396, 48)]
    S["③ 이메일로 시작"] = parent_shell(right, step=0)

    # ④ 보호자 동의
    rows = [("[필수] 이용약관", True), ("[필수] 보호자 개인정보 수집 · 이용", True),
            ("[필수] 만 14세 미만 아동의 법정대리인 동의", True), ("[선택] 새 기능 알림 받기", False)]
    right = [checkbox(32, 30, True, big=True), text("전체 동의", 18, font=PARENT_FONT, weight="700", x=70, y=30),
             {"type": "rectangle", "id": nid("hr"), "name": "구분선", "x": 32, "y": 74, "width": 412, "height": 1.5,
              "fill": "#3A2A201A"}]
    for i, (t_, on) in enumerate(rows):
        yy = 92 + i * 40
        right += [checkbox(32, yy, on), text(t_, 14, font=PARENT_FONT, x=66, y=yy + 1),
                  text("보기", 13, "ink-soft", font=PARENT_FONT, x=400, y=yy + 2)]
    right += [wtext("아이의 녹음 · 그림 · 책은 폰 밖으로 나가지 않아요.", 12, "felt-teal", weight="700", x=32, y=252, w=412),
              pbtn("동의하고 계속", 32, 272, 412, 44)]
    S["④ 보호자 동의"] = parent_shell(right, step=1)

    # ⑤ 마이크 사전 안내
    pts = [("mic", "오또가 아이 목소리를 듣고 그림책을 지어요"), ("shield", "녹음은 폰 밖으로 나가지 않아요"),
           ("hand", "마이크를 안 켜도 그림을 골라서 만들 수 있어요")]
    right = [wtext("마이크를 켜 주세요", 22, weight="700", x=32, y=30, w=412)]
    for i, (ic, t_) in enumerate(pts):
        right += [felt(ic, 36, 36, "felt-teal", 18, stitch=False, shadow=False, x=32, y=84 + i * 50,
                       children=[center([icon(ic, 18)], 36, 36)]),
                  wtext(t_, 14, x=80, y=92 + i * 50, w=360)]
    right += [pbtn("나중에", 32, 256, 150, 48, "secondary"), pbtn("마이크 허용하기", 196, 256, 248, 48)]
    S["⑤ 마이크 켜기"] = parent_shell(right, step=2)

    # ⑥ 부모 비밀번호
    right = [wtext("부모 비밀번호 4자리", 18, weight="700", x=28, y=30, w=176),
             wtext("부모 영역과 하루 한도 연장에 써요", 13, "ink-soft", x=28, y=86, w=176),
             *pin_dots(34, 150, 2),
             wtext("한 번 더 입력해서 확인해요", 12, "ink-soft", x=28, y=186, w=176),
             *keypad(212, 34)]
    S["⑥ 부모 비밀번호"] = parent_shell(right, step=3)

    # ⑦ 오또 인사 · 방 둘러보기
    stripes, window, theater, sofa, shelf = room_objects()
    dim = {"type": "rectangle", "id": nid("dim"), "name": "나머지 흐리게", "x": 0, "y": 0, "width": 800, "height": 360,
           "fill": "#3A2A2066"}
    spot = {"type": "ellipse", "id": nid("spot"), "name": "조명 (이것만 밝게)", "x": 372, "y": 54, "width": 236,
            "height": 236, "fill": "#FFF7EC40"}
    S["⑦ 오또 인사 · 방 둘러보기"] = [
        *stripes, felt("바닥", 800, 100, "stage-wood", 0, stitch=False, shadow=False, x=0, y=262),
        window, shelf, sofa, dim, spot, theater,
        image(MASCOT, 214, 214, x=150, y=106, name="오또 (무대를 가리킴 · \"여기서 이야기를 만들어!\")"),
        say(330, 110),
        felt("👆 누르는 손 (맥동)", 64, 64, "white", 32, stitch=False, x=540, y=210,
             children=[center([icon("pointer", 34, "ink")], 64, 64)]),
    ]

    # ⑨ 오또의 방 — 기존 화면 1을 그대로 쓴다 (build에서 옮김)

    # 주인공 고르기 · 만들기 (캐릭터 커스텀)
    heroes = ["hero_short_red_round", "hero_long_yellow_none", "hero_tied_blue_square"]
    hk = []
    for i, h in enumerate(heroes):
        sel = i == 0
        card = felt("주인공 카드" + (" (고름)" if sel else ""), 136, 176, "wool-cream", 28, x=107 + i * 150, y=84,
                    stroke={"align": "inside", "thickness": 5, "fill": c("felt-coral")} if sel else None,
                    children=[image(A + h + ".png", 128, 128, x=4, y=12, name=h)])
        hk.append(card)
    hk.append(felt("＋ 새 주인공 만들기", 136, 176, "wool", 28, stitch=True, x=557, y=84,
                   stroke={"align": "inside", "thickness": 2, "fill": "#3A2A2040"},
                   children=[center([icon("plus", 44, "ink-soft")], 136, 176)]))
    S["이야기 만들기 · 주인공 고르기"] = [
        *wall(), say(24, 290),
        kid_close(), ref(C["gate"], 76, 20), ref(C["prog1"], 236, 6),
        *hk,
        text("＋ 를 누르면 머리 · 옷 · 눈 · 안경 · 아래옷을 고르는 꾸미기 화면으로", 12, "ink-soft", font=PARENT_FONT,
             x=107, y=282)]

    # ⑩ 책 만드는 중
    S["⑩ 책 만드는 중"] = [
        *wall(),
        felt("그려지는 책", 300, 220, "white", 24, stitch=False, x=420, y=50, children=[
            image(A + "bg_dino.png", 276, 196, x=12, y=12, radius=16, opacity=0.35, name="흐릿하게 나타나는 그림"),
            icon("paintbrush", 48, "felt-coral", x=126, y=86)]),
        image(MASCOT, 250, 250, x=70, y=60, name="오또 (붓으로 그리는 몸짓 반복)"),
        felt("말풍선 (소리)", 300, 56, "wool-cream", 24, stitch=False, x=60, y=20,
             children=[center([icon("volume-2", 18, "felt-coral"), text("책 이름은 뭐라고 할까?", 18)], 300, 56,
                              direction="horizontal", gap=8)]),
        *[felt(f"붓 칸 {i + 1}", 36, 20, "felt-mustard" if i < 3 else "wool-cream", 10, stitch=False, shadow=i < 3,
               x=466 + i * 44, y=300) for i in range(5)],
        text("기다리는 동안 제목을 말로 짓는다 (숫자 % 없음)", 11, "ink-soft", font=PARENT_FONT, x=420, y=276)]

    # ⑪ 책 읽기
    band = felt("자막 띠", 560, 64, "wool", 22, stitch=False, x=120, y=284, children=[
        text("공룡이 수영장에서", 22, x=28, y=18),
        {"type": "rectangle", "id": nid("hl"), "name": "지금 읽는 낱말", "x": 222, "y": 14, "width": 118, "height": 36,
         "cornerRadius": 10, "fill": "#F2B23266"},
        text("첨벙첨벙", 22, x=232, y=18), text("뛰었어요.", 22, x=350, y=18),
        text("3 / 8", 13, "ink-soft", font=PARENT_FONT, x=500, y=24)])
    tools = [("hand", True), ("hammer", False), ("feather", False), ("search", False)]
    S["⑪ 책 읽기"] = [
        image(A + "bg_pool.png", 800, 360, name="그림 (화면의 80%)"),
        image(A + "dino_trex.png", 190, 190, x=330, y=96, name="주인공 공룡 (만지면 반응)"),
        kid_close(ic="x"),
        *[felt(f"도구 · {ic}", 52, 52, "felt-mustard" if on else "wool-cream", 26, x=260 + i * 64, y=10,
               children=[center([icon(ic, 24, "white" if on else "ink")], 52, 52)]) for i, (ic, on) in enumerate(tools)],
        round_kid("volume-2", 724, 12, s=56),
        round_kid("chevron-left", 24, 150), round_kid("chevron-right", 712, 150, color="felt-coral", ic_color="white"),
        band]

    # ⑫ 친구 평가
    S["⑫ 친구 평가"] = [
        *wall(floor=290),
        image(A + "bud_alien.png", 240, 240, x=280, y=56, name="오늘의 친구 (말함)"),
        say(470, 60),
        felt("💗 또 만날래", 132, 132, "cheek", 36, x=578, y=120,
             children=[center([icon("heart", 52), text("또 만날래", 18, "white")], 132, 132)]),
        felt("👋 안녕", 132, 132, "wool-cream", 36, x=90, y=120,
             children=[center([icon("hand", 48, "ink"), text("안녕", 18)], 132, 132)]),
        ref(C["prog3"], 236, 6)]

    # ⑬ 선물
    S["⑬ 선물"] = [
        *wall(floor=290),
        {"type": "ellipse", "id": nid("glow"), "name": "빛", "x": 250, "y": 40, "width": 300, "height": 260,
         "fill": "#F2B23240"},
        image(A + "gift_book.png", 150, 150, x=250, y=90, name="선물 1 (누르면 열림)"),
        image(A + "gift_crayon.png", 150, 150, x=400, y=90, name="선물 2"),
        icon("sparkles", 36, "felt-mustard", x=520, y=70), icon("sparkles", 24, "felt-mustard", x=260, y=80),
        say(24, 290),
        felt("받은 선물 → 오또의 방으로 날아감", 210, 44, "felt-teal", 22, stitch=False, x=560, y=300,
             children=[center([icon("house", 18), text("방에 놓을게!", 16, "white")], 210, 44, direction="horizontal",
                              gap=8)])]

    # ⑭ 책장 — 지금 앱 ShelfView 그대로 (bg_shelf · 선반 두 칸 · 표지가 보이게 서는 책)
    books = [("공룡 숲의 첨벙첨벙", "bg_dino", True), ("수영장 대모험", "bg_pool", False),
             ("놀이터의 비밀", "bg_playground", False), ("공원에서 만난 친구", "bg_park", False),
             ("할머니 집 이야기", "bg_home", False)]
    shelf_k = [image(A + "bg_shelf.png", 800, 360, name="책장 그림 (bg_shelf · Crop)")]
    shelf_y = [0.645, 0.985]
    for i, (title, cv, new) in enumerate(books):
        row, col = (0, i) if i < 4 else (1, i - 4)
        bx = round(800 * 0.262 + 94 * col)
        by = round(360 * shelf_y[row] - 98)
        shelf_k.append({"type": "frame", "id": nid("bk"), "name": f"책 · {title}" + (" (방금 만든 책 · 위에서 내려와 꽂힘)" if new else ""),
                        "x": bx, "y": by, "width": 72, "height": 98, "cornerRadius": 6, "clip": True,
                        "fill": "#FFFBF2", "effect": SHADOW_SOFT, "layout": "none", "children": [
                            image(A + cv + ".png", 72, 70, name="표지 그림"),
                            {"type": "rectangle", "id": nid("sp"), "name": "책등 그림자", "x": 0, "y": 0, "width": 7,
                             "height": 70, "fill": "#00000040"},
                            text(title, 9, font=KID_FONT, x=3, y=72, width=66, textGrowth="fixed-width",
                                 textAlign="center", lineHeight=1.2)]})
        if new:
            shelf_k += [
                {"type": "frame", "id": nid("nb"), "name": "새 책! (숨 쉬듯 커졌다 작아짐)", "x": bx + 8, "y": by - 14,
                 "width": 56, "height": 22, "cornerRadius": 11, "fill": c("felt-coral"), "layout": "horizontal",
                 "justifyContent": "center", "alignItems": "center",
                 "children": [text("새 책!", 12, "white", font=PARENT_FONT, weight="700")]},
                {"type": "frame", "id": nid("rn"), "name": "✏️ 이름 바꾸기 (자리만)", "x": bx + 58, "y": by + 16,
                 "width": 28, "height": 28, "cornerRadius": 14, "fill": c("white"), "effect": SHADOW_PRESSED,
                 "stroke": {"align": "inside", "thickness": 2, "fill": c("felt-mustard")}, "layout": "horizontal",
                 "justifyContent": "center", "alignItems": "center", "children": [icon("pencil", 14, "ink")]},
                icon("sparkles", 22, "felt-mustard", x=bx - 14, y=by + 76)]
    shelf_k += [
        {"type": "frame", "id": nid("cnt"), "name": "📚 N권", "x": 16, "y": 12, "height": 30, "width": 84,
         "cornerRadius": 15, "fill": "#FFFFFFD9", "layout": "horizontal", "justifyContent": "center",
         "alignItems": "center", "gap": 6, "children": [icon("library", 16, "ink"),
                                                         text(f"{len(books)}권", 15, font=PARENT_FONT, weight="700")]},
        {"type": "frame", "id": nid("sb"), "name": "👪 부모 모드 (→ 태어난 해)", "x": 486, "y": 306, "width": 150,
         "height": 42, "cornerRadius": 21, "fill": "#4A423A", "effect": SHADOW_SOFT, "layout": "horizontal",
         "justifyContent": "center", "alignItems": "center", "gap": 6,
         "children": [icon("users", 18, "white"), text("부모 모드", 16, "white", font=PARENT_FONT, weight="700")]},
        {"type": "frame", "id": nid("sb"), "name": "🏠 처음으로 (오또의 방)", "x": 646, "y": 306, "width": 138,
         "height": 42, "cornerRadius": 21, "fill": c("felt-mustard"), "effect": SHADOW_SOFT, "layout": "horizontal",
         "justifyContent": "center", "alignItems": "center", "gap": 6,
         "children": [icon("house", 18, "ink"), text("처음으로", 16, font=PARENT_FONT, weight="700")]}]
    S["⑭ 책장"] = shelf_k

    # ⑮ 하루 한도
    S["⑮ 하루 한도에 닿으면"] = [
        felt("밤 배경", 800, 360, "#3B4A6B", 0, stitch=False, shadow=False),
        *[icon("star", 14 + (i % 3) * 6, "felt-mustard", x=60 + i * 97 % 700, y=20 + (i * 53) % 120) for i in range(9)],
        {"type": "ellipse", "id": nid("moon"), "name": "달", "x": 620, "y": 30, "width": 90, "height": 90,
         "fill": "#F7E08A"},
        felt("바닥", 800, 70, "stage-wood-deep", 0, stitch=False, shadow=False, x=0, y=290),
        image(MASCOT, 240, 240, x=280, y=70, name="오또 (하품 · 손 흔들기)"),
        felt("말풍선 (소리)", 280, 56, "wool", 24, stitch=False, x=40, y=120,
             children=[center([icon("volume-2", 18, "felt-coral"), text("오늘은 여기까지! 내일 또 만나", 17)],
                              280, 56, direction="horizontal", gap=8)]),
        {"type": "frame", "id": nid("adult"), "name": "어른 버튼 (구석 · 작게 → 태어난 해 → 시간 더 주기)", "x": 676, "y": 306,
         "width": 108, "height": 40, "cornerRadius": 20, "fill": "#FFFFFF26", "layout": "horizontal",
         "justifyContent": "center", "alignItems": "center", "gap": 6,
         "children": [icon("lock", 16, "white"), text("어른", 13, "white", font=PARENT_FONT, weight="600")]}]

    # 부모 문 → 비밀번호 입력
    S["🔒 부모 문 → 비밀번호"] = parent_shell(ctx_title="부모 영역", ctx_sub="아이 화면 왼쪽 위 자물쇠를 2초 길게 누르면 여기로 와요.", ctx_icon="lock", right=[
        wtext("비밀번호를 입력하세요", 18, weight="700", x=28, y=30, w=176), *pin_dots(34, 110, 1),
        wtext("5번 틀리면 1분 기다려요 · 잊었다면 계정으로 다시 로그인", 12, "ink-soft", x=28, y=150, w=176),
        text("닫기", 14, "ink-soft", font=PARENT_FONT, weight="600", x=28, y=270), *keypad(212, 34)])

    # 부모 영역 대시보드
    menu = [("book-open", "기록", True), ("message-square", "같이 만들기 질문", False), ("settings", "설정", False),
            ("user", "계정", False), ("flag", "신고 · 동의 철회", False)]
    side = [{"type": "rectangle", "id": nid("side"), "name": "왼쪽 메뉴 (22%)", "x": 0, "y": 0, "width": 176, "height": 360,
             "fill": c("wool-cream")},
            text("부모 영역", 18, font=PARENT_FONT, weight="700", x=20, y=22)]
    for i, (ic, t_, on) in enumerate(menu):
        side.append({"type": "frame", "id": nid("mi"), "name": f"메뉴 · {t_}", "x": 10, "y": 60 + i * 46, "width": 156,
                     "height": 40, "cornerRadius": 12, "fill": c("white") if on else "#00000000", "layout": "horizontal",
                     "alignItems": "center", "gap": 10, "padding": [0, 12],
                     "children": [icon(ic, 18, "felt-coral" if on else "ink-soft"),
                                  text(t_, 13, "ink" if on else "ink-soft", font=PARENT_FONT, weight="700" if on else "500")]})
    side.append(pbtn("아이 화면으로", 10, 306, 156, 40, "secondary"))

    def stat(x, y, big, small):
        return {"type": "frame", "id": nid("stat"), "name": f"요약 · {small}", "x": x, "y": y, "width": 184, "height": 84,
                "cornerRadius": 18, "fill": c("white"), "effect": SHADOW_PRESSED, "layout": "vertical", "gap": 4,
                "padding": [14, 16], "children": [text(big, 24, font=PARENT_FONT, weight="700"),
                                                   text(small, 12, "ink-soft", font=PARENT_FONT)]}
    quote = {"type": "frame", "id": nid("q"), "name": "오늘 아이가 한 말", "x": 196, "y": 118, "width": 584, "height": 118,
             "cornerRadius": 18, "fill": c("white"), "effect": SHADOW_PRESSED, "layout": "vertical", "gap": 8,
             "padding": [16, 18], "children": [
                 text("오늘 아이가 한 말 그대로", 12, "ink-soft", font=PARENT_FONT, weight="600"),
                 text("\"공룡이 수영장에서 첨벙첨벙 했어! 친구가 무서워서 내가 안아 줬어.\"", 16, font=PARENT_FONT,
                      weight="700"),
                 text("9월 28일 · 할머니와 함께 · 공룡 숲 이야기 8쪽", 12, "ink-soft", font=PARENT_FONT)]}
    S["부모 영역 · 기록"] = [
        {"type": "rectangle", "id": nid("bg"), "name": "배경", "x": 0, "y": 0, "width": 800, "height": 360,
         "fill": c("wool")}, *side,
        text("이번 주", 16, font=PARENT_FONT, weight="700", x=196, y=18),
        stat(196, 22 + 20, "3권", "만든 책"), stat(396, 42, "41분", "함께한 시간"), stat(596, 42, "+12", "지난주보다 늘어난 말"),
        quote,
        {"type": "frame", "id": nid("more"), "name": "6축 카드 목록", "x": 196, "y": 248, "width": 584, "height": 96,
         "cornerRadius": 18, "fill": c("white"), "effect": SHADOW_PRESSED, "layout": "horizontal", "gap": 10,
         "padding": [14, 16], "alignItems": "center",
         "children": [felt(n_, 82, 64, col_, 14, stitch=False, shadow=False) for n_, col_ in
                      [("말하기", "felt-coral"), ("상상", "felt-mustard"), ("마음", "cheek"), ("관계", "felt-sky"),
                       ("순서", "felt-teal"), ("낱말", "kitten")]]}]

    # 계정
    acct_rows = [("로그인", "카카오 · parent@kakao.com"), ("알림", "새 기능 알림 끔"), ("개인정보처리방침", "보기"),
                 ("이용약관", "보기")]
    acct = [{"type": "rectangle", "id": nid("bg"), "name": "배경", "x": 0, "y": 0, "width": 800, "height": 360,
             "fill": c("wool")}, *[dict(n, id=nid("cp")) if isinstance(n, dict) else n for n in []]]
    side2 = json.loads(json.dumps(side))
    _reid(side2)
    for n in side2:
        if n.get("name", "").startswith("메뉴 · "):
            on = n["name"].endswith("계정")
            n["fill"] = c("white") if on else "#00000000"
            n["children"][0]["fill"] = c("felt-coral") if on else c("ink-soft")
            n["children"][1]["fill"] = c("ink") if on else c("ink-soft")
    acct += side2
    acct.append(text("계정", 18, font=PARENT_FONT, weight="700", x=196, y=18))
    for i, (t_, v_) in enumerate(acct_rows):
        acct.append({"type": "frame", "id": nid("row"), "name": f"줄 · {t_}", "x": 196, "y": 54 + i * 52, "width": 584,
                     "height": 44, "cornerRadius": 14, "fill": c("white"), "layout": "horizontal",
                     "justifyContent": "space_between", "alignItems": "center", "padding": [0, 16],
                     "children": [text(t_, 14, font=PARENT_FONT, weight="600"),
                                  text(v_ + "  ›", 13, "ink-soft", font=PARENT_FONT)]})
    acct += [pbtn("로그아웃", 196, 268, 180, 40, "secondary"),
             text("로그아웃해도 폰 안의 책은 남아요", 12, "ink-soft", font=PARENT_FONT, x=388, y=280),
             text("회원 탈퇴 · 데이터 삭제", 13, "ink-soft", font=PARENT_FONT, weight="600", x=196, y=324)]
    S["부모 영역 · 계정"] = acct

    # 탈퇴 안내
    right = [wtext("탈퇴하면 이렇게 돼요", 20, weight="700", x=32, y=26, w=412),
             *[item for i, (ic, t_) in enumerate([("user-x", "보호자 계정과 기록이 바로 삭제돼요"),
                                                  ("book", "이 폰에 있는 책 5권 · 그림 · 녹음")])
               for item in (felt(ic, 34, 34, "felt-coral", 17, stitch=False, shadow=False, x=32, y=74 + i * 46,
                                 children=[center([icon(ic, 18)], 34, 34)]),
                            wtext(t_, 14, x=78, y=81 + i * 46, w=360))],
             checkbox(78, 170, False), text("폰 안의 책 · 그림 · 녹음도 함께 지우기", 13, font=PARENT_FONT, x=110, y=171),
             checkbox(32, 214, True), wtext("안내를 모두 확인했어요", 14, weight="600", x=66, y=215, w=300),
             pbtn("취소", 32, 262, 150, 48, "secondary"), pbtn("다음 — 본인 확인", 196, 262, 248, 48)]
    S["탈퇴 ① 안내"] = parent_shell(right, ctx_title="회원 탈퇴 · 데이터 삭제", ctx_sub="사유는 묻지 않아요. 앱을 지운 뒤에는 웹 페이지에서도 삭제를 요청할 수 있어요.", ctx_icon="user-x")

    # 탈퇴 확인
    right = [wtext("정말 삭제할까요?", 22, weight="700", x=32, y=30, w=412),
             wtext("카카오로 한 번 더 로그인해서 본인인지 확인했어요.", 14, "ink-soft", x=32, y=74, w=412),
             {"type": "frame", "id": nid("warn"), "name": "되돌릴 수 없음", "x": 32, "y": 116, "width": 412, "height": 70,
              "cornerRadius": 16, "fill": "#FBE3DF", "stroke": {"align": "inside", "thickness": 1.5,
                                                                  "fill": c("felt-coral")},
              "layout": "horizontal", "alignItems": "center", "gap": 12, "padding": [0, 16],
              "children": [icon("triangle-alert", 24, "felt-coral"),
                           wtext("삭제하면 되돌릴 수 없어요. 계정 · 기록 · 이 폰의 책 5권이 지워져요.", 13, w=330)]},
             pbtn("취소", 32, 250, 150, 52, "secondary"), pbtn("삭제하기", 196, 250, 248, 52)]
    S["탈퇴 ② 본인 확인 · 최종 확인"] = parent_shell(right, ctx_title="마지막 확인", ctx_sub="끝나면 처음 설치한 상태(⓪ CLAP)로 돌아가요.", ctx_icon="triangle-alert")

    # 예외 · 오프라인
    S["예외 · 오프라인"] = [
        *wall(floor=290), image(MASCOT, 220, 220, x=90, y=80, name="오또 (고개를 갸웃하는 몸짓)"),
        felt("말풍선 (소리)", 300, 56, "wool-cream", 24, stitch=False, x=40, y=20,
             children=[center([icon("volume-2", 18, "felt-coral"), text("새 책은 조금 뒤에 만들자!", 17)], 300, 56,
                              direction="horizontal", gap=8)]),
        felt("📚 만든 책 보기 (아이용 하나)", 220, 200, "felt-coral", 36, x=420, y=60,
             children=[center([icon("library", 72), text("책장 가기", 22, "white")], 220, 200, gap=12)]),
        {"type": "frame", "id": nid("adult"), "name": "어른용 (작게)", "x": 660, "y": 306, "width": 124, "height": 40,
         "cornerRadius": 20, "fill": "#3A2A201A", "layout": "horizontal", "justifyContent": "center",
         "alignItems": "center", "gap": 6, "children": [icon("wifi-off", 16, "ink-soft"),
                                                         text("다시 시도", 13, "ink-soft", font=PARENT_FONT, weight="600")]}]

    # 예외 · 마이크 꺼짐
    cb = C["cb"]
    S["예외 · 마이크가 꺼져 있을 때"] = [
        *backdrop("story"), felt("짓고 있는 책 한 쪽", 424, 256, "white", 28, stitch=False, x=356, y=80,
                      children=[image(BG_SAMPLE, 400, 232, x=12, y=12, radius=20, opacity=0.4)]),
        image(MASCOT, 280, 280, x=24, y=40, name="오또 (말 대신 고르자는 몸짓)"),
        ref(C["badge_think"], 250, 70), kid_close(), ref(C["gate"], 76, 20), ref(C["prog2"], 236, 6),
        ref(cb, 364, 124, {cb["_label"]: {"content": "공룡 숲"}}),
        ref(cb, 504, 124, {cb["_label"]: {"content": "바닷가"}}),
        ref(cb, 644, 124, {cb["_label"]: {"content": "눈 나라"}}),
        text("마이크 없이도 고르기 방울로 끝까지 — 부모 영역에만 \"마이크를 켜 주세요\" 알림", 11, "ink-soft",
             font=PARENT_FONT, x=356, y=340)]

    # 예외 · 로그인 만료
    S["예외 · 로그인이 풀렸을 때"] = [
        {"type": "rectangle", "id": nid("bg"), "name": "배경", "x": 0, "y": 0, "width": 800, "height": 360,
         "fill": c("wool")},
        image(MASCOT, 240, 240, x=80, y=70, name="오또 (어른을 부르는 몸짓)"),
        felt("말풍선 (소리)", 260, 56, "wool-cream", 24, stitch=False, x=60, y=20,
             children=[center([icon("volume-2", 18, "felt-coral"), text("어른 불러 줄래?", 18)], 260, 56,
                              direction="horizontal", gap=8)]),
        {"type": "frame", "id": nid("card"), "name": "보호자 카드", "x": 384, "y": 60, "width": 380, "height": 240,
         "cornerRadius": 24, "fill": c("white"), "effect": SHADOW_PRESSED, "layout": "none", "children": [
             wtext("다시 로그인해 주세요", 20, weight="700", x=28, y=28, w=320),
             wtext("보안을 위해 로그인이 풀렸어요. 폰 안의 책은 그대로 있어요.", 13, "ink-soft", x=28, y=66, w=320),
             kakao_btn(28, 120, 324), text("다른 방법으로 로그인", 13, "ink-soft", font=PARENT_FONT, weight="600",
                                           x=126, y=190)]}]
    return S


def _reid(nodes):
    for n in nodes:
        if isinstance(n, dict):
            n["id"] = nid("cp")
            _reid(n.get("children", []))


# 화면 배치 순서 (흐름도 번호) — (이름, 흐름도 단계, 참고한 앱 · 규칙)
SCREEN_ORDER = [
    ("⓪ CLAP", "처음 · 매번", "팀 이름. 펠트 글자 네 개가 0.11초 간격으로 통통 튀어 오르고 1.4초 뒤 옅어지며 ①로 · 누르면 바로 넘어감 (지금 앱 SplashScreen을 펠트로)"),
    ("① 스플래시", "처음 · 매번", "커튼이 열리고 조명 아래 오또가 목소리로 인사 · 3초 넘는 강제 로고 없음 · 어디든 누르면 시작 (Toca · Khan Kids)"),
    ("② 보호자 환영", "처음 한 번", "아이에게는 \"어른 불러 줘\" 음성, 어른은 2초 길게 눌러 확인 (Duolingo ABC · Buddy.ai · YouTube Kids 게이트)"),
    ("③ 로그인 / 회원가입", "처음 한 번", "카카오(#FEE500·모서리 12·심볼 필수) → 네이버(#03C75A) → Google(흰 바탕·#747775 테두리, 다른 버튼보다 작게 금지) 같은 크기로 세로 · 이메일은 글자 링크"),
    ("③ 이메일로 시작", "처음 한 번", "입력 두 칸 · 인증 메일 · 비밀번호 재설정 경로"),
    ("④ 보호자 동의", "처음 한 번", "전체 동의 → 필수 3 · 선택 1(미리 체크 안 함) · 필수를 다 체크해야 버튼이 켜짐 (개인정보보호법 22조의2)"),
    ("⑤ 마이크 켜기", "처음 한 번", "시스템 창 전에 이유 · 거부해도 되는 이유를 먼저 · \"나중에\" 버튼 필수 (Android 권한 가이드 · Duolingo ABC)"),
    ("⑥ 부모 비밀번호", "처음 한 번", "왼쪽 제목 · 점 4개, 오른쪽 3×4 키패드(76×50) · 확인 입력 · 잊었을 때 경로"),
    ("⑦ 오또 인사 · 방 둘러보기", "처음 한 번", "슬라이드 없이 실제 첫 화면 위에서 하나만 밝히고 손가락 맥동 (Toca · Pok Pok)"),
    ("화면 1 · 오또의 방 (800×360)", "매일 ⑨", "버튼 없이 물건이 메뉴 (Toca · Pok Pok · Khan Kids)"),
    ("이야기 만들기 · 주인공 고르기", "매일 · 이야기", "캐릭터 커스텀은 여기서만 · 도감 3칸 + 새로 만들기 (보호자 정보만 저장)"),
    ("화면 2 · 이야기 짓기 — 오또가 말해요", "매일 · 이야기", "이야기 만들기 배경 = 인형극 무대(방의 무대 안으로 들어옴) · 마주 보고 대화 · 분홍 물결 (Buddy.ai · Moxie)"),
    ("화면 3 · 이야기 짓기 — 네 차례야 (듣는 중)", "매일 · 이야기", "청록 물결일 때만 녹음 · 되받아 말하기 (Buddy.ai · Lingokids)"),
    ("화면 4 · 이야기 짓기 — 말이 없으면 골라 줘", "매일 · 이야기", "말이 없으면 고르기 방울 3개 · 탭으로도 답함"),
    ("화면 5 · 오늘 이야기 — 네 차례야 (아침 창가)", "매일 · 오늘", "일기 모드 배경 = 아침 창가: 하늘색 벽 · 펠트 깃발 줄 · 햇살 · 밝은 나무 바닥. 화면 짜임은 이야기 만들기와 같고 배경만 다름"),
    ("화면 6 · 같이 만들기 — 부모 띠 (저녁 거실)", "매일 · 같이", "같이 만들기 배경 = 저녁 거실: 복숭아색 벽 · 스탠드 불빛 · 소파. 아래 부모 띠는 부모가 읽고 아이에게 물어봄 (지금 앱 ParentBand)"),
    ("⑩ 책 만드는 중", "매일", "스피너 대신 오또가 그리는 몸짓 + 제목 짓기 질문 · 붓 칸 진행 (숫자 % 없음)"),
    ("⑪ 책 읽기", "매일", "그림 80% · 아래 자막 띠에 읽는 낱말 강조 · ◀▶ 64dp를 가장자리에서 띄움 · 왼쪽 위 닫기, 오른쪽 위 다시 듣기 (Epic! · Vooks)"),
    ("⑫ 친구 평가", "매일", "또 만날래 / 안녕 두 개만 · 점수 없음"),
    ("⑬ 선물", "매일", "상자를 누르면 열리고 선물이 오또의 방으로 날아감 · 결제 · 순위 없음 (Khan Kids 수집)"),
    ("⑭ 책장", "매일", "지금 앱 책장 그대로 — 나무 책장 그림 · 선반 두 칸에 표지가 보이게 4권씩 · 새 책은 위에서 내려와 꽂히고 「새 책!」 · ✏️ · ✨ · 왼쪽 위 권수 · 오른쪽 아래 [부모 모드] [처음으로]"),
    ("⑮ 하루 한도에 닿으면", "매일", "밤 장면 · 오또가 하품 · 아이용 버튼 없음 · 구석 작은 어른 버튼 → 비밀번호 → 연장 (Lingokids · YouTube Kids)"),
    ("🔒 부모 문 → 태어난 해", "어디서든", "PIN 대신 태어난 해 · 틀려도 잠그지 않음 · 3번 틀리면 잠시 대기"),
    ("부모 영역 · 기록", "어디서든", "왼쪽 메뉴 22% + 오른쪽 카드 · 첫 카드는 이번 주 요약 · 차분한 성인 UI (Lingokids Parents Area)"),
    ("부모 영역 · 계정", "어디서든", "로그인 정보 · 로그아웃(데이터 유지) · 맨 아래 작은 회원 탈퇴 (Google Play 계정 삭제 요건)"),
    ("탈퇴 ① 안내", "어디서든 · 탈퇴", "지워지는 것을 숫자로 · 사유 묻지 않음 · 폰 데이터 삭제는 따로 고름 (Google · 네이버 방식)"),
    ("탈퇴 ② 본인 확인 · 최종 확인", "어디서든 · 탈퇴", "소셜 재로그인으로 본인 확인 · 되돌릴 수 없음 경고 한 번 · 붙잡기 화면 없음"),
    ("예외 · 오프라인", "예외", "막다른 화면 없음 · 아이용 하나(책장) + 어른용 작게 (Khan Kids 오프라인)"),
    ("예외 · 마이크가 꺼져 있을 때", "예외", "기능만 끄고 나머지는 진행 · 반복해서 조르지 않음 (Android 권한 가이드)"),
    ("예외 · 로그인이 풀렸을 때", "예외", "아이에게는 음성, 어른에게는 같은 버튼으로 다시 로그인"),
]


def screens_board(children, C, P):
    """흐름도 순서대로 모든 화면을 4열 격자에 놓는다. 기존 화면 1~4 판도 여기로 옮긴다."""
    S = screens(C, P)
    S.update(screens_v2(C))
    S.update(screens_v3(C))
    S.update(screens_mode_extra(C))
    LAST["S"] = S
    for k_, pose_ in POSE_OF_SCREEN.items():
        name_poses(S[k_], pose_)
    existing = {b["name"]: b for b in children if b.get("name", "").startswith("화면 ")}
    for b in existing.values():
        children.remove(b)
    COLS, CW, CH, TOP = 4, 860, 540, 150
    K = [text("06 화면 — 흐름도 순서대로", 36, x=40, y=36),
         text("화면 아래 글: 흐름도 단계 · 참고한 앱과 규칙. 아이 화면의 글자는 설명용이며, 실제로는 오또가 소리로 말한다.", 14,
              "ink-soft", font=PARENT_FONT, x=42, y=90)]
    for i, (name, step, why) in enumerate(SCREEN_ORDER_V2):
        col, row = i % COLS, i // COLS
        x, y = 40 + col * CW, TOP + row * CH
        if name in existing:
            fr = existing[name]
            fr["x"], fr["y"] = x, y + 44
        else:
            fr = board(name, x, y + 44, 800, 360, S[name])
        fr["cornerRadius"] = 20
        fr["effect"] = SHADOW_SOFT
        K.append({"type": "frame", "id": nid("chip"), "name": "단계", "x": x, "y": y, "height": 30, "width": 150,
                  "cornerRadius": 15, "fill": c("felt-teal"), "layout": "horizontal", "justifyContent": "center",
                  "alignItems": "center", "children": [text(step, 13, "white", font=PARENT_FONT, weight="700")]})
        K.append(text(name.split(" (")[0], 18, font=PARENT_FONT, weight="700", x=x + 164, y=y + 3))
        K.append(fr)
        K.append(text(why, 13, "ink-soft", font=PARENT_FONT, x=x, y=y + 416, width=800, textGrowth="fixed-width",
                      lineHeight=1.45))
    rows = (len(SCREEN_ORDER_V2) + COLS - 1) // COLS
    return board("06 화면 — 흐름도 순서대로", 0, 4100, 40 + COLS * CW, TOP + rows * CH + 40, K)



# ══ v2 (2026-09-28 저녁) — 새 로고 · 방 이동 확인 · 책 재화 · 새 진행 바 · 나레이션 칸 · 오또 자세 ══
LOGO_MARK = "assets/logo_otto_v2_mark.png"   # 작은 자리용 (글자만)
LOGO = "assets/logo_otto_v2.png"          # Black Han Sans 펠트 패치 글자 (tools/make_logo2.py 후보 D)
FACE = "assets/mascot_face.png"

# 오또 자세 — 지금은 그림이 한 장뿐이라 같은 그림에 자세 이름만 붙인다 (07 판 참고)
POSES = {
    "인사": "손 흔들기 — 튜토리얼에서 처음 만날 때",
    "폰 내밀기": "보호자 환영 — 어른에게 폰을 건넴",
    "가리키기": "오또의 방 · 튜토리얼 — 추천 물건을 손으로 가리킴",
    "걷기": "오또의 방 — 누른 물건 쪽으로 걸어감",
    "말하기": "나레이션 칸 얼굴 — 입을 움직임",
    "귀 쫑긋": "나레이션 칸 얼굴 — 들을 때 (이때만 녹음)",
    "갸웃": "나레이션 칸 얼굴 · 오프라인 — 생각할 때",
    "하품": "하루 한도 — 잘 자 인사",
    "어른 부르기": "로그인 풀림 — 손나팔",
}


POSE_FILE = {"인사": "wave", "폰 내밀기": "phone", "가리키기": "point", "걷기": "walk", "말하기": "talk",
             "귀 쫑긋": "listen", "갸웃": "think", "하품": "yawn", "어른 부르기": "call"}


def pose_img(pose):
    """ComfyUI 로 만든 자세 그림 (tools/make_poses.py → tools/collect_poses.py). 없으면 기본 그림."""
    f = POSE_FILE.get(pose)
    path = f"assets/poses/{f}.png" if f else None
    return path if path and os.path.exists(os.path.join(HERE, "..", path)) else MASCOT


def mascot(pose, w, x, y):
    return image(pose_img(pose), w, w, x=x, y=y, name=f"오또 · 자세: {pose}")


def star_progress(done, total=6):
    """진행 — 장면 위에서도 읽히게 흰 펠트 판 위에 털실 길 · 별 구슬 · 끝 메달."""
    TW, W = 250, 330
    complete = done >= total
    fill_w = max(18, TW * done / total)
    kids = [
        felt("판", W, 46, "white", 23, stitch=False, texture=0.35, x=0, y=6),
        felt("길", TW, 16, "wool-cream", 8, stitch=False, shadow=False, x=16, y=21, children=[
            {"type": "rectangle", "id": nid("in"), "name": "안쪽 그늘", "x": 2, "y": 2, "width": TW - 4, "height": 4,
             "cornerRadius": 2, "fill": "#3A2A2014"}]),
        felt("차오른 만큼", fill_w, 16, "felt-mustard", 8, stitch=False, shadow=False, x=16, y=21, children=[
            {"type": "rectangle", "id": nid("hl"), "name": "윤기", "x": 5, "y": 3, "width": max(fill_w - 10, 2),
             "height": 4, "cornerRadius": 2, "fill": "#FFFFFF73"}]),
    ]
    for i in range(1, total):
        px = 16 + TW * i / total - 9
        kids.append(star(18, c("white") if i <= done else "#8A735F4D", x=px, y=20,
                         stroke=("#C98A12", 1.2) if i <= done else None, name=f"{i}쪽 별"))
    # 끝 메달 — 리본 꼬리 두 개 + 펠트 원 + 별
    mx = W - 58
    kids += [{"type": "path", "id": nid("rb"), "name": "리본 꼬리", "x": mx + 12, "y": 36, "width": 14, "height": 22,
              "geometry": "M0 0 L14 0 L14 22 L7 16 L0 22 Z", "fill": c("felt-coral")},
             {"type": "path", "id": nid("rb"), "name": "리본 꼬리", "x": mx + 30, "y": 36, "width": 14, "height": 22,
              "geometry": "M0 0 L14 0 L14 22 L7 16 L0 22 Z", "fill": c("felt-coral")},
             felt("메달" + (" (완성 · 숨 쉼)" if complete else ""), 56, 56, "felt-mustard" if complete else "wool", 28,
                  x=mx, y=0, stroke={"align": "inside", "thickness": 3, "fill": c("felt-mustard")},
                  children=[star(34, c("white") if complete else c("felt-mustard"), x=11, y=10,
                                 name="별 (안쪽부터 차오름)")])]
    if complete:
        kids.append(icon("sparkles", 18, "felt-mustard", x=mx + 48, y=-6))
    return {"type": "frame", "id": nid("f"), "name": f"⭐ 진행 · {'완성' if complete else f'{done}/{total}'}",
            "width": W, "height": 60, "layout": "none", "clip": False, "reusable": True, "children": kids}


def book_wallet(n=3):
    """오늘 만들 수 있는 동화책 — 재화처럼 (지금 앱 StarWallet 자리)."""
    return {"type": "frame", "id": nid("f"), "name": "📖 오늘 만들 수 있는 책 (재화)", "width": 128, "height": 56,
            "layout": "none", "reusable": True, "clip": False, "children": [
                felt("판", 104, 44, "white", 22, stitch=False, texture=0.35, x=24, y=6, children=[
                    text(f"{n}", 28, x=40, y=4), text("권", 14, "ink-soft", x=64, y=18)]),
                felt("책 동전", 52, 52, "felt-coral", 26, x=0, y=2,
                     stroke={"align": "inside", "thickness": 3, "fill": "#FFFFFF80"},
                     children=[center([icon("book-open", 26)], 52, 52)])]}


MODE_SKIN = {  # 나레이션 칸 테두리 색 · 모드 표시
    "story": ("curtain", "이야기 만들기", "drama"),
    "diary": ("felt-sky", "오늘 이야기", "sun"),
    "coop": ("felt-teal", "같이 만들기", "users"),
}
def face_img(state):
    f = {"talk": "talk", "listen": "listen", "think": "think"}[state]
    path = f"assets/poses/face_{f}.png"
    return path if os.path.exists(os.path.join(HERE, "..", path)) else FACE


STATE_RING = {"talk": ("cheek", "말하기"), "listen": ("felt-teal", "귀 쫑긋"), "think": ("felt-mustard", "갸웃")}


def narration(mode, state, line, x=16, y=248):
    """아래 나레이션 칸 — 왼쪽에 오또 얼굴(상태 색 테두리), 오른쪽에 오또가 하는 말."""
    col, mname, mic_ = MODE_SKIN[mode]
    ring, pose = STATE_RING[state]
    W, H = 768, 100
    kids = [felt("나레이션 칸", W, H, "wool", 26, stitch=True, texture=0.5, x=0, y=0,
                 stroke={"align": "inside", "thickness": 5, "fill": c(col)}),
            felt(f"모드 표시 · {mname}", 132, 30, col, 15, stitch=False, x=W - 150, y=-14,
                 children=[center([icon(mic_, 15), text(mname, 14, "white", font=PARENT_FONT, weight="700")],
                                  132, 30, direction="horizontal", gap=6)])]
    if state == "listen":
        kids.append({"type": "ellipse", "id": nid("pulse"), "name": "듣는 중 파동", "x": 2, "y": -40, "width": 136,
                     "height": 136, "fill": "#4FAF9840"})
    kids += [{"type": "frame", "id": nid("pt"), "name": f"오또 얼굴 · 자세: {pose}", "x": 12, "y": -30, "width": 116,
              "height": 116, "cornerRadius": 58, "clip": True, "fill": c("wool-cream"), "effect": SHADOW_SOFT,
              "stroke": {"align": "inside", "thickness": 6, "fill": c(ring)}, "layout": "none",
              "children": [image(face_img(state), 116, 116, name="얼굴")]},
             text(line, 24, x=150, y=20, width=440, textGrowth="fixed-width", lineHeight=1.35)]
    if state == "talk":
        kids.append(felt("🔊 말하는 중", 48, 48, "cheek", 24, stitch=False, x=W - 68, y=34,
                         children=[center([icon("volume-2", 24)], 48, 48)]))
    if state == "listen":
        bars = [10, 22, 32, 16, 26, 12, 20]
        kids.append(felt("🎙 아이 목소리 (출렁)", 124, 48, "felt-teal", 24, stitch=False, x=W - 144, y=34,
                         children=[{"type": "rectangle", "id": nid("bar"), "x": 18 + i * 13, "y": 24 - h // 2,
                                    "width": 6, "height": h, "cornerRadius": 3, "fill": c("white")}
                                   for i, h in enumerate(bars)]))
    return {"type": "frame", "id": nid("nr"), "name": f"나레이션 · {mname} · {state}", "x": x, "y": y, "width": W,
            "height": H, "layout": "none", "clip": False, "children": kids}


def page_scene(scene, cast=(), dim=False):
    """전체 화면 = 지금 만들어지는 그림책 한 쪽. 아이 말에 따라 인물이 하나씩 붙는다."""
    k = [image(scene, 800, 360, name="만들어지는 그림책 쪽 (전체 배경)")]
    for (img, w, x, y, nm) in cast:
        k.append(image(img, w, w, x=x, y=y, name=nm))
    k.append({"type": "rectangle", "id": nid("fade"), "name": "아래 어둡게 (글 읽기 쉽게)", "x": 0, "y": 200,
              "width": 800, "height": 160, "fill": "#3A2A2033"})
    if dim:
        k.append({"type": "rectangle", "id": nid("dim"), "name": "고르는 동안 흐리게", "x": 0, "y": 0, "width": 800,
                  "height": 360, "fill": "#FFF7EC80"})
    return k


def top_bar(C, prog="prog2"):
    return [ref(C["home"], 12, 10), ref(C["gate"], 76, 18), ref(C[prog], 235, 2)]


def confirm_dialog(ic, question, x=150, y=64):
    return {"type": "frame", "id": nid("dlg"), "name": f"확인 창 · {question}", "x": x, "y": y, "width": 330,
            "height": 232, "layout": "none", "clip": False, "children": [
                felt("창", 330, 232, "wool", 32, texture=0.5,
                     stroke={"align": "inside", "thickness": 4, "fill": c("felt-mustard")}),
                felt("무엇으로 가는지", 76, 76, "felt-coral", 38, x=127, y=-34,
                     stroke={"align": "inside", "thickness": 4, "fill": "#FFFFFF99"},
                     children=[center([icon(ic, 38)], 76, 76)]),
                {"type": "frame", "id": nid("q"), "name": "질문 (오또 목소리로도)", "x": 0, "y": 56, "width": 330,
                 "height": 40, "layout": "horizontal", "justifyContent": "center", "alignItems": "center", "gap": 8,
                 "children": [icon("volume-2", 20, "felt-coral"), text(question, 26)]},
                felt("✕ 아니", 120, 76, "wool-cream", 26, x=34, y=124,
                     children=[center([icon("x", 30, "ink"), text("아니", 16)], 120, 76, gap=2)]),
                felt("✓ 응!", 120, 76, "felt-teal", 26, x=176, y=124,
                     children=[center([icon("check", 30), text("응!", 16, "white")], 120, 76, gap=2)])]}


def room_base(C, pose="가리키기", mascot_xy=(214, 106), mascot_w=214, trail=None):
    stripes, window, theater, sofa, shelf = room_objects()
    k = [*stripes, felt("바닥", 800, 100, "stage-wood", 0, stitch=False, shadow=False, x=0, y=262),
         {"type": "ellipse", "id": nid("rug"), "name": "러그", "x": 196, "y": 296, "width": 250, "height": 50,
          "fill": "#F48C9266"}, window, theater, shelf, sofa]
    for (px, py) in (trail or []):
        k.append(icon("paw-print", 22, "stage-wood-deep", x=px, y=py))
    k += [mascot(pose, mascot_w, *mascot_xy), ref(C["gate"], 12, 12), ref(C["wallet"], 660, 8)]
    return k


def screens_v2(C):
    S = {}
    ts = [n for n in title_splash() if n.get("type") != "text"
          and n.get("name") not in ("오", "또", "누르면 시작 (맥동)")
          and not (n.get("type") == "rectangle" and n.get("name", "").startswith("오또"))]
    S["① 스플래시"] = ts + [image(LOGO, 460, 262, x=170, y=20, name="오또 로고 (Black Han Sans 펠트 패치 · 손 흔드는 오또 · 리본)"),
                          felt("누르면 시작 (맥동)", 150, 40, "wool", 20, stitch=False, x=325, y=300,
                               children=[center([icon("pointer", 16, "felt-coral"), text("눌러서 시작", 15)], 150, 40,
                                                direction="horizontal", gap=6)])]

    # ⑦ 기능 소개 — 설정이 끝나면 보호자와 아이가 함께 봄
    cards = [("1", "말하면", "오또에게 말로 이야기해요", "mic", "felt-teal"),
             ("2", "그림책이 돼요", "말한 대로 그림이 그려져요", "book-open", "felt-coral"),
             ("3", "책장에 모여요", "만든 책은 언제든 다시 봐요", "library", "felt-mustard")]
    k = [{"type": "rectangle", "id": nid("bg"), "name": "배경", "x": 0, "y": 0, "width": 800, "height": 360,
          "fill": c("wool")}, image(TEXTURE, 800, 360, opacity=0.4, name="양모 결"),
         image(LOGO_MARK, 102, 60, x=22, y=16, name="로고 (글자만)"),
         text("오또랑 이렇게 놀아요", 30, x=250, y=24)]
    for i, (num, t_, s_, ic, col) in enumerate(cards):
        cx = 60 + i * 236
        k.append(felt(f"기능 {num} · {t_}", 208, 200, "white", 28, x=cx, y=84, texture=0.3, children=[
            felt("번호", 36, 36, col, 18, stitch=False, x=-10, y=-12,
                 children=[center([text(num, 18, "white")], 36, 36)]),
            felt("그림", 96, 96, col, 48, x=56, y=20, children=[center([icon(ic, 46)], 96, 96)]),
            center([text(t_, 22), text(s_, 12, "ink-soft", font=PARENT_FONT)], 208, 64, x=0, y=124, gap=4)]))
        if i < 2:
            k.append(icon("chevron-right", 28, "ink-soft", x=cx + 213, y=170))
    k += [text("방금 해 본 것 — 오또가 목소리로 한 번 더 정리해 줘요", 13, "ink-soft", font=PARENT_FONT, x=60, y=312),
          pbtn("오또의 방으로", 590, 300, 180, 48)]
    S["⑦ 기능 소개"] = k

    # ⑦ 튜토리얼 ① — 방에서 물건 하나 눌러 보기
    rb = room_base(C, pose="가리키기")[:-2]          # 부모 문 · 재화는 튜토리얼 동안 숨김
    theater = [n for n in rb if n.get("name", "").startswith("🎭")][0]
    rb.remove(theater)
    S["⑥ 튜토리얼 ① 눌러 보기"] = rb + [
        {"type": "rectangle", "id": nid("dim"), "name": "나머지 흐리게", "x": 0, "y": 0, "width": 800, "height": 360,
         "fill": "#3A2A2066"},
        {"type": "ellipse", "id": nid("spot"), "name": "조명", "x": 372, "y": 54, "width": 236, "height": 236,
         "fill": "#FFF7EC40"},
        theater,
        felt("👆 누르는 손 (맥동)", 64, 64, "white", 32, stitch=False, x=540, y=214,
             children=[center([icon("pointer", 34, "ink")], 64, 64)]),
        felt("말풍선 (소리)", 290, 56, "wool", 24, stitch=False, x=24, y=24,
             children=[center([icon("volume-2", 18, "felt-coral"), text("여기를 눌러 봐!", 18)], 290, 56,
                              direction="horizontal", gap=8)])]

    # ⑦ 튜토리얼 ② — 말해 보기 연습
    S["⑥ 튜토리얼 ② 말해 보기"] = [
        *page_scene(A + "bg_park.png"),
        felt("연습 표시", 110, 32, "felt-mustard", 16, stitch=False, x=345, y=14,
             children=[center([icon("sparkles", 14), text("연습", 15, "white")], 110, 32, direction="horizontal",
                              gap=6)]),
        narration("story", "listen", "좋아하는 동물을 말해 줄래?")]

    # ⑨ 오또의 방 · 물건을 누르면 오또가 걸어가서 묻는다
    S["⑨ 오또의 방"] = room_base(C)
    S["⑨-1 책장을 눌렀을 때"] = room_base(C, pose="걷기", mascot_xy=(470, 96), mascot_w=200,
                                     trail=[(300, 318), (360, 330), (420, 318)]) + [
        {"type": "rectangle", "id": nid("dim"), "name": "나머지 흐리게", "x": 0, "y": 0, "width": 800, "height": 360,
         "fill": "#3A2A204D"}, confirm_dialog("library", "책장으로 갈까?", x=86, y=76)]
    S["⑨-2 인형극 무대를 눌렀을 때"] = room_base(C, pose="걷기", mascot_xy=(300, 112), mascot_w=190,
                                        trail=[(290, 330)]) + [
        {"type": "rectangle", "id": nid("dim"), "name": "나머지 흐리게", "x": 0, "y": 0, "width": 800, "height": 360,
         "fill": "#3A2A204D"}, confirm_dialog("drama", "이야기 만들러 갈까?", x=440, y=76)]

    # 이야기 짓기 — 전체 화면 그림책 + 아래 나레이션 칸 (세 모드 공통)
    hero = (A + "hero_short_red_round.png", 150, 250, 70, "주인공 (아이가 고른 캐릭터)")
    dino = (A + "dino_trex.png", 170, 460, 60, "친구 (아이가 말한 대로 붙음)")
    S["이야기 만들기 — 오또가 말해요"] = [*page_scene(A + "bg_dino.png", [hero]), *top_bar(C),
                                 narration("story", "talk", "숲에서 누구를 만났을까?")]
    S["이야기 만들기 — 네 차례야"] = [*page_scene(A + "bg_dino.png", [hero]), *top_bar(C),
                               felt("되받아 말하기", 260, 48, "white", 24, stitch=False, x=460, y=176,
                                    children=[center([icon("ear", 20, "felt-teal"), text("\"공룡을 만났구나!\"", 18)],
                                                     260, 48, direction="horizontal", gap=8)]),
                               narration("story", "listen", "숲에서 누구를 만났을까?")]
    cb, cbs = C["cb"], C["cb_s"]
    S["이야기 만들기 — 말이 없으면 골라 줘"] = [
        *page_scene(A + "bg_dino.png", [hero], dim=True), *top_bar(C),
        ref(cb, 220, 70, {cb["_label"]: {"content": "공룡"}}), ref(cbs, 360, 70, {cbs["_label"]: {"content": "토끼"}}),
        ref(cb, 500, 70, {cb["_label"]: {"content": "로봇"}}),
        narration("story", "think", "공룡일까, 토끼일까, 로봇일까?")]
    S["오늘 이야기 — 네 차례야"] = [
        *page_scene(A + "bg_playground.png", [(A + "hero_long_yellow_none.png", 160, 300, 60, "주인공")]),
        *top_bar(C), narration("diary", "listen", "오늘 놀이터에서 뭐 하고 놀았어?")]
    S["같이 만들기 — 부모 띠"] = [
        *page_scene(A + "bg_home.png", [(A + "hero_tied_blue_square.png", 150, 320, 60, "주인공")]), *top_bar(C),
        {"type": "frame", "id": nid("band"), "name": "부모 띠 (부모가 읽고 물어봄)", "x": 150, "y": 196, "width": 634,
         "height": 40, "cornerRadius": 20, "fill": "#3A2A20E6", "layout": "horizontal", "alignItems": "center",
         "gap": 10, "padding": [0, 14], "children": [
             felt("부모님", 78, 26, "felt-teal", 13, stitch=False, shadow=False,
                  children=[center([text("부모님", 12, "white", font=PARENT_FONT, weight="700")], 78, 26)]),
             text("\"할머니 집에서 뭐가 제일 재미있었어?\" 하고 물어봐 주세요", 14, "white", font=PARENT_FONT,
                  weight="600")]},
        narration("coop", "talk", "할머니랑 뭐 했는지 들려줘!")]

    # ⑩ 책 만드는 중 — 같은 틀: 흐릿한 그림책 + 가운데 바느질 로딩 + 나레이션
    stitches = [{"type": "rectangle", "id": nid("stc"), "name": "바느질 땀", "x": 12 + i * 22, "y": 13, "width": 12,
                 "height": 4, "cornerRadius": 2, "fill": "#FFFFFFB3"} for i in range(9)]
    S["⑩ 책 만드는 중"] = [
        image(A + "bg_dino.png", 800, 360, opacity=0.35, name="그려지는 그림 (점점 또렷해짐)"),
        {"type": "rectangle", "id": nid("bg"), "name": "양모 막", "x": 0, "y": 0, "width": 800, "height": 360,
         "fill": "#FFF7EC80"},
        felt("책 (펼쳐지는 중)", 150, 110, "curtain", 18, x=325, y=40, children=[
            felt("속지", 128, 90, "white", 12, stitch=False, shadow=False, x=11, y=10,
                 children=[center([icon("paintbrush", 40, "felt-coral")], 128, 90)])]),
        felt("로딩 판", 400, 52, "white", 26, stitch=False, texture=0.35, x=200, y=168, children=[
            felt("길", 360, 24, "wool-cream", 12, stitch=False, shadow=False, x=20, y=14),
            felt("꿰맨 만큼", 216, 24, "felt-coral", 12, stitch=False, shadow=False, x=20, y=14, children=stitches),
            felt("바늘 (앞으로 감)", 36, 36, "felt-mustard", 18, stitch=False, x=218, y=8,
                 children=[center([icon("pen-tool", 18)], 36, 36)])]),
        text("그림 5장 중 3장 — 숫자 대신 바느질이 앞으로 가요", 12, "ink-soft", font=PARENT_FONT, x=262, y=226),
        narration("story", "talk", "책 이름은 뭐라고 할까?")]

    # 예외 · 마이크가 꺼져 있을 때 — 같은 틀
    S["예외 · 마이크가 꺼져 있을 때"] = [
        *page_scene(A + "bg_dino.png", [hero], dim=True), *top_bar(C),
        ref(cb, 220, 70, {cb["_label"]: {"content": "공룡"}}), ref(cb, 360, 70, {cb["_label"]: {"content": "토끼"}}),
        ref(cb, 500, 70, {cb["_label"]: {"content": "로봇"}}),
        narration("story", "think", "골라서 알려 줄래?")]

    return S


SCREEN_ORDER_V2 = [
    ("⓪ CLAP", "처음 · 매번", "팀 이름. 펠트 글자 네 개가 0.11초 간격으로 통통 튀어 오르고 1.4초 뒤 옅어지며 ①로 · 누르면 바로 넘어감"),
    ("① 스플래시", "처음 · 매번", "새 로고(Black Han Sans 펠트 패치 글자 · 흰 테두리 · 바느질 · 손 흔드는 오또 · 청록 리본)를 가운데 크게 · 커튼이 열리고 오또 목소리로 인사"),
    ("② 로그인 (1/3)", "처음 · 설정", "첫 설정 화면 · 왜 가입하는지 한 줄 (토스) · 카카오 → 네이버 → Google 같은 크기 · 이메일은 글자 링크 · 로그인 없이 샘플 책 보기(녹음 없음) [자세: 인사]"),
    ("② 이메일로 계속 (1/3)", "처음 · 설정", "한 화면 한 가지 (토스) · 인증 메일 · 오른쪽 아래 「다음」"),
    ("③ 동의 (2/3)", "처음 · 설정", "왼쪽 그림 세 칸으로 데이터가 어떻게 쓰이는지 · 모두 동의 + 필수 3(법정대리인 동의 포함) · 선택은 미리 체크 안 함"),
    ("④ 마이크 (3/3)", "처음 · 설정", "말할 차례에만 듣는다는 것 · 「나중에」 필수 · 거부해도 고르기로 진행 (Android 권한 가이드 · Duolingo ABC) [자세: 귀 쫑긋]"),
    ("⑤ 아이에게 건네기", "설정 끝", "진행 점 없음 · 부모 PIN은 만들지 않음 — 부모 영역은 매번 태어난 해로 (Lingokids · ABCmouse · Sago)"),
    ("⑥ 튜토리얼 ① 눌러 보기", "설정 끝", "슬라이드 없이 실제 방에서 무대 하나만 밝히고 손가락 맥동 (Toca · Pok Pok) [자세: 가리키기]"),
    ("⑥ 튜토리얼 ② 말해 보기", "설정 끝", "나레이션 칸에서 한 번 말해 보기 — 틀려도 괜찮은 연습"),
    ("⑦ 기능 소개", "설정 끝", "튜토리얼에서 해 본 것을 세 가지로 정리: 말하면 → 그림책이 돼요 → 책장에 모여요 · 「해 볼래요」 → 오또의 방"),
    ("⑨ 오또의 방", "매일", "물건이 메뉴 · 우측 상단 오늘 만들 수 있는 책 3권(재화, 지금 앱 StarWallet 자리) [자세: 가리키기]"),
    ("⑨-1 책장을 눌렀을 때", "매일", "오또가 발자국을 남기며 책장으로 걸어가서 \"책장으로 갈까?\" — 그림 · 목소리 · ✓/✕ 큰 버튼 [자세: 걷기]"),
    ("⑨-2 인형극 무대를 눌렀을 때", "매일", "창문 · 소파도 같은 방식: 오또가 걸어가서 \"○○ 하러 갈까?\" [자세: 걷기]"),
    ("이야기 만들기 · 주인공 고르기", "매일 · 이야기", "캐릭터 커스텀은 여기서만 · 도감 3칸 + 새로 만들기"),
    ("이야기 만들기 — 오또가 말해요", "매일 · 이야기", "전체 화면 = 만들어지는 그림책 쪽 · 아래 나레이션 칸에 오또 얼굴(분홍 테두리) [자세: 말하기]"),
    ("이야기 만들기 — 네 차례야", "매일 · 이야기", "얼굴 테두리 청록 + 파동 · 목소리 막대가 출렁 · 들은 말을 장면 위에 되받아 말함 [자세: 귀 쫑긋]"),
    ("이야기 만들기 — 말이 없으면 골라 줘", "매일 · 이야기", "장면을 흐리게 하고 고르기 방울 3개 · 얼굴 테두리 겨자 [자세: 갸웃]"),
    ("오늘 이야기 — 오또가 말해요", "매일 · 오늘", "칸 테두리 파랑 + 「오늘 이야기」 · 오늘 있었던 일을 물음 [자세: 말하기]"),
    ("오늘 이야기 — 네 차례야", "매일 · 오늘", "같은 틀 · 나레이션 칸 테두리 파랑 + 「오늘 이야기」 표시"),
    ("오늘 이야기 — 말이 없으면 골라 줘", "매일 · 오늘", "오늘 갔던 곳에서 할 만한 일 세 가지 [자세: 갸웃]"),
    ("같이 만들기 — 부모 띠", "매일 · 같이", "같은 틀 · 테두리 청록 + 「같이 만들기」 표시 · 나레이션 위에 부모 띠 (지금 앱 ParentBand)"),
    ("같이 만들기 — 네 차례야", "매일 · 같이", "부모 띠는 기다려 달라는 안내로 바뀜 [자세: 귀 쫑긋]"),
    ("같이 만들기 — 말이 없으면 골라 줘", "매일 · 같이", "부모 띠는 힌트 주는 법으로 바뀜 [자세: 갸웃]"),
    ("⑩ 책 만드는 중", "매일", "같은 틀 · 흐릿한 그림이 점점 또렷해짐 · 가운데 바느질 로딩(바늘이 앞으로 감, 숫자 % 없음) · 나레이션으로 제목 짓기"),
    ("⑪ 책 읽기", "매일", "그림 80% · 아래 자막 띠에 읽는 낱말 강조 · ◀▶ 64dp · 왼쪽 위 닫기, 오른쪽 위 다시 듣기 (Epic! · Vooks)"),
    ("⑫ 친구 평가", "매일", "안녕 · 친구 · 또 만날래 좌우 대칭 · 점수 없음"),
    ("⑬ 선물", "매일", "상자를 누르면 열리고 선물이 오또의 방으로 날아감 (Khan Kids 수집)"),
    ("⑭ 책장", "매일", "지금 앱 책장 그대로 — 선반 두 칸 · 표지 · 새 책! · ✏️ · ✨ · [부모 모드] [처음으로]"),
    ("⑮ 하루 한도에 닿으면", "매일", "밤 장면 · 아이용 버튼 없음 · 구석 작은 어른 버튼 → 태어난 해 → 연장 (Lingokids · YouTube Kids) [자세: 하품]"),
    ("🔒 부모 문 → 태어난 해", "어디서든", "PIN 대신 태어난 해 · 틀려도 잠그지 않음 · 3번 틀리면 잠시 대기"),
    ("부모 영역 · 기록", "어디서든", "왼쪽 메뉴 22% + 오른쪽 카드 · 차분한 성인 UI (Lingokids Parents Area)"),
    ("부모 영역 · 계정", "어디서든", "로그인 정보 · 로그아웃(데이터 유지) · 맨 아래 작은 회원 탈퇴 (Google Play 요건)"),
    ("탈퇴 ① 안내", "어디서든 · 탈퇴", "지워지는 것을 숫자로 · 사유 묻지 않음 · 폰 데이터 삭제는 따로 고름"),
    ("탈퇴 ② 본인 확인 · 최종 확인", "어디서든 · 탈퇴", "소셜 재로그인으로 본인 확인 · 되돌릴 수 없음 경고 한 번"),
    ("예외 · 오프라인", "예외", "막다른 화면 없음 · 아이용 하나(책장) + 어른용 작게 [자세: 갸웃]"),
    ("예외 · 마이크가 꺼져 있을 때", "예외", "같은 틀 · 고르기 방울로 끝까지 · 부모 영역에만 알림"),
    ("예외 · 로그인이 풀렸을 때", "예외", "아이에게는 음성, 어른에게는 같은 버튼으로 다시 로그인 [자세: 어른 부르기]"),
]


def poses_board():
    K = [text("07 오또 자세 — 상황마다 다른 몸짓", 36, x=40, y=36),
         text("ComfyUI(FLUX.1 Kontext)로 원본 오또에서 자세만 바꿔 만들었다 — tools/make_poses.py · collect_poses.py. 화면의 레이어 이름 「자세: ○○」로 찾는다.",
              14, "ink-soft", font=PARENT_FONT, x=42, y=90)]
    for i, (pose, where) in enumerate(POSES.items()):
        x, y = 40 + (i % 5) * 300, 140 + (i // 5) * 330
        face = pose in ("말하기", "귀 쫑긋", "갸웃")
        K.append(felt(f"자세 · {pose}", 270, 270, "wool-cream", 28, x=x, y=y, children=[
            image(pose_img(pose), 180, 180, x=45, y=14, name=f"오또 · 자세: {pose}"),
            center([text(pose, 22), text(where, 11, "ink-soft", font=PARENT_FONT)], 270, 64, x=0, y=196, gap=4)]))
        done = pose_img(pose) != MASCOT
        tag = "완성" if done else "그림 필요"
        K.append(felt(tag, 86, 26, "felt-teal" if done else "felt-coral", 13, stitch=False, shadow=False, x=x + 176,
                      y=y - 8, children=[center([text(tag, 12, "white", font=PARENT_FONT, weight="700")], 86, 26)]))
    return board("07 오또 자세", 0, 0, 1540, 820, K)


POSE_OF_SCREEN = {"② 보호자 환영": "폰 내밀기", "⑮ 하루 한도에 닿으면": "하품", "예외 · 오프라인": "갸웃",
                  "예외 · 로그인이 풀렸을 때": "어른 부르기"}


def name_poses(nodes, pose):
    for n in nodes:
        if isinstance(n, dict):
            if n.get("type") == "rectangle" and n.get("name", "").startswith("오또"):
                n["name"] = f"오또 · 자세: {pose}"
                if isinstance(n.get("fill"), dict):
                    n["fill"]["url"] = pose_img(pose)
            name_poses(n.get("children", []), pose)


def wordmark(x, y, s=34):
    return [image(LOGO_MARK, 88, 52, x=x, y=y - 8, name="로고 (글자만)")]



# ══ v3 — 초반 설정 다시 (상위 앱 온보딩 비교: Khan Kids · Lingokids · ABCmouse · Sago · Duolingo ABC · 토스) ══
OB_STEPS = ["로그인", "동의", "마이크"]


def ob_frame(step, title, sub, art, right, cta=None, cta2=None, back=True):
    """초반 설정 공통 틀 — 위: ← · 진행 점 4 / 왼쪽 340: 그림 + 한 줄 제목 / 오른쪽 460: 조작부 / 오른쪽 아래: 큰 버튼."""
    k = [{"type": "rectangle", "id": nid("bg"), "name": "배경", "x": 0, "y": 0, "width": 800, "height": 360,
          "fill": c("wool")},
         felt("왼쪽 판", 340, 360, "wool-cream", 0, stitch=False, shadow=False, texture=0.55)]
    if back:
        k.append({"type": "frame", "id": nid("bk"), "name": "← 뒤로", "x": 12, "y": 10, "width": 44, "height": 44,
                  "cornerRadius": 22, "fill": "#FFFFFFB3", "layout": "horizontal", "justifyContent": "center",
                  "alignItems": "center", "children": [icon("arrow-left", 22, "ink")]})
    if step is not None:
        dots = []
        for i, nm in enumerate(OB_STEPS):
            cur, done = i == step, i < step
            dots.append({"type": "frame", "id": nid("dt"), "name": f"진행 · {nm}", "width": 34 if cur else 12,
                         "height": 12, "cornerRadius": 6,
                         "fill": c("felt-coral") if cur else (c("felt-teal") if done else "#3A2A2026")})
        k.append({"type": "frame", "id": nid("dots"), "name": f"진행 점 {step + 1}/{len(OB_STEPS)}", "x": 540, "y": 24, "width": 100,
                  "height": 12, "layout": "horizontal", "gap": 8, "alignItems": "center", "children": dots})
        k.append(text(f"{step + 1} / {len(OB_STEPS)}  {OB_STEPS[step]}", 12, "ink-soft", font=PARENT_FONT, weight="600", x=652, y=21))
    k += art
    k += [wtext(title, 21, weight="700", x=30, y=236, w=290, lh=1.3),
          wtext(sub, 13, "ink-soft", x=30, y=292, w=290)]
    k += right
    if cta:
        k.append(pbtn(cta, 564, 290, 204, 56))
    if cta2:
        k.append(pbtn(cta2, 364, 290, 184, 56, "secondary"))
    return k


def ob_art(pose, w=190, x=75, y=36):
    return [mascot(pose, w, x, y)]


def year_boxes(x, y, digits):
    out = []
    for i in range(4):
        filled = i < len(digits)
        box = {"type": "frame", "id": nid("yb"), "name": f"연도 {i + 1}", "x": x + i * 50, "y": y, "width": 44,
               "height": 58, "cornerRadius": 14, "fill": c("white"), "layout": "horizontal",
               "justifyContent": "center", "alignItems": "center",
               "stroke": {"align": "inside", "thickness": 2.5 if i == len(digits) else 1.5,
                          "fill": c("felt-coral") if i == len(digits) else "#3A2A2026"},
               "children": [text(digits[i], 28, font=PARENT_FONT, weight="700")] if filled else []}
        out.append(box)
    return out


def mini_keypad(x, y, kw=62, kh=40, gap=6):
    keys = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫"]
    out = []
    for i, kk in enumerate(keys):
        if not kk:
            continue
        out.append({"type": "frame", "id": nid("key"), "name": f"키 {kk}", "x": x + (i % 3) * (kw + gap),
                    "y": y + (i // 3) * (kh + gap), "width": kw, "height": kh, "cornerRadius": 12,
                    "fill": c("wool-cream"), "layout": "horizontal", "justifyContent": "center", "alignItems": "center",
                    "children": [icon("delete", 18, "ink") if kk == "⌫" else text(kk, 18, font=PARENT_FONT, weight="600")]})
    return out


def year_gate(title, sub, step=None, art_pose="폰 내밀기", back=True, note="", art=None):
    right = [wtext("태어난 해를 입력해 주세요", 16, weight="700", x=372, y=62, w=300),
             *year_boxes(372, 92, "198"),
             wtext(note or "네 자리를 다 넣으면 바로 넘어가요", 12, "ink-soft", x=372, y=164, w=220),
             *mini_keypad(588, 62)]
    return ob_frame(step, title, sub, art or ob_art(art_pose), right, back=back)


def screens_v3(C):
    S = {}
    # ④ 로그인 — 왜 가입하는지 한 줄 (토스)
    bw = 404
    right = [wtext("보호자 계정으로 시작해요", 17, weight="700", x=372, y=60, w=bw),
             kakao_btn(372, 90, bw), naver_btn(372, 144, bw), google_btn(372, 198, bw),
             text("이메일로 계속하기", 13, "ink-soft", font=PARENT_FONT, weight="600", x=372, y=262),
             text("로그인 없이 샘플 책 보기", 13, "felt-teal", font=PARENT_FONT, weight="700", x=372, y=300),
             wtext("샘플 책은 녹음 없이 읽기만 해요", 11, "ink-soft", x=372, y=320, w=260)]
    S["② 로그인 (1/3)"] = ob_frame(0, "만든 책을 안전하게 보관해요", "아이 이름 · 사진은 받지 않아요. 보호자 계정 하나면 돼요.",
                                ob_art("인사"), right, back=False)
    right = [*[it for i, (lb, ph) in enumerate([("이메일", "parent@example.com"), ("비밀번호", "8자 이상 · 영문 + 숫자")])
               for it in (text(lb, 13, "ink-soft", font=PARENT_FONT, weight="600", x=372, y=62 + i * 82),
                          {"type": "frame", "id": nid("in"), "name": f"입력 · {lb}", "x": 372, "y": 84 + i * 82,
                           "width": 396, "height": 48, "cornerRadius": 12, "fill": c("white"),
                           "stroke": {"align": "inside", "thickness": 1.5, "fill": "#3A2A2026"}, "layout": "none",
                           "children": [text(ph, 15, "ink-soft", font=PARENT_FONT, x=16, y=14)]})]]
    S["② 이메일로 계속 (1/3)"] = ob_frame(0, "이메일로 계속하기", "인증 메일을 보내 드려요. 비밀번호를 잊으면 메일로 다시 만들어요.",
                                    ob_art("인사"), right, cta="다음")

    # ⑤ 동의 — 왼쪽은 데이터가 어떻게 쓰이는지 그림 세 칸
    flow3 = []
    for i, (ic, t_) in enumerate([("mic", "아이가 말해요"), ("pen-line", "오또가 글로 바꿔요"), ("book-open", "책으로 저장돼요")]):
        flow3 += [felt(t_, 64, 64, ["felt-teal", "felt-mustard", "felt-coral"][i], 32, x=36 + i * 96, y=82,
                       children=[center([icon(ic, 30)], 64, 64)]),
                  wtext(t_, 11, weight="600", x=22 + i * 96, y=156, w=92)]
        if i < 2:
            flow3.append(icon("chevron-right", 20, "ink-soft", x=106 + i * 96, y=104))
    rows = [("[필수] 이용약관", True), ("[필수] 보호자 개인정보 수집 · 이용", True),
            ("[필수] 만 14세 미만 아동의 법정대리인 동의", True), ("[선택] 새 기능 알림", False)]
    right = [checkbox(372, 62, True, big=True), text("모두 동의해요", 17, font=PARENT_FONT, weight="700", x=408, y=63),
             {"type": "rectangle", "id": nid("hr"), "name": "구분선", "x": 372, "y": 102, "width": 396, "height": 1.5,
              "fill": "#3A2A201A"}]
    for i, (t_, on) in enumerate(rows):
        yy = 116 + i * 38
        right += [checkbox(372, yy, on), text(t_, 13, font=PARENT_FONT, x=404, y=yy + 2),
                  text("보기", 12, "ink-soft", font=PARENT_FONT, x=740, y=yy + 3)]
    S["③ 동의 (2/3)"] = ob_frame(1, "이렇게만 써요", "녹음은 글로 바꾼 뒤 바로 지우고, 책은 이 폰에 저장해요.", flow3, right,
                              cta="동의하고 계속")

    # ⑥ 마이크 — 거부해도 앱은 그대로 (Android 권한 가이드)
    right = [wtext("오또가 아이 목소리를 들을 수 있게", 17, weight="700", x=372, y=62, w=396),
             *[it for i, (ic, t_) in enumerate([("ear", "말할 차례에만 들어요 — 오또가 귀를 쫑긋할 때"),
                                                ("shield", "녹음은 폰 밖으로 나가지 않아요"),
                                                ("hand", "안 켜도 그림을 골라서 만들 수 있어요")])
               for it in (felt(ic, 32, 32, "felt-teal", 16, stitch=False, shadow=False, x=372, y=104 + i * 46,
                               children=[center([icon(ic, 16)], 32, 32)]),
                          wtext(t_, 13, x=414, y=111 + i * 46, w=354))]]
    S["④ 마이크 (3/3)"] = ob_frame(2, "마이크를 켜 주세요", "다음에 뜨는 휴대폰 창에서 '허용'을 눌러 주세요.", ob_art("귀 쫑긋"),
                              right, cta="마이크 켜기", cta2="나중에")

    # ⑦ 아이에게 건네기 — 진행 점 없음
    right = [wtext("준비 끝!", 28, weight="700", x=372, y=80, w=396),
             wtext("이제 아이에게 건네주세요. 오또가 목소리로 이어서 안내해요.", 15, "ink-soft", x=372, y=128, w=380),
             wtext("부모 설정은 언제든 왼쪽 위 자물쇠를 2초 누르고 태어난 해를 넣으면 열려요.", 12, "ink-soft",
                   x=372, y=196, w=380)]
    art = [felt("건네는 폰", 150, 96, "white", 20, x=95, y=78, texture=0.3,
                children=[center([icon("smartphone", 44, "ink"), icon("arrow-right", 24, "felt-coral")], 150, 96,
                                 direction="horizontal", gap=10)]),
           icon("sparkles", 28, "felt-mustard", x=252, y=64)]
    S["⑤ 아이에게 건네기"] = ob_frame(None, "설정이 끝났어요", "처음 설정은 이번 한 번만 해요.", art, right,
                                cta="아이 차례 시작", back=False)

    # 어디서든 · 부모 문 — PIN 대신 태어난 해 (Lingokids · ABCmouse)
    S["🔒 부모 문 → 태어난 해"] = year_gate("부모 영역", "아이 화면 왼쪽 위 자물쇠를 2초 누르면 여기로 와요.",
                                      note="틀려도 잠그지 않아요 · 3번 틀리면 잠시 기다려요",
                                      art=[felt("자물쇠", 110, 110, "felt-teal", 55, x=115, y=70,
                                                children=[center([icon("lock", 50)], 110, 110)])])
    return S



# ══ 모드별 파일로 나누기 — pen.dev 팀 공간 @clap-otto/otto-app 의 폴더 구조에 맞춘다 ══
import copy as _copy
import shutil as _shutil

LAST = {}


def screens_mode_extra(C):
    """오늘 이야기 · 같이 만들기도 세 상태(말해요 · 네 차례야 · 골라 줘)를 갖춘다."""
    S = {}
    cb, cbs = C["cb"], C["cb_s"]
    girl = (A + "hero_long_yellow_none.png", 160, 300, 60, "주인공")
    S["오늘 이야기 — 오또가 말해요"] = [*page_scene(A + "bg_playground.png", [girl]), *top_bar(C, "prog1"),
                                 narration("diary", "talk", "오늘 어디에 갔었어?")]
    S["오늘 이야기 — 말이 없으면 골라 줘"] = [
        *page_scene(A + "bg_playground.png", [girl], dim=True), *top_bar(C),
        ref(cb, 220, 70, {cb["_label"]: {"content": "미끄럼틀"}}), ref(cbs, 360, 70, {cbs["_label"]: {"content": "그네"}}),
        ref(cb, 500, 70, {cb["_label"]: {"content": "모래놀이"}}),
        narration("diary", "think", "미끄럼틀, 그네, 모래놀이 중에 뭐 했어?")]
    boy = (A + "hero_tied_blue_square.png", 150, 320, 60, "주인공")
    band = lambda q: {"type": "frame", "id": nid("band"), "name": "부모 띠 (부모가 읽고 물어봄)", "x": 150, "y": 196,
                      "width": 634, "height": 40, "cornerRadius": 20, "fill": "#3A2A20E6", "layout": "horizontal",
                      "alignItems": "center", "gap": 10, "padding": [0, 14], "children": [
                          felt("부모님", 78, 26, "felt-teal", 13, stitch=False, shadow=False,
                               children=[center([text("부모님", 12, "white", font=PARENT_FONT, weight="700")], 78, 26)]),
                          text(q, 14, "white", font=PARENT_FONT, weight="600")]}
    S["같이 만들기 — 네 차례야"] = [*page_scene(A + "bg_home.png", [boy]), *top_bar(C),
                             band("아이가 대답하는 동안 기다려 주세요 — 대신 말해 주지 않아요"),
                             narration("coop", "listen", "할머니랑 뭐 했는지 들려줘!")]
    S["같이 만들기 — 말이 없으면 골라 줘"] = [
        *page_scene(A + "bg_home.png", [boy], dim=True), *top_bar(C),
        ref(cb, 220, 56, {cb["_label"]: {"content": "요리"}}), ref(cbs, 360, 56, {cbs["_label"]: {"content": "산책"}}),
        ref(cb, 500, 56, {cb["_label"]: {"content": "그림 그리기"}}),
        band("\"같이 쿠키 만들었잖아\"처럼 힌트를 줘도 좋아요"),
        narration("coop", "think", "할머니랑 요리했어, 산책했어?")]
    return S


MODE_ORDER = {
    "01-story": ("otto_story", "이야기 만들기", [
        ("이야기 만들기 · 주인공 고르기", "매일 · 이야기", "캐릭터 커스텀은 여기서만 · 도감 3칸 + 새로 만들기"),
        ("이야기 만들기 — 오또가 말해요", "매일 · 이야기", "전체 화면 = 만들어지는 그림책 쪽 · 나레이션 칸 오또 얼굴(분홍) [자세: 말하기]"),
        ("이야기 만들기 — 네 차례야", "매일 · 이야기", "얼굴 테두리 청록 + 파동 · 목소리 막대 · 되받아 말하기 [자세: 귀 쫑긋]"),
        ("이야기 만들기 — 말이 없으면 골라 줘", "매일 · 이야기", "장면을 흐리게 하고 고르기 방울 3개 [자세: 갸웃]"),
        ("예외 · 마이크가 꺼져 있을 때", "예외", "같은 틀 · 고르기 방울로 끝까지 · 부모 영역에만 알림")]),
    "02-diary": ("otto_diary", "오늘 이야기", [
        ("오늘 이야기 — 오또가 말해요", "매일 · 오늘", "칸 테두리 파랑 + 「오늘 이야기」 · 오늘 있었던 일을 물음 [자세: 말하기]"),
        ("오늘 이야기 — 네 차례야", "매일 · 오늘", "같은 틀 · 아이가 오늘 있었던 일을 말함 [자세: 귀 쫑긋]"),
        ("오늘 이야기 — 말이 없으면 골라 줘", "매일 · 오늘", "오늘 갔던 곳에서 할 만한 일 세 가지 [자세: 갸웃]")]),
    "03-coop": ("otto_coop", "같이 만들기", [
        ("같이 만들기 — 부모 띠", "매일 · 같이", "칸 테두리 청록 + 부모 띠: 부모가 읽고 아이에게 물어봄 (지금 앱 ParentBand) [자세: 말하기]"),
        ("같이 만들기 — 네 차례야", "매일 · 같이", "부모 띠는 기다려 달라는 안내로 바뀜 [자세: 귀 쫑긋]"),
        ("같이 만들기 — 말이 없으면 골라 줘", "매일 · 같이", "부모 띠는 힌트 주는 법으로 바뀜 [자세: 갸웃]")]),
}


def grid_board(S, order, title, y=0, existing=None):
    existing = existing or {}
    COLS, CW, CH, TOP = 4, 860, 540, 150
    K = [text(title, 36, x=40, y=36),
         text("화면 아래 글: 흐름도 단계 · 참고한 앱과 규칙. 아이 화면의 글자는 설명용이며, 실제로는 오또가 소리로 말한다.", 14,
              "ink-soft", font=PARENT_FONT, x=42, y=90)]
    for i, (name, step, why) in enumerate(order):
        col, row = i % COLS, i // COLS
        x, yy = 40 + col * CW, TOP + row * CH
        fr = _copy.deepcopy(existing[name]) if name in existing else board(name, x, yy + 44, 800, 360, _copy.deepcopy(S[name]))
        fr["x"], fr["y"] = x, yy + 44
        fr["cornerRadius"], fr["effect"] = 20, SHADOW_SOFT
        K += [{"type": "frame", "id": nid("chip"), "name": "단계", "x": x, "y": yy, "height": 30, "width": 150,
               "cornerRadius": 15, "fill": c("felt-teal"), "layout": "horizontal", "justifyContent": "center",
               "alignItems": "center", "children": [text(step, 13, "white", font=PARENT_FONT, weight="700")]},
              text(name.split(" (")[0], 18, font=PARENT_FONT, weight="700", x=x + 164, y=yy + 3), fr,
              text(why, 13, "ink-soft", font=PARENT_FONT, x=x, y=yy + 416, width=800, textGrowth="fixed-width",
                   lineHeight=1.45)]
    rows = (len(order) + COLS - 1) // COLS
    return board(title, 0, y, 40 + COLS * CW, TOP + rows * CH + 40, K)


def _reid_all(n):
    """파일마다 id 가 겹치지 않게 — ref 가 가리키는 컴포넌트 id 는 그대로 둔다."""
    if isinstance(n, dict):
        if n.get("type") != "ref" and not n.get("reusable") and "id" in n:
            n["id"] = nid("x")
        for ch in n.get("children", []) or []:
            _reid_all(ch)


def _images(nodes):
    urls = set()

    def w(n):
        if isinstance(n, dict):
            f = n.get("fill")
            if isinstance(f, dict) and f.get("type") == "image":
                urls.add(f["url"])
            for v in n.values():
                w(v)
        elif isinstance(n, list):
            for v in n:
                w(v)
    w(nodes)
    return urls


FEATURES = [  # 흐름도 구간 = 파일 하나 (pen.dev 팀 공간 @clap-otto/otto-app 의 폴더)
    ("01-onboarding", "otto_01_onboarding", "처음 한 번 — 시작 · 로그인 · 동의 · 마이크 · 튜토리얼 · 기능 소개", range(0, 10)),
    ("02-home", "otto_02_home", "오또의 방 — 물건이 메뉴 · 걸어가서 묻기 · 오늘 만들 수 있는 책", range(10, 13)),
    ("03-story", "otto_03_story", "이야기 짓기 — 주인공 고르기 · 이야기 만들기 · 오늘 이야기 · 같이 만들기", range(13, 23)),
    ("04-book", "otto_04_book", "책 — 만드는 중 · 읽기 · 친구 평가 · 선물 · 책장 · 하루 한도", range(23, 29)),
    ("05-parent", "otto_05_parent", "부모 영역 — 부모 문 · 기록 · 계정 · 탈퇴", range(29, 34)),
    ("06-exceptions", "otto_06_exceptions", "예외 — 오프라인 · 마이크 꺼짐 · 로그인 풀림", range(34, 37)),
]


def write_split(children, variables):
    """흐름도 구간별 파일을 design/ 바로 아래에 만든다. 그림은 design/assets 를 같이 쓴다."""
    comp_board = next(b for b in children if b["name"].startswith("03 컴포넌트"))
    S = LAST["S"]
    plans = {"otto_00_design_system": [b for b in children if b["name"][:2] in ("01", "02", "03", "04", "05", "07")]}
    for folder, fname, title, idx in FEATURES:
        order = [SCREEN_ORDER_V2[i] for i in idx]
        plans[fname] = [_copy.deepcopy(comp_board), grid_board(S, order, title, y=comp_board["height"] + 120)]
    for fname, boards in plans.items():
        doc = {"version": "2.8", "variables": variables, "children": [strip_private(_copy.deepcopy(b)) for b in boards]}
        with open(os.path.join(HERE, "..", f"{fname}.pen"), "w", encoding="utf-8", newline=chr(10)) as fp:
            json.dump(doc, fp, ensure_ascii=False, indent=1)
        print(" ", fname + ".pen")

# ── 조립 ────────────────────────────────────────────
def strip_private(n):
    if isinstance(n, dict):
        for k in [k for k in n if k.startswith("_")]:
            del n[k]
        for ch in n.get("children", []):
            strip_private(ch)
    return n


def build():
    # 2026-09-28 부터 .pen 은 pen.dev 에서 직접 고친다. 다시 만들면 그 수정이 사라지므로 --force 없이는 멈춘다.
    import sys as _sys
    existing = [f for f in os.listdir(os.path.join(HERE, "..")) if f.endswith(".pen")]
    if existing and "--force" not in _sys.argv:
        print("이미 .pen 파일이 있습니다:", ", ".join(sorted(existing)))
        print("pen.dev 에서 고친 내용이 덮어써집니다. 정말 다시 만들려면: python design/tools/build_design_system.py --force")
        return
    children = []

    # 01 색
    sw = []
    for i, (name, hexv, use) in enumerate(COLORS):
        col, row = i % 8, i // 8
        x, y = 40 + col * 152, 110 + row * 190
        sw.append(felt(f"swatch/{name}", 112, 96, name, 24, stitch=name not in ("white",), x=x, y=y))
        sw.append(text(f"--{name}", 15, x=x, y=y + 106, font=PARENT_FONT, weight="700"))
        sw.append(text(hexv, 13, "ink-soft", x=x, y=y + 126, font=PARENT_FONT))
        sw.append(text(use, 12, "ink-soft", x=x, y=y + 144, font=PARENT_FONT))
    children.append(board("01 색 — 무대는 차분하게, 누르는 것만 선명하게", 0, 0, 1260, 520, [
        text("01 색", 36, x=40, y=32),
        label("윗줄 = 무대(차분) · 아랫줄 = 펠트 강조(누르는 것·브랜드). 청록(오또 후드)은 마이크에만 씁니다.", 190, 46),
    ] + sw))

    # 02 글자 · 크기 · 모서리 · 재료
    type_rows = [
        ("아이 · 큰 제목", "오또랑 이야기 짓자", 40, KID_FONT, "400"),
        ("아이 · 말풍선", "공룡이 어디로 갈까?", 22, KID_FONT, "400"),
        ("아이 · 카드 이름", "공룡 숲", 20, KID_FONT, "400"),
        ("부모 · 제목", "오늘의 기록", 22, PARENT_FONT, "700"),
        ("부모 · 본문", "아이가 직접 한 말을 그대로 모았어요.", 16, PARENT_FONT, "400"),
        ("부모 · 작은 글", "9월 28일 · 12분", 13, PARENT_FONT, "400"),
    ]
    tk = [text("02 글자 · 크기 · 재료", 36, x=40, y=32)]
    y = 110
    for role, sample, size, font, w in type_rows:
        tk.append(text(role, 13, "ink-soft", font=PARENT_FONT, x=40, y=y + 6))
        tk.append(text(f"{font} {size}", 12, "ink-soft", font=PARENT_FONT, x=40, y=y + 24))
        tk.append(text(sample, size, font=font, weight=w, x=200, y=y))
        y += max(size + 22, 46)
    # 터치 크기
    tx = 640
    tk.append(text("누르는 곳 크기", 20, x=tx, y=110, font=PARENT_FONT, weight="700"))
    for i, (nm, s, col, note) in enumerate([("touch-hero", 96, "felt-teal", "마이크 · 그림 카드"),
                                             ("touch-kid", 80, "felt-coral", "다음 · 좋아/다시"),
                                             ("touch-kid-min", 64, "wool-cream", "아이 최소"),
                                             ("touch-parent", 48, "white", "부모 최소")]):
        x = tx + sum([112, 96, 80, 0][:i]) + i * 16
        tk.append(felt(nm, s, s, col, s // 2, stitch=s >= 64, x=x, y=150 + (96 - s)))
        tk.append(text(f"{s}dp", 14, font=PARENT_FONT, weight="700", x=x, y=256))
        tk.append(text(note, 12, "ink-soft", font=PARENT_FONT, x=x, y=276))
    # 모서리
    tk.append(text("모서리", 20, x=tx, y=320, font=PARENT_FONT, weight="700"))
    for i, (nm, r) in enumerate([("radius-s", 12), ("radius-m", 20), ("radius-l", 28), ("radius-full", 999)]):
        tk.append(felt(nm, 72, 72, "wool-cream", min(r, 36), x=tx + i * 96, y=360))
        tk.append(text(f"{nm} {r if r < 999 else '원'}", 12, "ink-soft", font=PARENT_FONT, x=tx + i * 96, y=440))
    # 재료 해부
    tk.append(text("펠트 조각 해부", 20, x=40, y=470, font=PARENT_FONT, weight="700"))
    anat = [("① 단색", dict(texture=0, stitch=False, shadow=False)),
            ("② + 양모 결", dict(stitch=False, shadow=False)),
            ("③ + 바느질선", dict(shadow=False)),
            ("④ + 부드러운 그림자", dict())]
    for i, (nm, kw) in enumerate(anat):
        f = felt(nm, 120, 96, "felt-coral", 28, x=40 + i * 150, y=510, **kw)
        if "texture" in kw:
            f["children"] = f["children"][1:]
        tk.append(f)
        tk.append(text(nm, 13, "ink-soft", font=PARENT_FONT, x=40 + i * 150, y=616))
    tk.append(text("누르면: 96%로 꾹 눌리고 그림자가 얕아집니다 (120ms). 떼면 톡 튀어 돌아옵니다.", 14,
                   "ink-soft", font=PARENT_FONT, x=640, y=520))
    tk.append(felt("눌린 상태 예", 120, 96, "felt-coral", 28, x=660, y=550))
    tk[-1]["effect"] = SHADOW_PRESSED
    children.append(board("02 글자 · 크기 · 재료", 0, 580, 1180, 680, tk))

    # 03 컴포넌트 · 아이
    C = {}
    for st in STATE:
        C["halo_" + st] = halo(st)
        C["badge_" + st] = state_badge(st)
    C["voice"] = voice_pill()
    C["echo"] = echo_card()
    C["cb"], C["cb_s"] = choice_bubble(), choice_bubble(True)
    C["prog1"], C["prog2"], C["prog3"] = star_progress(1), star_progress(4), star_progress(6)
    C["wallet"] = book_wallet()
    C["home"] = home_btn()
    C["gate"] = parent_gate()
    C["hint"] = hotspot_hint()
    C["yes"], C["again"] = yes(), again()
    C["tool"], C["tool_s"] = tool(), tool(True)
    C["crayon"] = crayon("felt-coral")

    place = [  # (컴포넌트, x, y, 설명)
        ("halo_talk", 40, 150, "말하는 중 · 분홍"), ("halo_listen", 300, 150, "듣는 중 · 청록 — 이때만 녹음"),
        ("halo_think", 560, 150, "생각하는 중 · 겨자"),
        ("badge_talk", 840, 150, ""), ("badge_listen", 900, 150, ""), ("badge_think", 960, 150, "상태 표시 (작게)"),
        ("voice", 40, 260, "아이 목소리 — 들리는 만큼 출렁"), ("echo", 220, 256, "되받아 말하기 — 들은 말 확인"),
        ("home", 660, 260, "방으로"),
        ("gate", 850, 268, "부모 문"), ("hint", 930, 262, "반짝 (새것·추천만)"),
        ("cb", 40, 400, "고르기 방울"), ("cb_s", 200, 400, "고름"),
        ("prog1", 380, 400, "진행 · 1/6"), ("prog2", 380, 490, "진행 · 4/6"), ("prog3", 380, 580, "진행 · 완성 (별이 빛나고 숨 쉼)"),
        ("yes", 790, 400, "좋아"), ("again", 950, 400, "다시"),
        ("wallet", 560, 690, "오늘 만들 수 있는 책 (재화)"), ("tool", 40, 640, ""), ("tool_s", 120, 640, "미션 도구 · 고름"), ("crayon", 240, 648, "크레용"),
    ]
    kc = [text("03 컴포넌트 · 아이", 36, x=40, y=32),
          label("마이크 버튼이 없습니다. 오또의 몸짓 + 발밑 물결 색이 차례를 알립니다. 작은 글은 설명이며 화면에 넣지 않습니다.", 360, 46)]
    for key, x, y, note in place:
        comp = C[key]
        comp["x"], comp["y"] = x, y
        kc.append(comp)
        if note:
            kc.append(label(note, x, y + comp["height"] + 12, 12))
    children.append(board("03 컴포넌트 · 아이", 1340, 0, 1180, 780, kc))

    # 04 컴포넌트 · 부모
    P = {"btn": p_button(), "btn2": p_button(False), "row": p_row(), "pin": p_pinkey(), "card": p_card()}
    pc = [text("04 컴포넌트 · 부모", 36, x=40, y=32),
          label("같은 색과 모서리를 쓰되 펠트 질감을 빼고 차분하게. 글자는 Noto Sans KR, 누르는 곳은 48dp 이상.", 390, 46)]
    for key, x, y in [("btn", 40, 120), ("btn2", 260, 120), ("pin", 480, 118), ("row", 40, 210), ("card", 440, 200)]:
        P[key]["x"], P[key]["y"] = x, y
        pc.append(P[key])
    children.append(board("04 컴포넌트 · 부모", 1340, 860, 1180, 400, pc, color="wool-cream"))

    # 화면 1 · 오또의 방 — 버튼 없이 물건이 메뉴
    stripes, window, theater, sofa, shelf = room_objects()
    room = [*stripes,
            felt("바닥", 800, 100, "stage-wood", 0, stitch=False, shadow=False, x=0, y=262),
            {"type": "ellipse", "id": nid("rug"), "name": "러그", "x": 196, "y": 296, "width": 250, "height": 50,
             "fill": "#F48C9266"},
            window, theater, shelf, sofa,
            ref(C["halo_talk"], 200, 304),
            image(MASCOT, 214, 214, x=214, y=106, name="오또 (말 걸면 추천 물건을 가리킴)"),
            ref(C["hint"], 560, 60),
            ref(C["gate"], 12, 12)]
    children.append(board("화면 1 · 오또의 방 (800×360)", 0, 1340, 800, 360, room))

    # 화면 2~4 · 이야기 짓기 — 오또와 마주 보고
    def convo(state, title, x, y, extra, mode="story", scene=BG_SAMPLE, band=None):
        coop = mode == "coop"
        ph = 196 if coop else 256            # 같이 만들기는 아래에 부모 띠가 들어간다
        dim = [] if state != "choose" else [
            {"type": "rectangle", "id": nid("dim"), "name": "쪽 흐림", "x": 356, "y": 80, "width": 424, "height": ph,
             "cornerRadius": 28, "fill": "#FFF7ECB3"}]
        halo_key = {"choose": "halo_think"}.get(state, "halo_" + state)
        kids = [*backdrop(mode),
                felt("짓고 있는 책 한 쪽", 424, ph, "white", 28, stitch=False, x=356, y=80,
                     children=[image(scene, 400, ph - 24, x=12, y=12, radius=20, name="장면 (아이 말대로 채워짐)")]),
                *dim,
                ref(C[halo_key], 44, 244 if coop else 300),
                image(MASCOT, 240 if coop else 280, 240 if coop else 280, x=34 if coop else 24, y=34 if coop else 40,
                      name="오또 (마주 봄)"),
                ref(C["home"], 12, 12), ref(C["gate"], 76, 20),
                ref(C["prog2"], 236, 6),
                *extra]
        if band:
            kids.append(parent_band(band))
        children.append(board(title, x, y, 800, 360, kids))

    convo("talk", "화면 2 · 이야기 짓기 — 오또가 말해요", 900, 1340,
          [ref(C["badge_talk"], 250, 70)])
    convo("listen", "화면 3 · 이야기 짓기 — 네 차례야 (듣는 중)", 0, 1780,
          [ref(C["badge_listen"], 250, 70), ref(C["voice"], 380, 262),
           ref(C["echo"], 440, 190)])
    cb, cbs = C["cb"], C["cb_s"]
    convo("choose", "화면 4 · 이야기 짓기 — 말이 없으면 골라 줘", 900, 1780,
          [ref(C["badge_think"], 250, 70),
           ref(cb, 364, 124, {cb["_label"]: {"content": "공룡 숲"}}),
           ref(cbs, 504, 124, {cbs["_label"]: {"content": "바닷가"}}),
           ref(cb, 644, 124, {cb["_label"]: {"content": "눈 나라"}})])
    convo("listen", "화면 5 · 오늘 이야기 — 네 차례야 (아침 창가)", 0, 2220,
          [ref(C["badge_listen"], 250, 70), ref(C["voice"], 380, 262),
           ref(C["echo"], 440, 190, {C["echo"]["_text"]: {"content": "\"미끄럼틀 탔구나!\""}})],
          mode="diary", scene=A + "bg_playground.png")
    convo("talk", "화면 6 · 같이 만들기 — 부모 띠 (저녁 거실)", 900, 2220,
          [ref(C["badge_talk"], 234, 62)],
          mode="coop", scene=A + "bg_home.png", band="\"할머니 집에서 뭐가 제일 재미있었어?\" 하고 물어봐 주세요")

    children.append(flow_board())
    children.append(screens_board(children, C, P))
    pb = poses_board()
    pb["y"] = children[-1]["y"] + children[-1]["height"] + 100
    children.append(pb)

    doc = {"version": "2.8",
           "variables": {**{f"--{n}": {"type": "color", "value": v} for n, v, _ in COLORS},
                         **{f"--{n}": {"type": "number", "value": v} for n, v in NUMBERS}},
           "children": [strip_private(ch) for ch in children]}
    with open(OUT, "w", encoding="utf-8", newline="\n") as fp:
        json.dump(doc, fp, ensure_ascii=False, indent=1)
    print("wrote", os.path.normpath(OUT), f"({_seq[0]} ids)")
    write_split(doc["children"], doc["variables"])


if __name__ == "__main__":
    build()
