from __future__ import annotations

import argparse
import hashlib
import json
import os
from datetime import date
from pathlib import Path

from eval.config import key_env_for, load_dotenv, resolve_model, usd_krw
from eval.run_judge import run as run_judge
from eval.score import score_judge

ROOT = Path(__file__).resolve().parent
EVAL = ROOT  # scripts live inside eval/ now; there is no nested eval/
RAW = EVAL / "raw"
DEFAULT_MODELS = ["mistral-small-4", "ministral-3-3b"]


def safe_name(name: str) -> str:
    return "".join(c if c.isalnum() or c in "-_." else "_" for c in name)


def has_all_gold(path: Path) -> bool:
    seen = 0
    with path.open("r", encoding="utf-8") as f:
        for line in f:
            if not line.strip():
                continue
            seen += 1
            row = json.loads(line)
            if not isinstance(row.get("gold"), dict):
                return False
    return seen > 0


def pct(v: float) -> str:
    return f"{v * 100:.1f}%"


def sec(v) -> str:
    return "-" if v is None else f"{v:.3f}s"


def append_results(path: Path, markdown: str) -> bool:
    """Append one result section without replacing or duplicating prior measurements."""
    section = markdown.rstrip() + "\n"
    digest = hashlib.sha256(section.encode("utf-8")).hexdigest()[:16]
    marker = f"<!-- eval-result:{digest} -->"
    existing = path.read_text(encoding="utf-8") if path.exists() else ""
    if marker in existing:
        return False

    if existing and not existing.endswith("\n"):
        existing += "\n"
    if existing and not existing.endswith("\n\n"):
        existing += "\n"
    block = f"{marker}\n{section}<!-- /eval-result:{digest} -->\n"
    path.write_text(existing + block, encoding="utf-8")
    return True


