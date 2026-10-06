# -*- coding: utf-8 -*-
"""AI-Hub 「자유대화 음성(소아남여)」 3~6세를 학습 · 확인 · 평가로 나눈다 — **아이 단위로** 나눈다.

같은 아이의 목소리가 학습과 평가에 같이 들어가면 평가가 부풀려진다. 그래서 화자(recorderId)를 먼저 나누고,
나이별로 고르게(3 · 4 · 5 · 6세 각각) 평가 화자를 떼어 둔다.

입력 (재배포 금지 데이터 — 깃에 올리지 않는다 · `.gitignore` 의 eval/audio/aihub/ 와 같은 취급)
  --raw     원천 zip   `1.AI챗봇_8_자유대화(소아남여)_TRAINING.zip`  (wav 약 15만 · 3~6세 273명)
  --labels  라벨 zip   `1.AI챗봇_라벨링_자유대화(소아남여)_TRAINING.zip` (json · 이름이 wav 와 같다)

출력 (--out 아래)
  train.jsonl · dev.jsonl   학습 · 학습 중 확인 — {"wav", "text", "age", "speaker"}
  eval/<화자>/*.wav + *.json  평가 — `eval/stt_child_bench.py` 가 그대로 읽는 모양(AIHUB_CHILD=<out>/eval)
  split.json                어느 화자가 어디에 갔는지 · 나눈 규칙 · 버린 수

정답 글자
  - 평가: `stt_child_bench.py` 와 **같은 방식**으로 태그를 통째로 뗀다(그 스크립트가 json 을 직접 읽는다)
  - 학습: 안에 글자가 든 태그 「(SP:버)」「(NO:지금)」가 있는 클립은 **뺀다**. 아이가 실제로 「버」라고 했는데
    정답에서 빼면 모델이 말을 빼먹는 법을 배운다. 빈 태그 「(SN:)」「(NO:)」만 떼고 쓴다

    py eval/whisper_ft/prepare.py --raw <원천 zip> --labels <라벨 zip> --out D:/whisper_ft/data
"""
from __future__ import annotations

import argparse
import json
import random
import re
import sys
import zipfile
from collections import Counter, defaultdict
from pathlib import Path

SPK = re.compile(r"_(\d{9,10}-\d+)_(\d)_")
TAG = re.compile(r"\(([A-Z]+):([^()]*)\)")
AGES = {3, 4, 5, 6}


def train_text(raw: str) -> str | None:
    """학습 정답 — 안에 글자가 든 태그가 있으면 None(뺀다), 빈 태그는 뗀다"""
    if any(m.group(2).strip() for m in TAG.finditer(raw)):
        return None
    t = re.sub(r"\s+", " ", TAG.sub(" ", raw)).strip()
    return t or None


