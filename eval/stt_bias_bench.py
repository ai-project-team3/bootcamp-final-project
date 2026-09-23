# -*- coding: utf-8 -*-
"""받아쓰기 낱말 편향 비교 — whisper(자체 GPU) vs Grok(외부 API), 편향 있고 없고 (09-22).

왜 이 실험인가
  09-19 폰 온디바이스 STT가 프리셋 매칭 **65%** (합격선 95%)였다. 실패가 **어절 누락**
  (`공룡나라 갈래` → `갈래`)이라 자모 매칭으로 복구되지 않았다.
  ⚠️ **whisper 로는 프리셋 매칭을 잰 적이 한 번도 없다.** 65%는 폰 숫자다.

  프리셋은 미리 아는 낱말 목록이고, 두 엔진 다 그 목록을 밀어 넣는 방법이 있다.
    whisper → `initial_prompt`      Grok → `keyterm` (요청당 100개, 각 50자)
  ⚠️ **한쪽에만 걸면 기능 차이를 모델 차이로 읽는다.** 그래서 네 칸을 같은 음성으로 돌린다.

  | | 편향 없이 | 편향 걸고 |
  | whisper | whisper_plain | whisper_bias |
  | grok    | grok_plain    | grok_bias    |

네 묶음 — **프리셋만 재면 안 된다**
  preset   프리셋 3종 × 8                 편향이 겨냥하는 자리. 합격선 95%
  outside  프리셋 밖 「눈 나라 갈래」 × 5  편향이 **프리셋 쪽으로 끌고 가는지**
  short    응·몰라·싫어·또·아니 × 5        편향이 **짧은 답을 프리셋 낱말로 바꾸는지**
  silence  무음 × 5                        편향이 **없는 말을 지어내는지** (whisper 는 넣어 준 낱말을 뱉는 버릇이 알려져 있다)

  ⚠️ 편향이 preset 을 올리고 short/silence 를 깎으면 **이득이 아니다.** 아이가 「몰라」라고 했는데
     「공룡 나라」로 적히면 매칭률은 오르고 아이 말은 틀리게 남는다.

  30건 남짓이다. **5%p 차이는 동률로 읽는다** (멘토조사 §6 에서 미리 정했다).

⚠️ **성인 목소리 전용이다.** Grok 은 외부 업체라 **아이 음성(AI-Hub 포함)을 넣지 않는다** —
   `CLAUDE.md` 차별점 1. `--engines whisper_plain,whisper_bias` 로 whisper 만 돌린다.

    ⚠️ 전부 `.venv-diar` 로 돌린다 — 기본 파이썬 3.14 에는 CUDA 휠이 없다.
    PY=.venv-diar/Scripts/python.exe

    $PY -m eval.stt_bias_bench script          녹음 대본(manifest.csv)을 만든다
    $PY -m pip install sounddevice soundfile  (녹음 처음 한 번)
    $PY -m eval.stt_bias_bench record          마이크를 고르고 소리를 확인한 뒤, 대본을 한 줄씩 띄우고 녹음한다
    $PY -m eval.stt_bias_bench record --redo short_몰라_2   옆 사람 말이 섞인 클립만 다시
    ⚠️ 블루투스 마이크는 쓰지 않는다 — 켜는 순간 통화용 저음질로 떨어져 엔진 탓과 음질 탓이 안 갈린다
    $PY -m eval.stt_bias_bench run             네 칸을 돌리고 표를 낸다 (XAI_API_KEY 없으면 grok 은 건너뛴다)
    $PY -m eval.stt_bias_bench score RAW.csv   이미 돌린 결과로 표만 다시 낸다

녹음 파일은 `eval/audio/bias/` 에 쌓인다. **깃에 올리지 않는다** (`.gitignore` 의 `eval/audio/bias/`).
폰으로 녹음했다면 파일 이름을 `manifest.csv` 의 `clip_id` 로 맞춰 같은 폴더에 넣으면 된다 (wav · m4a · mp3).
"""
from __future__ import annotations

import argparse
import csv
import io
import os
import sys
import time
import unicodedata
from datetime import date
from pathlib import Path

try:
    from .config import load_dotenv
    from .corrupt import jamo_string, levenshtein, load_android_theme_labels
    from .stt_eval import percentile
