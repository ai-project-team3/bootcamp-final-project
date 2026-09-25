# -*- coding: utf-8 -*-
"""가변 낭독 목소리 비교 — gpt-4o-mini-tts · TypeCast · ElevenLabs (2026-09-25)

왜 재나
  고정 대사는 미리 구워 번들에 넣는다(결정). 남은 자리는 **가변 낭독** — 아이 말이 안에
  들어가서 미리 못 굽는 마스코트 대사와 책 자막이다. 폰 TTS 는 9/19 귀 판정 세 질문에서
  모두 「아니오」로 떨어졌고, 지금 후보는 `gpt-4o-mini-tts` 하나뿐이다. 9회차 멘토가
  TypeCast 를 권했고, ElevenLabs 를 같이 본다.

무엇을 재나 — 둘
  1. **첫 소리까지** — 요청을 보내고 오디오 첫 바이트가 올 때까지. 세 곳을 **같은 방법**으로 잰다.
     마스코트 대사는 판정 뒤에 나가므로 이게 리액션이 덮어야 하는 구멍에 그대로 더해진다.
  2. **귀 판정** — 9/19 에 폰 TTS 를 떨어뜨린 **같은 세 질문**. 목소리마다 A~G 가짜 이름을
     붙여 **누구 것인지 모르고** 듣는다(`listen.html`). 정답표는 따로 둔다.

⚠️ 공정성
  - 문장은 `bench_tts.py` 의 `LIVE` 세 줄 그대로 + 책 자막 한 줄. 아이 데이터는 보내지 않는다
  - ElevenLabs 기본 목소리 21개는 **전부 영어 원어민**이다. 한국어 원어민 목소리는 공유
    라이브러리에 많지만 **무료 계정은 API 로 못 쓴다**(HTTP 402, 09-25). 그래서 무료 키로
    실제로 쓸 수 있는 기본 목소리로 잰다 — ElevenLabs 에는 **불리한 비교**임을 알고 읽는다
  - TypeCast 는 목소리에 언어 칸이 없다(한 모델이 37개 언어). 한국어 이름 목소리를 골랐다
  - 매 요청이 새 연결이라 TLS 핸드셰이크가 세 곳 모두에 똑같이 들어간다
  - 값은 재지 않는다 — 한 권에 몇 글자를 읽는지가 기능 확정 뒤에 정해진다(09-25 조장)

    python -m eval.bench_tts_compare
"""
from __future__ import annotations

import json
import os
import random
import statistics
import time
import urllib.error
import urllib.request
from pathlib import Path

from eval.bench_tts import INSTR, LIVE, MODEL as OPENAI_MODEL, VOICE as OPENAI_VOICE, mp3_seconds
from eval.config import load_dotenv

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "eval" / "tts_out" / "compare"      # gitignored (eval/tts_out/)

BOOK = "지호는 공룡 나라에 갔어요. 커다란 공룡이 쿵쿵 걸어와서 인사했어요."
SENTENCES = LIVE + [BOOK]

# (vendor, voice id, model, what it is) — ids from each vendor's voice list on 09-25
CONFIGS = [
    ("openai", OPENAI_VOICE, OPENAI_MODEL, "현행 마스코트 (coral + 지시문)"),
    ("typecast", "tc_6699eb5749dfac016c29445c", "ssfm-v30", "Sua · 여자 어린이 · 대화"),
    ("typecast", "tc_69c1f8e4f8842d80fbe7fa4f", "ssfm-v30", "Woony · 남자 어린이 · 대화"),
    ("typecast", "tc_6a4f2130d153a5cac8e19996", "ssfm-v30", "Jiseon · 여자 중년 · 오디오북"),
    # ⚠️ Korean-native voices (shared library: Annie Lb7qkOn5hF8p7qfCDH8q, Jiyoung
    #    AW5wrnG1jVizOYY7R1Oo) return HTTP 402 on the free plan: "Free users cannot use
    #    library voices via the API" (09-25). So ElevenLabs is measured with what a free key
    #    can actually use — an English-native premade voice speaking Korean. Paid plan
    #    (Starter) is the only way to hear its Korean-native voices through the API.
    ("elevenlabs", "cgSgspJ2msm6clMCkdW9", "eleven_flash_v2_5", "Jessica · 기본 목소리(영어 원어민) · 밝음 (Flash)"),
    ("elevenlabs", "cgSgspJ2msm6clMCkdW9", "eleven_v3", "Jessica · 기본 목소리(영어 원어민) · 밝음 (v3)"),
]


