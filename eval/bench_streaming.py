"""판정 응답의 각 필드가 몇 초에 도착하는지 잰다.

「한 단어씩 흘려보내며 다음 단계를 겹쳐 돌린다」가 우리 설계에 쓸모가 있는지
보려면, 먼저 **뒷단계가 필요로 하는 값이 언제 오는지**를 알아야 한다.

마스코트 대사(§3)는 판정(§2)의 `next_slot`·`next_reason`을 재료로 받는다.
그 둘이 응답 앞쪽에 오면 대사 생성을 일찍 시작할 수 있고, 뒤쪽에 오면
사실상 응답 전체를 기다려야 한다. 스키마의 **필드 순서**가 지연을 정한다.

    python -m eval.bench_streaming
"""
from __future__ import annotations

import io
import json
import time
from pathlib import Path

from eval.config import load_dotenv, resolve_model

ROOT = Path(__file__).resolve().parent
MODEL = "gpt-5.6-luna"
# 뒷단계가 실제로 기다리는 필드
NEEDED = ["next_slot", "next_reason", "slot_1", "value_1", "story_ready"]


def main() -> None:
    load_dotenv()
    from eval.providers.openai_adapter import OpenAIAdapter

    schema = json.loads((ROOT / "judge_schema.json").read_text(encoding="utf-8"))
    prompt = (ROOT / "judge_prompt.md").read_text(encoding="utf-8")
    fx = json.loads(io.open(ROOT / "fixtures_judge.jsonl", encoding="utf-8").readline())

    order = list(schema["properties"])
    print("스키마 필드 순서:")
    for i, k in enumerate(order, 1):
        mark = "  ←뒷단계가 기다림" if k in NEEDED else ""
        print("  %2d. %s%s" % (i, k, mark))

    cfg = resolve_model(MODEL)
    a = OpenAIAdapter()
    user = json.dumps(fx, ensure_ascii=False)

    print("\n스트리밍 시작...")
    started = time.time()
    r = a.stream_judge(
        model=cfg["api_model"], system_prompt=prompt, user_prompt=user,
        schema=schema, max_output_tokens=512,
        reasoning_effort=cfg.get("reasoning_effort"),
    )

    text = ""
    ttft = None
    seen: dict[str, float] = {}
    for chunk in r.chunks:
        if not chunk.text:
            continue
        if ttft is None:
            ttft = time.time() - started
        text += chunk.text
        # 필드가 "완성"된 시점 = 그 키의 값 뒤에 쉼표나 닫는 괄호가 온 때
        for k in order:
            if k in seen:
                continue
            needle = '"%s"' % k
            at = text.find(needle)
            if at < 0:
                continue
            tail = text[at + len(needle):]
            if tail.count(",") >= 1 or tail.rstrip().endswith("}"):
                seen[k] = time.time() - started
    total = time.time() - started

    print("\n%-20s %8s" % ("필드", "도착"))
    print("-" * 30)
    for k in order:
        t = seen.get(k)
        mark = " ←" if k in NEEDED else ""
        print("%-20s %7s%s" % (k, ("%.2f초" % t) if t else "—", mark))

    print("\n" + "=" * 46)
    print("첫 토큰            %.2f초" % (ttft or 0))
    need = [seen[k] for k in NEEDED if k in seen]
    if need:
        print("뒷단계 재료가 다 옴  %.2f초" % max(need))
    print("응답 전체          %.2f초" % total)
    if need:
        saved = total - max(need)
        print("\n먼저 시작할 수 있는 여유: %.2f초 (%.0f%%)" % (saved, saved / total * 100))
    print("\n※ 이 여유가 작으면 필드 순서를 바꿔야 의미가 생긴다.")


if __name__ == "__main__":
    main()