except ImportError:
    from config import load_dotenv
    from corrupt import jamo_string, levenshtein, load_android_theme_labels
    from stt_eval import percentile

ROOT = Path(__file__).resolve().parent
AUDIO = ROOT / "audio" / "bias"
MANIFEST = AUDIO / "manifest.csv"
RAW = ROOT / "raw"

ENGINES = ("whisper_plain", "whisper_bias", "grok_plain", "grok_bias")
EXTS = (".wav", ".m4a", ".mp3", ".flac", ".ogg")

GROK_URL = "https://api.x.ai/v1/stt"
GROK_MODEL = os.getenv("XAI_STT_MODEL", "grok-voice-transcribe-2.0")
WHISPER_MODEL = os.getenv("WHISPER_MODEL", "large-v3")   # 09-23 확정 — 3~6세에서 turbo 대비 CER 절반 (results.md)

# 09-19 에 폰으로 읽은 문장과 같다 — 같은 문장이어야 65%와 견줄 수 있다 (`results.md` §2)
PRESET_LINES = {"우주": "우주로 가자", "바닷속": "바닷속 갈래", "공룡 나라": "공룡나라 갈래"}
SHORT = ["응", "몰라", "싫어", "또", "아니"]


# ── 대본 ────────────────────────────────────────────────────────────

def build_manifest(presets: list[str], repeat: int = 8) -> list[dict]:
    rows = []
    for p in presets:
        line = PRESET_LINES.get(p, f"{p} 갈래")
        for i in range(repeat):
            rows.append({"clip_id": f"preset_{len(rows):02d}", "group": "preset", "read": line, "ref": p})
    for i in range(5):
        rows.append({"clip_id": f"outside_{i:02d}", "group": "outside", "read": "눈 나라 갈래", "ref": "눈 나라"})
    for w in SHORT:
        for i in range(5):
            rows.append({"clip_id": f"short_{w}_{i}", "group": "short", "read": w, "ref": w})
    for i in range(5):
        rows.append({"clip_id": f"silence_{i:02d}", "group": "silence", "read": "(아무 말도 하지 않는다)", "ref": ""})
    # 같은 문장을 몰아 읽으면 3번째부터 말투가 굳는다. 섞어서 읽는다 (고정 시드 — 누가 만들어도 같은 순서)
    import random
    random.Random(20260922).shuffle(rows)
    return rows


def write_manifest(rows: list[dict]) -> None:
    AUDIO.mkdir(parents=True, exist_ok=True)
    with io.open(MANIFEST, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["clip_id", "group", "read", "ref"])
        w.writeheader()
        w.writerows(rows)


def read_manifest() -> list[dict]:
    if not MANIFEST.exists():
        sys.exit(f"대본이 없습니다 — 먼저 `python -m eval.stt_bias_bench script` ({MANIFEST})")
    with io.open(MANIFEST, encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))


def clip_path(clip_id: str) -> Path | None:
    for ext in EXTS:
        p = AUDIO / f"{clip_id}{ext}"
        if p.exists():
            return p
    return None


# ── 녹음 ────────────────────────────────────────────────────────────

def list_mics() -> None:
    import sounddevice as sd
    default = sd.default.device[0]
    for i, d in enumerate(sd.query_devices()):
        if d["max_input_channels"] > 0:
            print(f"{'*' if i == default else ' '} {i:3d}  {d['name']}")
    print("\n* 가 지금 기본 마이크. 다른 걸 쓰려면 record --device 번호")


