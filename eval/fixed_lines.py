"""Every line the mascot can say that is written in the app — the list to bake into audio once.

Why (10-01): in server mode every mascot line went to TypeCast, fixed lines too, and the free
credit ran out during the team's testing (how many sessions that was is not measured — the
server logs characters per /tts call since 10-01 18:51; count those). Lines written in the app never
change, so they are baked once per voice and played from the app; only lines the server
writes (/turn ack · expand · question, /story captions) need TypeCast.

Static scan of android/.../demo/*.kt: string literals passed to the mascot's mouth —
say(…) · Question(…) (its text, easierText, easierAsk) · the question banks. Each line is
  fixed     — no template part: bake as is
  template  — has ${…} / $name: bake per value only if the values are a short known list
              (a theme name, a card); a child's word or name is never baked (it is the child's)

  py eval/fixed_lines.py   → eval/fixed_lines.tsv  +  a summary

A static scan misses lines built far from the call (a variable passed to say). The summary
says how many say(…) calls had no literal — those are checked by hand.
"""
import csv
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "android/app/src/main/java/com/example/finalproject_demo/demo"
OUT = Path(__file__).parent / "fixed_lines.tsv"

# a Kotlin string literal (no raw strings in these files' speech)
LIT = re.compile(r'"((?:[^"\\\n]|\\.)*)"')
HANGUL = re.compile(r"[가-힣]")
SPOKEN_CTX = {
    "say": re.compile(r"\bsay\(\s*$"),
    "question": re.compile(r"\bQuestion\(\s*(text\s*=\s*)?$"),
    "easier": re.compile(r"\beasier(Text|Ask)\s*=\s*$"),
    "line_of": re.compile(r"\b(line|ask|q|text|hint|prompt|easy|again)\s*=\s*$"),
}
NOT_SPOKEN = re.compile(r"\b(log|event|Log\.\w|require|check|error|TODO|println|mark)\(\s*$")
BANK_FILES = {"StoryBank.kt", "DiaryBank.kt", "CoopSteps.kt", "PictureDiary.kt", "Missions.kt"}


# on the same source line: a scripted child answer, a card, a book value — written, not spoken
NOT_SPEECH_LINE = re.compile(r"\b(Answer|Card|DemoBtn|CoopAsked|Pick|Text)\(|\b(probe|label|title|value|name)\s*=")


def context(text: str, start: int) -> str | None:
    before = text[max(0, start - 60):start]
    line_start = text.rfind("\n", 0, start) + 1
    if NOT_SPOKEN.search(before) or NOT_SPEECH_LINE.search(text[line_start:start]):
        return None
    for name, rx in SPOKEN_CTX.items():
        if rx.search(before):
            return name
    return None


def kind(s: str) -> str:
    return "template" if re.search(r"\$\{|\$[A-Za-z_]", s) else "fixed"


def main() -> None:
    rows, say_calls, say_without_literal = [], 0, 0
    for f in sorted(SRC.glob("*.kt")):
        text = f.read_text(encoding="utf-8")
        say_calls += len(re.findall(r"\bsay\(", text))
        say_without_literal += len(re.findall(r'\bsay\(\s*[^"\s)]', text))
        for m in LIT.finditer(text):
            s = m.group(1)
            if not HANGUL.search(s) or len(s) < 4 or "|" in s:     # "값|책 문장" pairs are book text
                continue
            ctx = context(text, m.start())
            if ctx is None and f.name in BANK_FILES and re.search(r"[?!.~]\s*$|[요야어자까]\s*[?!.~]?$", s):
                ctx = "bank"
            if ctx is None:
                continue
            line = text.count("\n", 0, m.start()) + 1
            rows.append((kind(s), ctx, f"{f.name}:{line}", s))
    seen, uniq = set(), []
    for r in rows:
        if r[3] not in seen:
            seen.add(r[3]); uniq.append(r)
    with OUT.open("w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh, delimiter="\t")
        w.writerow(["kind", "from", "where", "text"])
        w.writerows(uniq)
    by = Counter(r[0] for r in uniq)
    chars = {k: sum(len(r[3]) for r in uniq if r[0] == k) for k in by}
    print(f"lines {len(uniq)} · fixed {by['fixed']} ({chars.get('fixed', 0)} chars) · "
          f"template {by['template']} ({chars.get('template', 0)} chars)")
    print("by file:", dict(Counter(r[2].split(':')[0] for r in uniq)))
    print(f"say(…) calls {say_calls} · without a literal (built elsewhere — check by hand) {say_without_literal}")
    print(f"→ {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