def _timed(req: urllib.request.Request) -> tuple[float, float, bytes]:
    """(seconds to first byte, seconds to last byte, body). Same clock for every vendor."""
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=120) as r:
        first = r.read(1)
        ttfb = time.time() - t0
        body = first + r.read()
    return ttfb, time.time() - t0, body


def openai(text: str, voice: str, model: str) -> urllib.request.Request:
    body = {"model": model, "voice": voice, "input": text,
            "instructions": INSTR, "response_format": "mp3"}
    return urllib.request.Request(
        "https://api.openai.com/v1/audio/speech", json.dumps(body).encode(),
        {"Authorization": "Bearer " + os.environ["OPENAI_API_KEY"],
         "Content-Type": "application/json"})


def typecast(text: str, voice: str, model: str) -> urllib.request.Request:
    # No streaming endpoint is documented — the whole file comes back at once,
    # so first byte ~= last byte. That is a finding, not a harness artefact.
    body = {"voice_id": voice, "text": text, "model": model, "language": "kor",
            "prompt": {"emotion_type": "preset", "emotion_preset": "normal",
                       "emotion_intensity": 1.0},
            "output": {"audio_format": "mp3"}}
    return urllib.request.Request(
        "https://api.typecast.ai/v1/text-to-speech", json.dumps(body).encode(),
        {"X-API-KEY": os.environ["TYPECAST_API_KEY"], "Content-Type": "application/json"})


def elevenlabs(text: str, voice: str, model: str, lang: bool = True) -> urllib.request.Request:
    body = {"text": text, "model_id": model}
    if lang:
        body["language_code"] = "ko"
    return urllib.request.Request(
        f"https://api.elevenlabs.io/v1/text-to-speech/{voice}/stream?output_format=mp3_44100_128",
        json.dumps(body).encode(),
        {"xi-api-key": os.environ["ELEVENLABS_API_KEY"], "Content-Type": "application/json"})


BUILD = {"openai": openai, "typecast": typecast, "elevenlabs": elevenlabs}


def synth(vendor: str, voice: str, model: str, text: str) -> tuple[float, float, bytes]:
    try:
        return _timed(BUILD[vendor](text, voice, model))
    except urllib.error.HTTPError as e:
        detail = e.read()[:300].decode("utf-8", "replace")
        # Some ElevenLabs models reject language_code; retry once without it
        if vendor == "elevenlabs" and e.code in (400, 422) and "language" in detail.lower():
            return _timed(elevenlabs(text, voice, model, lang=False))
        raise RuntimeError(f"HTTP {e.code}: {detail}") from None


def listen_page(labels: list[str], key: dict) -> str:
    rows = []
    for si, s in enumerate(SENTENCES, 1):
        cells = "".join(
            f'<td><div class="lab">{lab}</div><audio controls preload="none" '
            f'src="{lab}_s{si}.mp3"></audio></td>' for lab in labels)
        rows.append(f'<tr><th>문장 {si}<div class="s">{s}</div></th>{cells}</tr>')
    answer = "".join(f"<li><b>{lab}</b> — {key[lab]}</li>" for lab in labels)
    return f"""<!doctype html><meta charset="utf-8"><title>목소리 블라인드 비교</title>
<style>body{{font:15px/1.5 system-ui,sans-serif;margin:24px;max-width:1400px}}
table{{border-collapse:collapse}}td,th{{border:1px solid #ddd;padding:8px;vertical-align:top}}
th{{text-align:left;width:260px}}.s{{font-weight:400;color:#555;font-size:13px}}
.lab{{font-weight:700;margin-bottom:4px}}audio{{width:170px}}
.q{{background:#f6f3ee;padding:12px 16px;border-radius:8px;margin-bottom:16px}}</style>
<h1>가변 낭독 — 블라인드 비교</h1>
<div class="q"><b>목소리마다 세 질문</b> (9/19 폰 TTS 를 떨어뜨린 그 질문)<ol>
<li>다섯 살이 끝까지 들을 만한가</li><li>사람 목소리로 들리는가</li>
<li>마스코트 캐릭터로 느껴지는가</li></ol>
<b>정답을 보기 전에</b> A~G 마다 예/아니오를 적어 두세요. 문장 1~3 은 마스코트 대사, 문장 4 는 책 자막입니다.</div>
<table>{''.join(rows)}</table>
<details style="margin-top:20px"><summary><b>정답 보기</b> (판정을 적은 뒤에)</summary><ul>{answer}</ul></details>"""