def pick_mic(sd, rate: int) -> int:
    """마이크를 고르고 **실제로 소리가 들어오는지** 2초 들어 본다.

    번호를 미리 찾아 오게 하지 않는다. 이어폰을 꽂아도 Windows 가 기본 마이크를 안 바꾸는 일이
    흔해서, 고른 뒤에 소리 크기로 확인하는 것이 번호보다 확실하다.
    """
    import numpy as np
    apis = sd.query_hostapis()
    # MME 쪽만 보인다 — 같은 마이크가 MME · DirectSound · WASAPI · WDM-KS 로 네 번씩 나와 목록이 헷갈린다
    mics = [(i, d["name"]) for i, d in enumerate(sd.query_devices())
            if d["max_input_channels"] > 0 and apis[d["hostapi"]]["name"] == "MME" and "Mapper" not in d["name"]]
    default = sd.default.device[0]
    while True:
        print("\n마이크 목록 (* = 지금 Windows 기본)")
        for i, n in mics:
            print(f"  {'*' if i == default else ' '} {i:3d}  {n}")
        print("  ⚠️ 이어폰 단자에 꽂은 마이크는 보통 「High Definition Audio」 쪽이다. 기본(*)이 이어폰이 아닐 수 있다")
        raw = input("쓸 마이크 번호 (a = 전부 비교해서 골라 주기, 그냥 Enter = *): ").strip().lower()
        if raw == "a":
            print("\n마이크마다 조용히 1초 → 말하기 2초를 잰다. 안내가 뜨면 「공룡나라 갈래」를 계속 반복해 주세요")
            scores = []
            for i, n in mics:
                try:
                    input(f"  [{n}] 1초 조용히 — Enter")
                    q = sd.rec(int(rate), samplerate=rate, channels=1, dtype="int16", device=i); sd.wait()
                    input(f"  [{n}] 2초 말하기 — Enter")
                    t = sd.rec(int(2 * rate), samplerate=rate, channels=1, dtype="int16", device=i); sd.wait()
                except Exception as e:
                    print(f"    열리지 않음 ({type(e).__name__})")
                    continue
                ratio = max(frame_rms(t, rate)) / max(float(np.median(frame_rms(q, rate))), 1e-6)
                scores.append((ratio, i, n))
                print(f"    → {ratio:.0f}배")
            if not scores:
                continue
            best = max(scores)
            print(f"\n  가장 잘 들리는 마이크: {best[2]} ({best[0]:.0f}배)")
            raw = str(best[1])
        dev = int(raw) if raw.isdigit() else default
        # ⚠️ 절대 크기로 보지 않는다. 09-22 첫 녹음은 강의실 바닥 소음만으로 peak 1,800 이 나와
        #    "1000 미만이면 경고"가 한 번도 안 떴고, 말소리가 소음의 2~6배뿐인 녹음 59개가 쌓였다.
        #    whisper 는 그걸 「감사합니다」「아멘」으로 받아썼다. **소음 대비 비율**로 본다
        try:
            input("1초 동안 조용히 있어 주세요 — Enter")
            quiet = sd.rec(int(1 * rate), samplerate=rate, channels=1, dtype="int16", device=dev); sd.wait()
            input("이제 2초 동안 「공룡나라 갈래」 하고 평소 크기로 말해 주세요 — Enter")
            talk = sd.rec(int(2 * rate), samplerate=rate, channels=1, dtype="int16", device=dev); sd.wait()
        except Exception as e:
            print(f"  이 마이크는 열리지 않습니다 ({type(e).__name__}). 다른 번호를 고르세요.")
            continue
        # 중앙값 — 최댓값으로 재면 딸깍 한 번에 바닥이 올라가 비율이 실제보다 낮게 나온다
        floor = float(np.median(frame_rms(quiet, rate)))
        ratio = max(frame_rms(talk, rate)) / max(floor, 1e-6)
        bar = "█" * min(30, int(ratio))
        print(f"  말소리 / 소음 |{bar:<30}| {ratio:.0f}배  (기준 {SNR_MIN:.0f}배 이상)")
        if ratio < SNR_MIN:
            print("  ⚠️ 말소리가 소음에 묻힙니다. 이 상태로 녹음하면 엔진이 아니라 녹음 품질을 재게 됩니다.")
            print("     → 이어폰 마이크 번호를 다시 고르거나, 마이크를 입 가까이, 더 조용한 자리로")
            print("     → Windows 소리 설정 → 입력 장치 → 볼륨을 올려도 됩니다")
            if input("  그래도 이 마이크로 할까요? (y = 예, Enter = 다시 고르기): ").strip().lower() != "y":
                continue
        elif input("이 마이크로 할까요? (Enter = 예, n = 다시 고르기): ").strip().lower() == "n":
            continue
        return dev, floor


SNR_MIN = 10.0      # 말소리가 바닥 소음의 10배(20dB) 안 되면 받아쓰기가 무너진다 — 09-22 첫 녹음이 2~6배였다


