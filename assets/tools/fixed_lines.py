# -*- coding: utf-8 -*-
"""List the mascot's lines in the app so the fixed ones can be baked into the voice bundle.

Why: the plan is "fixed lines are baked ahead of time with one voice and shipped in the app;
only variable lines go through POST /tts at run time" (guidelines/3 §3-4-1). Before baking we
need to know which lines are fixed, which carry a variable, and which carry a person's name.

It reads the Kotlin source, not a running app, so it is a first cut to be checked by eye:
  - only string literals in speech calls are taken (say / askSay / Question / rungs / QVariant ...)
  - only sentence-shaped literals (ending in ? ! . ~) — keyword lists and labels are left out
  - child dummy answers (Answer), book captions (PageSpec / line) and logs are left out

Kinds
  fixed  no template at all            -> bake once
  var    has ${...} / $x, no name      -> bake per value if the set is small, else /tts
  name   has a person's name           -> cannot be baked, and must not reach /tts as a real name
                                          (guidelines/3 §3-4-1: /tts text goes to an outside vendor)

    py assets/tools/fixed_lines.py                 summary
    py assets/tools/fixed_lines.py --tsv out.tsv   every line with file:line
"""
from __future__ import annotations

import argparse
import io
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "android/app/src/main/java/com/example/finalproject_demo/demo"
FILES = ["Scenes.kt", "DiaryScenes.kt", "CoopScenes.kt", "DiaryBank.kt", "StoryBank.kt", "Director.kt", "Missions.kt"]

# calls whose string arguments the mascot says out loud
SPEECH = {"say", "askSay", "Question", "QVariant", "listOf", "coopAsk", "sayLine", "ask", "hint"}
# calls whose strings are never the mascot's voice
SKIP = {"log", "event", "mark", "Answer", "PageSpec", "line", "tail", "Card", "DemoBtn", "require",
        "check", "error", "sentence", "joinWith", "setDiarySlot", "childSays", "partnerSays"}

# a person's name inside a template — child, hero, friend, partner, companion
# (StoryBank shorthands: it.c = childName, it.f = friendName — StoryBank.kt:20-24)
TEMPLATE = re.compile(r"\$\{([^}]*)\}|\$([A-Za-z_][\w.]*)")
NAME_TOKEN = re.compile(r"(?:^|[^\w])(?:c|w|f|nc|child|who|names|pn|childName|friendName|friendCallName|"
                        r"heroName|companion)(?:$|[^\w])|\.name\b|\.c\b|\.f\b|\.pn\b")

HANGUL = re.compile(r"[가-힣]")
# something said out loud ends like a sentence
SPOKEN = re.compile(r"[?!.~]\s*$")
LIT = re.compile(r'"((?:[^"\\$]|\\.|\$\{[^}]*\}|\$[A-Za-z_][\w.]*|\$)*)"')
CALL = re.compile(r"([A-Za-z_]\w*)\s*\($")


def has_name(text: str) -> bool:
    return any(NAME_TOKEN.search(m.group(1) or m.group(2)) for m in TEMPLATE.finditer(text))


def scan():
    """Walk each file once, keeping a stack of open calls across lines, so a literal on its own
    line inside `listOf(` … `)` or `Question(` … `)` still knows which call it belongs to."""
    rows = []
    for name in FILES:
        path = SRC / name
        if not path.exists():
            continue
        stack: list[str] = []
        for no, raw in enumerate(io.open(path, encoding="utf-8"), 1):
            line = raw.rstrip("\n")
            if line.strip().startswith(("//", "*", "/*")):
                continue
            lits = list(LIT.finditer(line))
            pos = 0
            for m in lits + [None]:
                stop = m.start() if m else len(line)
                chunk = line[pos:stop]
                cut = chunk.find("//")
                if cut >= 0:
                    chunk = chunk[:cut]
                for i, ch in enumerate(chunk):
                    if ch == "(":
                        c = CALL.search(chunk[: i + 1])
                        stack.append(c.group(1) if c else "")
                    elif ch == ")" and stack:
                        stack.pop()
                if m is None or cut >= 0:
                    break
                pos = m.end()
                text = m.group(1)
                ctx = stack[-1] if stack else ""
                if not HANGUL.search(text) or ctx in SKIP or ctx not in SPEECH:
                    continue
                if not SPOKEN.search(text):   # keyword lists, place names, labels
                    continue
                if any(t in text for t in ("|", "→", "§")):   # answer pairs · log lines
                    continue
                if re.search(r"(probe|part|name|label|title)\s*=\s*$", line[: m.start()]):
                    continue
                kind = "name" if has_name(text) else ("var" if "$" in text else "fixed")
                rows.append((name, no, ctx, kind, text))
    return rows


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--tsv")
    args = ap.parse_args()
    rows = scan()
    uniq = {}
    for r in rows:
        uniq.setdefault(r[4], r)
    kinds = Counter(r[3] for r in uniq.values())
    out = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
    out.write("mascot lines (unique text): %d  fixed %d · var %d · name %d\n"
              % (len(uniq), kinds["fixed"], kinds["var"], kinds["name"]))
    by_file = Counter((r[0], r[3]) for r in uniq.values())
    for f in FILES:
        c = [by_file[(f, k)] for k in ("fixed", "var", "name")]
        if any(c):
            out.write("  %-15s fixed %3d · var %3d · name %3d\n" % (f, *c))
    if args.tsv:
        with io.open(args.tsv, "w", encoding="utf-8", newline="") as fw:
            fw.write("kind\tfile\tline\tcall\ttext\n")
            for r in sorted(uniq.values(), key=lambda r: ("fixed var name".split().index(r[3]), r[0], r[1])):
                fw.write("%s\t%s\t%d\t%s\t%s\n" % (r[3], r[0], r[1], r[2], r[4]))
        out.write("-> %s\n" % args.tsv)
    out.flush()


if __name__ == "__main__":
    main()