def main() -> None:
    load_dotenv()
    OUT.mkdir(parents=True, exist_ok=True)
    rng = random.Random(20260925)                 # fixed so the answer key is reproducible
    order = list(range(len(CONFIGS)))
    rng.shuffle(order)
    labels = [chr(ord("A") + i) for i in range(len(CONFIGS))]
    by_label = {labels[i]: CONFIGS[order[i]] for i in range(len(CONFIGS))}
    key = {lab: f"{c[0]} · {c[3]} · {c[2]}" for lab, c in by_label.items()}

    rows = []
    for si, text in enumerate(SENTENCES, 1):
        for lab in labels:                        # sentence-major: every voice sees the same moment
            vendor, voice, model, desc = by_label[lab]
            try:
                ttfb, total, blob = synth(vendor, voice, model, text)
                path = OUT / f"{lab}_s{si}.mp3"
                path.write_bytes(blob)
                secs = mp3_seconds(str(path))
                rows.append({"label": lab, "vendor": vendor, "model": model, "desc": desc,
                             "sentence": si, "chars": len(text), "ttfb": round(ttfb, 3),
                             "total": round(total, 3), "audio_sec": round(secs, 2),
                             "bytes": len(blob)})
                print(f"  s{si} {lab}  첫소리 {ttfb:5.2f}s  전체 {total:5.2f}s  "
                      f"길이 {secs:4.1f}s  {vendor}")
            except Exception as e:                # keep going; one vendor failing must not hide the rest
                rows.append({"label": lab, "vendor": vendor, "model": model, "desc": desc,
                             "sentence": si, "error": str(e)[:300]})
                print(f"  s{si} {lab}  ✗ {vendor} {model}: {str(e)[:160]}")

    print("\n" + "=" * 78)
    print(f"{'':3s}{'업체':11s}{'모델':20s}{'첫 소리 중앙':>12s}{'전체 중앙':>10s}  목소리")
    summary = {}
    for lab in labels:
        ok = [r for r in rows if r["label"] == lab and "error" not in r]
        vendor, voice, model, desc = by_label[lab]
        if ok:
            t1 = statistics.median(r["ttfb"] for r in ok)
            t2 = statistics.median(r["total"] for r in ok)
            summary[lab] = {"vendor": vendor, "model": model, "desc": desc,
                            "ttfb_median": round(t1, 3), "total_median": round(t2, 3), "n": len(ok)}
            print(f"{lab:3s}{vendor:11s}{model:20s}{t1:11.2f}s{t2:9.2f}s  {desc}")
        else:
            print(f"{lab:3s}{vendor:11s}{model:20s}{'실패':>12s}{'':>10s}  {desc}")
    print("=" * 78)

    (OUT / "_answer_key.json").write_text(json.dumps(key, ensure_ascii=False, indent=2), encoding="utf-8")
    (OUT / "results.json").write_text(json.dumps({"rows": rows, "summary": summary},
                                                 ensure_ascii=False, indent=2), encoding="utf-8")
    (OUT / "listen.html").write_text(listen_page(labels, key), encoding="utf-8")
    print(f"\n듣기: {OUT / 'listen.html'}  (정답표는 페이지 맨 아래 접힘 · {OUT / '_answer_key.json'})")


if __name__ == "__main__":
    main()