def frame_rms(audio, rate: int, win: float = 0.1) -> list[float]:
    import numpy as np
    a = np.asarray(audio, dtype=np.float64).ravel() / 32768.0
    n = max(1, int(rate * win))
    return [float(np.sqrt(np.mean(a[i:i + n] ** 2))) for i in range(0, max(1, len(a) - n + 1), n)] or [0.0]


def record(seconds: float, rate: int = 16000, device: int | None = None, redo: list[str] | None = None) -> None:
    try:
        import sounddevice as sd
        import soundfile as sf
    except ImportError:
        sys.exit("녹음에는 `pip install sounddevice soundfile` 이 필요합니다.\n"
                 "폰으로 녹음했다면 이 단계는 건너뛰고 파일 이름만 clip_id 로 맞추세요.")
    rows = read_manifest()
    if redo:
        # 옆 사람 말이 섞인 클립만 다시 — 잘못 읽은 것은 다시 하지 않는다 (그것도 실제 조건이다)
        unknown = set(redo) - {r["clip_id"] for r in rows}
        if unknown:
            sys.exit(f"대본에 없는 clip_id: {sorted(unknown)}")
        for cid in redo:
            p = clip_path(cid)
            if p:
                p.unlink()
        todo = [r for r in rows if r["clip_id"] in set(redo)]
    else:
        todo = [r for r in rows if clip_path(r["clip_id"]) is None]
    if device is None:
        device, floor = pick_mic(sd, rate)
    else:
        input("바닥 소음을 잽니다. 1초 동안 조용히 — Enter")
        q = sd.rec(int(1 * rate), samplerate=rate, channels=1, dtype="int16", device=device); sd.wait()
        import numpy as np
        floor = float(np.median(frame_rms(q, rate)))
    name = sd.query_devices(device)["name"]
    print(f"\n마이크: {name}")
    print(f"남은 클립 {len(todo)}/{len(rows)} · 한 클립 {seconds}초 · Enter 로 시작, q 로 멈춤")
    print("클립마다 바로 검사한다 — 말이 소음에 묻히거나 무음에 소리가 들어가면 그 자리에서 다시 녹음한다\n")
    n = 0
    while n < len(todo):
        r = todo[n]
        if input(f"[{n + 1}/{len(todo)}] 「{r['read']}」  ▶ Enter ").strip().lower() == "q":
            break
        audio = sd.rec(int(seconds * rate), samplerate=rate, channels=1, dtype="int16", device=device)
        sd.wait()
        ratio = max(frame_rms(audio, rate)) / max(floor, 1e-6)
        # ⚠️ 잘못 읽은 것은 다시 하지 않는다(그것도 실제 조건이다). **소리가 안 담긴 것**만 다시 한다 —
        #    그건 엔진이 아니라 녹음이 틀린 것이라 채점에 넣으면 비교가 오염된다
        problem = None
        if r["group"] == "silence" and ratio > 3:
            problem = f"무음이어야 하는데 소리가 들어갔다 ({ratio:.0f}배) — 옆 사람 말이나 소음"
        elif r["group"] != "silence" and ratio < SNR_MIN:
            problem = f"말소리가 작다 ({ratio:.0f}배 · 기준 {SNR_MIN:.0f}배) — 2.5초 안에, 조금 더 크게"
        if problem:
            print(f"      ⚠️ {problem}")
            if input("      다시 녹음할까요? (Enter = 다시, k = 그대로 둔다): ").strip().lower() != "k":
                continue
        sf.write(AUDIO / f"{r['clip_id']}.wav", audio, rate)
        print(f"      └ {r['clip_id']}  ({ratio:.0f}배)")
        n += 1
    print(f"\n→ {AUDIO}")


# ── 엔진 ────────────────────────────────────────────────────────────

def bias_prompt(presets: list[str]) -> str:
    """whisper 의 initial_prompt. **Grok keyterm 과 같은 낱말**만 넣는다 — 다른 걸 넣으면 비교가 깨진다"""
    return ", ".join(presets)