def render_results(results: list[tuple[str, dict]], fx: float) -> str:
    lines = [
        f"## {date.today().isoformat()} · 모델 판정 비교",
        "",
        f"> 환율 가정: 1 USD = {fx:,.0f} KRW. 가격 스냅샷은 `eval/model_catalog.json`을 확인하세요.",
        "",
        "| 모델 | JSON | slot_1 F1 | 다중채움 F1 | 필수해제 F1 | s1 F1 | s2 F1 | next_slot | value_1 | TTFT p50 | TTFT p95 | 판정 세션 원가 |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for alias, m in results:
        c, b = m["categorical"], m["binary"]
        lines.append(
            f"| {alias} | {pct(m['json_success_rate'])} | {c['slot_1']['f1']:.3f} | "
            f"{c['slot_2']['f1']:.3f} | {c['no_longer_needed']['f1']:.3f} | "
            f"{b['s1_reason']['f1']:.3f} | {b['s2_addition']['f1']:.3f} | "
            f"{pct(m['next_slot']['accuracy'])} | {pct(m['value_1']['accuracy'])} | "
            f"{sec(m['ttft_p50'])} | {sec(m['ttft_p95'])} | {m['session_cost_krw']:.2f}원 |"
        )
    lines += [
        "",
        "### 전체 응답시간 · 토큰",
        "",
        "| 모델 | total p50 | total p95 | total 평균 | 평균 입력 토큰 | 평균 출력 토큰 |",
        "| --- | ---: | ---: | ---: | ---: | ---: |",
    ]
    for alias, m in results:
        lines.append(
            f"| {alias} | {sec(m['total_p50'])} | {sec(m['total_p95'])} | "
            f"{sec(m['total_avg'])} | {m['avg_input_tokens']:.1f} | {m['avg_output_tokens']:.1f} |"
        )
    lines += [
        "",
        "### 채점 규칙",
        "",
        "- `slot_1`, `slot_2`, `no_longer_needed`: null을 양성 클래스에서 제외한 범주형 macro F1.",
        "- `s1_reason`, `s2_addition`: 이진 Precision/Recall/F1.",
        "- `next_slot`: gold의 `next_slot_ok` 허용 집합 중 하나면 정답.",
        "- `value_1`: 자모 정규화 편집거리 ≤ 0.30이면 정답(보조 지표).",
        "- F1은 JSON 파싱/스키마 검증에 성공한 응답을 대상으로 계산하고 JSON 성공률은 별도 표시합니다.",
        "- raw JSONL에는 TTFT, total, token, 실패 원문이 남습니다.",
        "- 모델별 프롬프트를 손보지 않고 같은 파일을 사용해야 비교가 유효합니다.",
    ]
    return "\n".join(lines) + "\n"


def main():
    p = argparse.ArgumentParser(description="팀 실제 모델 비교 일괄 러너")
    p.add_argument("--models", nargs="+", default=DEFAULT_MODELS)
    p.add_argument("--fixtures", default=str(EVAL / "fixtures_judge.jsonl"))
    p.add_argument("--prompt", default=str(EVAL / "judge_prompt.md"))
    p.add_argument("--schema", default=str(EVAL / "judge_schema.json"))
    p.add_argument("--request-delay", type=float, default=1.0, help="요청 사이 대기 시간(초)")
    p.add_argument("--max-retries", type=int, default=4, help="HTTP 429 최대 재시도 횟수")
    p.add_argument("--yes-spend", action="store_true", help="실제 유료 API 호출 허용")
    args = p.parse_args()

    load_dotenv()
    fixtures = Path(args.fixtures)
    prompt = Path(args.prompt)
    schema = Path(args.schema)
    missing_files = [str(p) for p in (fixtures, prompt, schema) if not p.exists()]
    if missing_files:
        raise SystemExit("필수 팀 파일이 아직 없음:\n- " + "\n- ".join(missing_files) + "\n먼저 python -m eval.preflight 확인")

    runnable = []
    skipped = []
    for alias in args.models:
        cfg = resolve_model(alias)
        key_env = key_env_for(cfg["provider"])
        if key_env and not os.getenv(key_env):
            skipped.append((alias, f"missing {key_env}"))
        else:
            runnable.append(alias)

    print("=== 실제 모델 비교 준비 ===")
    print("실행 가능:", ", ".join(runnable) or "없음")
    for alias, why in skipped:
        print(f"건너뜀: {alias} ({why})")
    if not runnable:
        raise SystemExit("사용 가능한 API 키가 하나도 없음. 조장에게 키를 받은 뒤 다시 실행.")
    if not args.yes_spend:
        print("\n아직 API 호출하지 않았음. 비용 발생을 허용하려면:")
        print("python -m eval.run_team_eval --yes-spend")
        return

    RAW.mkdir(parents=True, exist_ok=True)
    fx = usd_krw()
    scored = []
    gold_ready = has_all_gold(fixtures)

    for alias in runnable:
        cfg = resolve_model(alias)
        out = RAW / f"{safe_name(alias)}.jsonl"
        print(f"\n>>> RUN {alias} -> {cfg['api_model']}")
        run_judge(
            model=alias,
            fixtures=fixtures,
            out=out,
            prompt_path=prompt,
            schema_path=schema,
            allow_real_api=True,
            request_delay=args.request_delay,
            max_retries=args.max_retries,
        )
        if gold_ready:
            metrics = score_judge(
                fixtures=fixtures,
                raw=out,
                input_price_usd_per_mtok=float(cfg.get("input_usd_per_mtok", 0.0)),
                output_price_usd_per_mtok=float(cfg.get("output_usd_per_mtok", 0.0)),
                usd_krw=fx,
            )
            scored.append((alias, metrics))

    if scored:
        md = render_results(scored, fx)
        results_path = EVAL / "results.md"
        appended = append_results(results_path, md)
        summary_path = RAW / "comparison_summary.json"
        summary_path.write_text(json.dumps(dict(scored), ensure_ascii=False, indent=2), encoding="utf-8")
        action = "누적" if appended else "동일 결과 존재 — 중복 생략"
        print(f"\n채점 완료 ({action}): {results_path}")
        print(f"통합 JSON: {summary_path}")
    else:
        print("\n예측 수집 완료. 아직 gold 라벨이 없어 채점은 보류함.")
        print("박진웅 gold가 fixtures_judge.jsonl에 합쳐지면 API 재호출 없이 python -m eval.score_existing 로 채점 가능.")


if __name__ == "__main__":
    main()
