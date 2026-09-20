from __future__ import annotations

import json
from pathlib import Path

from eval.corrupt import evaluate_presets, load_presets, SEED
from eval.run_judge import run as run_judge
from eval.score import score_judge, score_stories

ROOT = Path(__file__).resolve().parent
EVAL = ROOT  # scripts live inside eval/ now; there is no nested eval/
RAW = EVAL / "raw"


def pct(v):
    return f"{v * 100:.1f}%"


def sec(v):
    return "-" if v is None else f"{v:.4f}s"


def main():
    RAW.mkdir(parents=True, exist_ok=True)

    judge_raw = RAW / "mock_judge.jsonl"
    judge_rows = run_judge(
        model="mock-judge-v1",
        fixtures=EVAL / "fixtures_judge_demo.jsonl",
        out=judge_raw,
        prompt_path=EVAL / "judge_prompt_demo.md",
        schema_path=EVAL / "judge_schema_demo.json",
        allow_real_api=False,
    )

    judge = score_judge(
        fixtures=EVAL / "fixtures_judge_demo.jsonl",
        raw=judge_raw,
        input_price_usd_per_mtok=0.0,
        output_price_usd_per_mtok=0.0,
        usd_krw=1400.0,
    )

    story = score_stories(
        fixtures=EVAL / "fixtures_story_demo.jsonl",
        forbidden_words_path=EVAL / "forbidden_words_demo.txt",
    )

    presets = load_presets(EVAL / "presets_demo.json")
    corrupt_rows, corrupt_summary = evaluate_presets(presets)
    corrupt_raw = RAW / "corrupt_demo.jsonl"
    with corrupt_raw.open("w", encoding="utf-8") as f:
        for row in corrupt_rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")

    summary_json = RAW / "summary_demo.json"
    summary_json.write_text(
        json.dumps({"seed": SEED, "judge": judge, "story": story, "corrupt": corrupt_summary}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    c = judge["categorical"]
    b = judge["binary"]
    md = []
    md.append("# results_demo")
    md.append("")
    md.append("> 데모 전용 결과입니다. 실제 모델/API 성능이 아닙니다. 새 17필드 판정 스키마 + Mock provider + 임시 20개 데이터로 러너/채점기가 정상 동작하는지만 확인합니다.")
    md.append("")
    md.append("## 1. 판정 러너 / 채점")
    md.append("")
    md.append("| 모델 | JSON | slot_1 F1 | slot_2 F1 | 필수해제 F1 | s1 F1 | s2 F1 | next_slot | value_1 | TTFT p50 | TTFT p95 | 한 권 원가 |")
    md.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |")
    md.append(
        "| mock-judge-v1 | {json} | {slot1:.3f} | {slot2:.3f} | {nln:.3f} | {s1:.3f} | {s2:.3f} | {nexts} | {v1} | {p50} | {p95} | {cost:.2f}원 |".format(
            json=pct(judge["json_success_rate"]),
            slot1=c["slot_1"]["f1"],
            slot2=c["slot_2"]["f1"],
            nln=c["no_longer_needed"]["f1"],
            s1=b["s1_reason"]["f1"],
            s2=b["s2_addition"]["f1"],
            nexts=pct(judge["next_slot"]["accuracy"]),
            v1=pct(judge["value_1"]["accuracy"]),
            p50=sec(judge["ttft_p50"]),
            p95=sec(judge["ttft_p95"]),
            cost=judge["session_cost_krw"],
        )
    )
    md.append("")
    md.append("- `slot_1/slot_2/no_longer_needed`는 실제 슬롯명을 one-vs-rest로 본 **macro F1**입니다.")
    md.append("- `next_slot`은 gold의 `next_slot_ok` 허용 집합 안에 예측이 들어오면 정답입니다.")
    md.append("- `value_1`은 자모 편집거리 정규화 값이 **≤ 0.30**이면 정답입니다(보조 지표).")

    md.append("")
    md.append("### 세부 Precision / Recall / F1")
    md.append("")
    md.append("| field | Precision | Recall | F1 | exact accuracy |")
    md.append("| --- | ---: | ---: | ---: | ---: |")
    for field in ("slot_1", "slot_2", "no_longer_needed"):
        m = c[field]
        md.append(f"| {field} | {m['precision']:.3f} | {m['recall']:.3f} | {m['f1']:.3f} | {m['accuracy']:.3f} |")
    for field in ("s1_reason", "s2_addition"):
        m = b[field]
        md.append(f"| {field} | {m['precision']:.3f} | {m['recall']:.3f} | {m['f1']:.3f} | {m['accuracy']:.3f} |")

    failures = [r for r in judge_rows if not r.get("ok")]
    md.append("")
    md.append(f"- JSON 파싱/검증 실패: **{len(failures)}건** — raw JSONL에 실패를 남기고 다음 항목을 계속 실행합니다.")
    md.append(f"- 전체 응답시간 평균: **{judge['total_avg']:.4f}s**")
    md.append(f"- 평균 입력 토큰: **{judge['avg_input_tokens']:.1f}**, 평균 출력 토큰: **{judge['avg_output_tokens']:.1f}**")
    md.append("- 데모 단가는 0원입니다. 실제 단가는 모델 카탈로그에서 주입해 같은 공식으로 한 권(판정 16회) 비용을 계산합니다.")

    md.append("")
    md.append("## 2. 이야기 생성 자동 채점")
    md.append("")
    md.append("| 항목 | 결과 | 데모 합격선 |")
    md.append("| --- | ---: | ---: |")
    md.append(f"| −어요체 준수율 | {pct(story['yo_style_rate'])} | ≥ 95% |")
    md.append(f"| 15어절 이하 비율 | {pct(story['under_15_eojeol_rate'])} | ≥ 95% |")
    md.append(f"| 금지 표현 | {story['forbidden_count']}건 | 0건 |")
    md.append(f"| 자리표시자 무결성 | {pct(story['placeholder_integrity_rate'])} | 100% |")
    md.append(f"| 정확히 6장면 | {pct(story['scene_count_rate'])} | 100% |")

    md.append("")
    md.append("## 3. 자모 손상 / 프리셋 매칭")
    md.append("")
    md.append(f"- 랜덤 시드: `{SEED}`")
    md.append("")
    md.append("| 강도 | 손상률 | 적용 전 정확도 | 자모 편집거리 적용 후 |")
    md.append("| --- | ---: | ---: | ---: |")
    for level, rate in [("0", 0), ("1", 10), ("2", 25)]:
        x = corrupt_summary[level]
        md.append(f"| {level} | {rate}% | {pct(x['before_accuracy'])} | {pct(x['after_accuracy'])} |")

    md.append("")
    md.append("## 4. 팀 자료 대기 슬롯")
    md.append("")
    md.append("- `eval/judge_prompt.md` — 박진웅")
    md.append("- `eval/judge_schema.json` — 박진웅")
    md.append("- `eval/fixtures_judge.jsonl` — 안치영 추출본 → 이후 박진웅 gold 합본")
    md.append("- `eval/story_prompt.md`, `eval/story_schema.json` — 박진웅")
    md.append("- `eval/fixtures_story.jsonl` — 안치영")
    md.append("- `.env` API 키 — 조장")

    (ROOT / "results_demo.md").write_text("\n".join(md) + "\n", encoding="utf-8")
    (EVAL / "results.md").write_text("\n".join(md) + "\n", encoding="utf-8")

    print("=== Minwoo model-eval demo v3 ===")
    print(f"judge JSON success: {pct(judge['json_success_rate'])}")
    print(f"slot_1/slot_2 F1: {c['slot_1']['f1']:.3f} / {c['slot_2']['f1']:.3f}")
    print(f"next_slot accuracy: {pct(judge['next_slot']['accuracy'])}")
    print(f"value_1 fuzzy accuracy: {pct(judge['value_1']['accuracy'])}")
    print(f"TTFT p50/p95: {sec(judge['ttft_p50'])} / {sec(judge['ttft_p95'])}")
    print(f"story yo-style: {pct(story['yo_style_rate'])}")
    print(f"corrupt level1 before/after: {pct(corrupt_summary['1']['before_accuracy'])} -> {pct(corrupt_summary['1']['after_accuracy'])}")
    print(f"corrupt level2 before/after: {pct(corrupt_summary['2']['before_accuracy'])} -> {pct(corrupt_summary['2']['after_accuracy'])}")
    print("saved: results_demo.md, eval/results.md, eval/raw/*.jsonl")


if __name__ == "__main__":
    main()