class Whisper:
    def __init__(self, model: str | None = None):
        # ⚠️ `.venv-diar` 로 돌린다 (기본 파이썬 3.14 에는 CUDA 휠이 없다). 그 안에서도 cuBLAS 를
        #    PATH 에 올려야 한다 — 안 하면 "cublas64_12.dll is not found" (`bench_coresident.py:29`)
        try:
            from .bench_coresident import add_cuda_dlls
        except ImportError:
            from bench_coresident import add_cuda_dlls
        if not add_cuda_dlls():
            # `.venv-diar` 에는 nvidia-* 휠이 없고 cuBLAS 가 **torch/lib 안에** 들어 있다. 다른 벤치는
            # pyannote 가 torch 를 먼저 불러서 우연히 돌았다 — 여기서는 그 경로를 명시적으로 올린다
            import importlib.util
            spec = importlib.util.find_spec("torch")
            if spec and spec.origin:
                lib = str(Path(spec.origin).parent / "lib")
                os.add_dll_directory(lib)
                os.environ["PATH"] = lib + os.pathsep + os.environ.get("PATH", "")
        from faster_whisper import WhisperModel
        # 다른 벤치와 같은 설정이다 (`bench_concurrent.py:113`)
        self.name = model or WHISPER_MODEL
        self.m = WhisperModel(self.name, device="cuda", compute_type="int8_float16")

    def __call__(self, path: Path, prompt: str | None) -> dict:
        t0 = time.perf_counter()
        # vad_filter=False — 앱에서는 폰이 VAD 로 이미 잘라 보낸다. 여기서 또 자르면 조건이 달라진다
        segs, _ = self.m.transcribe(str(path), language="ko", beam_size=5,
                                    initial_prompt=prompt, vad_filter=False)
        text = "".join(s.text for s in segs).strip()
        return {"hyp": text, "latency_ms": (time.perf_counter() - t0) * 1000}


class Grok:
    def __init__(self):
        self.key = os.getenv("XAI_API_KEY")
        if not self.key:
            raise RuntimeError("XAI_API_KEY 없음")
        self.words_seen: set[str] = set()

    def __call__(self, path: Path, terms: list[str] | None) -> dict:
        import requests
        # keyterm 은 **같은 이름의 필드를 여러 번** 보낸다 — 쉼표 목록이 아니다 (docs.x.ai speech-to-text)
        data = [("model", GROK_MODEL), ("language", "ko"), ("format", "true")]
        data += [("keyterm", t) for t in (terms or [])]
        t0 = time.perf_counter()
        with open(path, "rb") as f:
            r = requests.post(GROK_URL, headers={"Authorization": f"Bearer {self.key}"},
                              data=data, files={"file": (path.name, f)}, timeout=60)
        ms = (time.perf_counter() - t0) * 1000
        r.raise_for_status()
        body = r.json()
        # 발표 페이지는 단어별 확신도를 준다고 했는데 REST 문서에는 그 필드가 없다 — 실제로 오는지 기록한다
        for w in body.get("words") or []:
            self.words_seen.update(w.keys())
        return {"hyp": (body.get("text") or "").strip(), "latency_ms": ms}


# ── 채점 ────────────────────────────────────────────────────────────

def norm(text: str) -> str:
    text = unicodedata.normalize("NFC", text or "")
    return "".join(ch for ch in text if ch.isalnum())


def preset_distance(hyp: str, preset: str) -> int:
    """hyp 안에서 preset 과 **가장 닮은 구간**까지의 자모 거리.

    ⚠️ `corrupt.jamo_nearest_match` 를 쓰지 않는 이유 — 그 함수는 **늘 하나를 고른다.**
    `공룡나라 갈래` 가 `갈래` 로 잘려도 셋 중 하나가 뽑혀 **3분의 1 은 맞은 것으로 센다.**
    09-19 의 실패 모양이 바로 그 어절 누락이었다. 여기서는 구간이 **실제로 있어야** 맞은 것이다.
    """
    h, p = jamo_string(norm(hyp)), jamo_string(norm(preset))
    if not h:
        return len(p)
    best = len(p)
    for width in range(max(1, len(p) - 2), len(p) + 3):
        for i in range(0, max(1, len(h) - width + 1)):
            best = min(best, levenshtein(h[i:i + width], p))
    return best


def matched_preset(hyp: str, presets: list[str]) -> str | None:
    """hyp 에 **실제로 들어 있는** 프리셋. 자모 25% 까지는 같은 낱말로 본다 (`곰뇽 나라` → `공룡 나라`)."""
    scored = []
    for p in presets:
        d = preset_distance(hyp, p)
        if d <= max(1, round(len(jamo_string(norm(p))) * 0.25)):
            scored.append((d, p))
    return min(scored)[1] if scored else None