class Augment:
    """2차 실험 — 학습 클립을 **앱에 들어오는 소리**에 가깝게 만든다. 평가 · 확인 클립은 손대지 않는다.

    --vad-trim  앱은 Silero VAD 가 말 끝 0.3초 뒤에 녹음을 끊는다(net/Voice.kt). AI-Hub 클립은 앞뒤 무음이
                제각각이다 → 같은 Silero(faster-whisper 에 든 것)로 말 구간을 찾아 앞은 0.5초 · 뒤는 0.3초만 남긴다.
                앞을 다 자르지 않는 것은 앱에서도 마이크를 켠 뒤 말을 시작하기까지 무음이 있어서다
    --noise     거실 소음 자료가 따로 없어 둘로 대신한다(받을 것이 없다):
                · 다른 아이의 말소리를 작게 겹친다 — 형제 · 친구가 옆에서 말하는 자리. 30% · SNR 15~25dB
                · 분홍 잡음 — 에어컨 · TV 웅웅거림. 20% · SNR 20~30dB
    """

    def __init__(self, vad_trim: bool, noise: bool, seed: int):
        self.vad_trim, self.noise = vad_trim, noise
        self.rng = random.Random(seed + 1)      # 1차와 같은 아이 · 같은 클립을 뽑게 나누기용 rng 와 따로 둔다
        self.pool: list[tuple[str, object]] = []  # (화자, 소리) — 겹칠 다른 아이 말소리
        self.count = Counter()

    def __bool__(self) -> bool:
        return self.vad_trim or self.noise

    def __call__(self, wav: bytes, speaker: str) -> bytes:
        import io
        import numpy as np
        import soundfile as sf
        x, sr = sf.read(io.BytesIO(wav), dtype="float32")
        if x.ndim > 1:
            x = x.mean(axis=1)
        if self.vad_trim:
            from faster_whisper.vad import get_speech_timestamps
            ts = get_speech_timestamps(x, sampling_rate=sr) if sr == 16000 else []
            if ts:
                a = max(0, ts[0]["start"] - int(0.5 * sr)); b = min(len(x), ts[-1]["end"] + int(0.3 * sr))
                if b - a < len(x):
                    self.count["vad-trim 잘림"] += 1
                x = x[a:b]
            else:
                self.count["vad-trim 말 구간 못 찾음(그대로)"] += 1
        if self.noise:
            power = float(np.mean(x ** 2)) + 1e-10
            others = [p for p in self.pool if p[0] != speaker]
            if others and self.rng.random() < 0.3:
                o = self.rng.choice(others)[1]
                o = np.resize(o, len(x)) if len(o) < len(x) else o[:len(x)]
                snr = self.rng.uniform(15, 25)
                x = x + o * np.sqrt(power / (float(np.mean(o ** 2)) + 1e-10) / 10 ** (snr / 10))
                self.count["다른 아이 말소리 겹침"] += 1
            if self.rng.random() < 0.2:
                w = np.fft.irfft(np.fft.rfft(np.random.default_rng(self.rng.randrange(1 << 30)).standard_normal(len(x)))
                                 / np.sqrt(np.arange(1, len(x) // 2 + 2)), n=len(x)).astype("float32")
                snr = self.rng.uniform(20, 30)
                x = x + w * np.sqrt(power / (float(np.mean(w ** 2)) + 1e-10) / 10 ** (snr / 10))
                self.count["분홍 잡음"] += 1
            if len(self.pool) < 400:
                self.pool.append((speaker, x.copy()))
            elif self.rng.random() < 0.05:
                self.pool[self.rng.randrange(len(self.pool))] = (speaker, x.copy())
            peak = float(np.max(np.abs(x))) if len(x) else 0.0
            if peak > 0.99:
                x = x / peak * 0.99
        buf = io.BytesIO()
        sf.write(buf, x, sr, format="WAV", subtype="PCM_16")
        return buf.getvalue()


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser()
    ap.add_argument("--raw", required=True)
    ap.add_argument("--labels", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--eval-frac", type=float, default=0.25, help="나이마다 평가로 뗄 화자 비율")
    ap.add_argument("--dev-frac", type=float, default=0.05, help="학습 화자 중 학습 확인용 비율")
    ap.add_argument("--eval-per-speaker", type=int, default=30, help="평가 화자당 클립 수")
    ap.add_argument("--train-max", type=int, default=40000, help="학습 클립 상한 — 하룻밤(3060 12GB)에 맞춘 값")
    ap.add_argument("--seed", type=int, default=20261006)
    ap.add_argument("--vad-trim", action="store_true", help="2차 — 학습 클립 앞뒤 무음을 앱처럼 자른다(Augment)")
    ap.add_argument("--noise", action="store_true", help="2차 — 학습 클립에 다른 아이 말소리 · 분홍 잡음을 섞는다(Augment)")
    a = ap.parse_args()
    rng = random.Random(a.seed)
    aug = Augment(a.vad_trim, a.noise, a.seed)
    out = Path(a.out)

    raw = zipfile.ZipFile(a.raw)
    wavs = defaultdict(list)                       # speaker -> [zip name]
    spk_age: dict[str, Counter] = defaultdict(Counter)
    for n in raw.namelist():
        if not n.endswith(".wav"):
            continue
        m = SPK.search(n)
        if not m or int(m.group(2)) not in AGES:
            continue
        wavs[m.group(1)].append(n)
        spk_age[m.group(1)][int(m.group(2))] += 1
    age_of = {s: c.most_common(1)[0][0] for s, c in spk_age.items()}
    print(f"원천: 3~6세 화자 {len(wavs)}명 · 클립 {sum(map(len, wavs.values()))}")

    stems = {Path(n).stem for v in wavs.values() for n in v}
    lab = zipfile.ZipFile(a.labels)
    label_of = {Path(n).stem: n for n in lab.namelist() if n.endswith(".json") and Path(n).stem in stems}
    print(f"라벨: 짝이 맞는 json {len(label_of)}")

    # 화자를 나이별로 나눈다 — 평가 · 확인 · 학습
    split: dict[str, str] = {}
    for age in sorted(AGES):
        ss = sorted(s for s in wavs if age_of[s] == age)
        rng.shuffle(ss)
        k_eval = max(1, round(len(ss) * a.eval_frac))
        k_dev = max(1, round((len(ss) - k_eval) * a.dev_frac))
        for i, s in enumerate(ss):
            split[s] = "eval" if i < k_eval else "dev" if i < k_eval + k_dev else "train"
    for part in ("train", "dev", "eval"):
        c = Counter(age_of[s] for s, p in split.items() if p == part)
        print(f"  {part}: 화자 {sum(c.values())} — " + " · ".join(f"{a_}세 {c[a_]}" for a_ in sorted(c)))

    def label(stem: str) -> dict | None:
        n = label_of.get(stem)
        return json.loads(lab.read(n).decode("utf-8-sig")) if n else None

    dropped = Counter()
    # 평가 — 화자당 N개 · 벤치가 읽는 모양으로 wav + json 그대로 푼다
    ev = out / "eval"
    n_eval = 0
    for s, p in sorted(split.items()):
        if p != "eval":
            continue
        names = sorted(wavs[s]); rng.shuffle(names)
        took = 0
        for n in names:
            if took >= a.eval_per_speaker:
                break
            d = label(Path(n).stem)
            if d is None:
                dropped["eval 라벨 없음"] += 1; continue
            dst = ev / s
            dst.mkdir(parents=True, exist_ok=True)
            (dst / Path(n).name).write_bytes(raw.read(n))
            (dst / (Path(n).stem + ".json")).write_text(json.dumps(d, ensure_ascii=False), encoding="utf-8")
            took += 1; n_eval += 1
    print(f"평가 클립 {n_eval} → {ev}")

    # 학습 · 확인 — 나이마다 같은 몫이 되게 뽑는다(3세가 5천, 6세가 7만이라 그대로 두면 6세만 배운다)
    wd = out / "wav"
    for part, cap in (("train", a.train_max), ("dev", max(500, a.train_max // 20))):
        by_age: dict[int, list[str]] = defaultdict(list)
        for s, p in split.items():
            if p == part:
                by_age[age_of[s]].extend(wavs[s])
        for v in by_age.values():
            rng.shuffle(v)
        per_age = cap // len(by_age)
        rows = []
        for age, names in sorted(by_age.items()):
            took = 0
            for n in names:
                if took >= per_age:
                    break
                d = label(Path(n).stem)
                if d is None:
                    dropped[f"{part} 라벨 없음"] += 1; continue
                u = d["발화정보"]
                if float(u.get("recrdTime") or 0) > 29.5:
                    dropped[f"{part} 30초 넘음"] += 1; continue
                text = train_text(u["stt"])
                if text is None:
                    dropped[f"{part} 글자 든 태그 · 빈 정답"] += 1; continue
                dst = wd / part / Path(n).name
                dst.parent.mkdir(parents=True, exist_ok=True)
                spk = SPK.search(n).group(1)
                data = raw.read(n)
                dst.write_bytes(aug(data, spk) if aug and part == "train" else data)
                rows.append({"wav": str(dst), "text": text, "age": age, "speaker": spk})
                took += 1
        rng.shuffle(rows)
        with open(out / f"{part}.jsonl", "w", encoding="utf-8") as f:
            for r in rows:
                f.write(json.dumps(r, ensure_ascii=False) + "\n")
        c = Counter(r["age"] for r in rows)
        print(f"{part} 클립 {len(rows)} — " + " · ".join(f"{k}세 {c[k]}" for k in sorted(c)))

    (out / "split.json").write_text(json.dumps({
        "seed": a.seed, "eval_frac": a.eval_frac, "dev_frac": a.dev_frac,
        "speakers": split, "age_of": age_of, "dropped": dropped,
        "augment": {"vad_trim": a.vad_trim, "noise": a.noise, "applied": aug.count},
    }, ensure_ascii=False, indent=1), encoding="utf-8")
    print("버림:", dict(dropped))
    if aug:
        print("2차 손질(학습 클립만):", dict(aug.count))


if __name__ == "__main__":
    main()