def judge(row: dict, presets: list[str]) -> dict:
    """묶음마다 **맞은 것의 뜻이 다르다.**"""
    g, hyp, ref = row["group"], row["hyp"], row["ref"]
    got = matched_preset(hyp, presets)
    if g == "preset":
        return {"ok": got == ref, "pulled": False}
    if g == "outside":
        # 「눈」 이 남아 있고 **어느 프리셋으로도 끌려가지 않아야** 맞다
        return {"ok": got is None and "눈" in norm(hyp), "pulled": got is not None}
    if g == "short":
        return {"ok": norm(hyp) == norm(ref), "pulled": got is not None}
    if g == "silence":
        return {"ok": not norm(hyp), "pulled": got is not None}
    raise ValueError(g)


GROUP_ORDER = ["preset", "outside", "short", "silence"]
GROUP_LABEL = {"preset": "프리셋 매칭", "outside": "프리셋 밖 보존", "short": "짧은 답 일치", "silence": "무음 → 빈칸"}
BAR = {"preset": 0.95}      # 합격선이 있는 것은 프리셋 하나다 (guidelines/6 §5). 나머지는 **나빠지지 않았나**를 본다


def summarize(rows: list[dict], presets: list[str]) -> str:
    out = []
    engines = [e for e in ENGINES if any(r["engine"] == e for r in rows)]

    def cell(e, g):
        rs = [r for r in rows if r["engine"] == e and r["group"] == g and not r.get("error")]
        if not rs:
            return "-"
        ok = sum(judge(r, presets)["ok"] for r in rs)
        return f"{ok}/{len(rs)} ({ok / len(rs) * 100:.0f}%)"

    out.append("| 묶음 | " + " | ".join(engines) + " | 합격선 |")
    out.append("|---|" + "---:|" * len(engines) + "---|")
    for g in GROUP_ORDER:
        bar = f"≥ {BAR[g] * 100:.0f}%" if g in BAR else "나빠지지 않을 것"
        out.append(f"| {GROUP_LABEL[g]} | " + " | ".join(cell(e, g) for e in engines) + f" | {bar} |")

    # 편향의 부작용 — 프리셋이 아닌 자리에서 프리셋 낱말이 튀어나온 횟수
    def pulled(e):
        rs = [r for r in rows if r["engine"] == e and r["group"] != "preset" and not r.get("error")]
        return str(sum(judge(r, presets)["pulled"] for r in rs)) if rs else "-"
    out.append("| ⚠️ 프리셋 아닌 말이 프리셋으로 | " + " | ".join(pulled(e) for e in engines) + " | 0 |")

    def lat(e, q):
        v = [float(r["latency_ms"]) for r in rows if r["engine"] == e and r.get("latency_ms") and not r.get("error")]
        p = percentile(v, q)
        return f"{p:.0f}ms" if p is not None else "-"
    out.append("| 지연 p50 | " + " | ".join(lat(e, 0.5) for e in engines) + " | |")
    out.append("| 지연 p95 | " + " | ".join(lat(e, 0.95) for e in engines) + " | |")

    errors = [r for r in rows if r.get("error")]
    if errors:
        out.append(f"\n⚠️ 실패한 호출 {len(errors)}건 — 표에서 뺐다. 첫 줄: {errors[0]['engine']} {errors[0]['clip_id']}: {errors[0]['error']}")

    out.append("\n**틀린 것** (묶음 · 엔진 · 읽은 말 → 받아쓴 말)")
    for r in rows:
        if not r.get("error") and not judge(r, presets)["ok"]:
            out.append(f"- {r['group']} · {r['engine']} · 「{r['read']}」 → 「{r['hyp']}」")
    return "\n".join(out)


FIELDS = ["clip_id", "group", "read", "ref", "engine", "hyp", "latency_ms", "error"]


def run(engines: list[str]) -> Path:
    load_dotenv()
    presets = load_android_theme_labels()
    rows = read_manifest()
    missing = [r["clip_id"] for r in rows if clip_path(r["clip_id"]) is None]
    if len(missing) > len(rows) // 10:
        sys.exit(f"녹음이 없는 클립 {len(missing)}개 — 예: {missing[:3]}. `record` 를 먼저 돌리세요.")
    if missing:
        # 한 파일로 이어 녹음하면 줄을 건너뛰는 일이 생긴다 (09-22 폰 녹음 — 54줄 중 2줄). 1할까지는 빼고 잰다.
        # ⚠️ 표 밑에 몇 개를 뺐는지 반드시 남긴다 — 분모가 바뀐 것을 모르고 읽으면 안 된다
        print(f"⚠️ 녹음이 없는 클립 {len(missing)}개를 빼고 잰다: {missing}")
        rows = [r for r in rows if r["clip_id"] not in set(missing)]

    runners = {}
    if any(e.startswith("whisper") for e in engines):
        runners["whisper"] = Whisper()
    if any(e.startswith("grok") for e in engines):
        try:
            runners["grok"] = Grok()
        except RuntimeError as e:
            print(f"⚠️ grok 을 건너뛴다 — {e}. `.env` 에 XAI_API_KEY 를 넣으면 돈다 (각자 발급 · 채팅에 붙이지 않는다)")
            engines = [e for e in engines if not e.startswith("grok")]

    RAW.mkdir(exist_ok=True)
    out_path = RAW / f"stt_bias_{date.today():%Y%m%d}.csv"
    results = []
    with io.open(out_path, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=FIELDS)
        w.writeheader()
        for e in engines:
            kind, mode = e.split("_")
            biased = mode == "bias"
            for n, r in enumerate(rows, 1):
                path = clip_path(r["clip_id"])
                arg = (bias_prompt(presets) if kind == "whisper" else presets) if biased else None
                rec = {**r, "engine": e, "hyp": "", "latency_ms": "", "error": ""}
                try:
                    rec.update(runners[kind](path, arg))
                except Exception as ex:           # 한 건이 실패해도 나머지는 잰다 — 실패는 표에 따로 적는다
                    rec["error"] = f"{type(ex).__name__}: {ex}"[:200]
                w.writerow(rec); f.flush()
                results.append(rec)
                print(f"\r{e:14s} {n}/{len(rows)}", end="")
            print()

    print()
    print(summarize(results, presets))
    if "grok" in runners and runners["grok"].words_seen:
        print(f"\nGrok words[] 에 실제로 온 필드: {sorted(runners['grok'].words_seen)}")
    print(f"\n→ {out_path}")
    return out_path


def score(path: Path) -> None:
    with io.open(path, encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))
    print(summarize(rows, load_android_theme_labels()))


def main() -> None:
    # 윈도 콘솔(cp949)에서 한글·「」 가 깨지거나 UnicodeEncodeError 로 죽는다
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("script")
    rp = sub.add_parser("record")
    rp.add_argument("--seconds", type=float, default=2.5)
    rp.add_argument("--device", type=int, default=None, help="마이크 번호 (mics 로 확인)")
    rp.add_argument("--redo", nargs="+", default=None, help="다시 녹음할 clip_id 들")
    sub.add_parser("mics")
    up = sub.add_parser("run")
    up.add_argument("--engines", default=",".join(ENGINES))
    sp = sub.add_parser("score")
    sp.add_argument("raw", type=Path)
    a = ap.parse_args()

    if a.cmd == "script":
        if MANIFEST.exists():
            sys.exit(f"대본이 이미 있습니다 — 녹음이 그 순서에 맞춰져 있을 수 있어 덮어쓰지 않습니다 ({MANIFEST})")
        rows = build_manifest(load_android_theme_labels())
        write_manifest(rows)
        counts = {g: sum(r["group"] == g for r in rows) for g in GROUP_ORDER}
        print(f"대본 {len(rows)}줄 → {MANIFEST}\n  " + " · ".join(f"{g} {n}" for g, n in counts.items()))
        print("  ⚠️ 09-19 과 같은 조건으로 — 성인 · 조용한 방 · 평소 말투. 한 번 읽고 다음으로 (다시 읽지 않는다)")
    elif a.cmd == "mics":
        list_mics()
    elif a.cmd == "record":
        record(a.seconds, device=a.device, redo=a.redo)
    elif a.cmd == "run":
        engines = [e.strip() for e in a.engines.split(",") if e.strip()]
        bad = set(engines) - set(ENGINES)
        if bad:
            sys.exit(f"모르는 엔진: {bad} — {ENGINES}")
        run(engines)
    else:
        score(a.raw)


if __name__ == "__main__":
    main()
