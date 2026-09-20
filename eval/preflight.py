from __future__ import annotations

import json
import os
from pathlib import Path

from eval.config import key_env_for, load_dotenv, resolve_model

ROOT = Path(__file__).resolve().parent
EVAL = ROOT  # scripts live inside eval/ now; there is no nested eval/
PROJECT_ROOT = EVAL.parent
# The full candidate list, not the subset one machine happens to hold keys for.
# preflight is a shared checklist: it has to report a missing key as missing,
# because that absence is the finding. Narrowing this to the models a given
# laptop can reach makes an unmeasured first choice look like it was never a
# candidate. Source of truth: guidelines/4_모듈_브리프.md §F.
DEFAULT_MODELS = [
    "gpt-5.6-luna",      # 1순위
    "gpt-5-nano",
    "claude-haiku-4-5",
    "mistral-small-4",
    "ministral-3-3b",    # 측정 완료 · 탈락 (results.md 09-20)
]
REQUIRED_FIXTURE_KEYS = {"id", "slots", "asked", "template", "utterance"}


def inspect_jsonl(path: Path) -> dict:
    info = {"rows": 0, "gold": 0, "bad_json": 0, "bad_shape": 0, "examples": []}
    if not path.exists():
        return info
    with path.open("r", encoding="utf-8") as f:
        for line_no, line in enumerate(f, 1):
            if not line.strip():
                continue
            info["rows"] += 1
            try:
                row = json.loads(line)
            except Exception as e:
                info["bad_json"] += 1
                info["examples"].append(f"line {line_no}: invalid JSON ({e})")
                continue
            missing = REQUIRED_FIXTURE_KEYS - set(row)
            if missing:
                info["bad_shape"] += 1
                info["examples"].append(f"line {line_no}: missing {sorted(missing)}")
            if isinstance(row.get("gold"), dict):
                info["gold"] += 1
    return info


def inspect_schema(path: Path) -> list[str]:
    if not path.exists():
        return []
    try:
        schema = json.loads(path.read_text(encoding="utf-8"))
    except Exception as e:
        return [f"schema JSON parse 실패: {e}"]
    problems = []
    if schema.get("type") != "object":
        problems.append("top-level type이 object가 아님")
    if schema.get("additionalProperties") is not False:
        problems.append("additionalProperties:false가 아님")
    req = set(schema.get("required", []))
    for key in ("reason", "slot_1", "slot_2", "s1_reason", "s2_addition", "next_slot", "no_longer_needed", "story_ready"):
        if key not in req:
            problems.append(f"required에 {key} 없음")
    return problems


def main():
    load_dotenv()
    files = {
        "박진웅 판정 프롬프트": EVAL / "judge_prompt.md",
        "박진웅 판정 스키마": EVAL / "judge_schema.json",
        "안치영 판정 평가셋": EVAL / "fixtures_judge.jsonl",
    }
    optional = {
        "박진웅 생성 프롬프트": EVAL / "story_prompt.md",
        "박진웅 생성 스키마": EVAL / "story_schema.json",
        "안치영 생성 입력 3벌": EVAL / "fixtures_story.jsonl",
        "공식 금칙어 정본": PROJECT_ROOT / "guidelines" / "8_금칙어.md",
    }

    print("=== 배터리/부품 preflight ===")
    for label, path in files.items():
        print(f"{'OK' if path.exists() else 'MISSING':7} {label}: {path.relative_to(PROJECT_ROOT)}")
    print("\n=== 생성 채점용 부품(없어도 판정 예측은 가능) ===")
    for label, path in optional.items():
        print(f"{'OK' if path.exists() else 'WAIT':7} {label}: {path.relative_to(PROJECT_ROOT)}")

    fixtures = EVAL / "fixtures_judge.jsonl"
    info = inspect_jsonl(fixtures)
    if info["rows"]:
        print(f"\nINFO    평가셋: {info['rows']}줄 / gold {info['gold']}줄 / 형식오류 {info['bad_shape']} / JSON오류 {info['bad_json']}")
        for ex in info["examples"][:3]:
            print("WARN   ", ex)
        if info["gold"] < info["rows"]:
            print("INFO    gold가 아직 없어도 예측 수집은 가능. 채점은 박진웅 gold 도착 후 API 재호출 없이 진행.")

    schema_problems = inspect_schema(EVAL / "judge_schema.json")
    for problem in schema_problems:
        print("WARN    ", problem)

    print("\n=== 모델/API 키 ===")
    any_ready = False
    for alias in DEFAULT_MODELS:
        cfg = resolve_model(alias)
        env_name = key_env_for(cfg["provider"])
        ready = bool(os.getenv(env_name)) if env_name else True
        any_ready |= ready
        print(f"{'READY' if ready else 'NO KEY':7} {alias:20} -> {cfg['api_model']} ({env_name or 'mock'})")
        # A present key is not a usable key. An org-level Anthropic key is
        # rejected on every call with HTTP 400 until the workspace is named,
        # and that only shows up 100 questions in. Say it here instead.
        if ready and cfg["provider"] == "anthropic" and not os.getenv("ANTHROPIC_WORKSPACE_ID"):
            print("        └ 워크스페이스에 묶이지 않은 키라면 전부 HTTP 400이 난다.")
            print("          워크스페이스 전용 키를 새로 만들거나 .env에 ANTHROPIC_WORKSPACE_ID를 넣는다.")

    print("\n=== 실행 판단 ===")
    required_files = all(path.exists() for path in files.values())
    fixture_ok = info["rows"] > 0 and info["bad_json"] == 0 and info["bad_shape"] == 0
    schema_ok = not schema_problems
    if required_files and fixture_ok and schema_ok and any_ready:
        print("READY: 최소 1개 실제 모델의 판정 예측을 수집할 준비가 됨.")
        print("실행 전 비용 확인 후: python -m eval.run_team_eval --yes-spend")
    else:
        print("WAIT: 위 MISSING / 형식 WARN / NO KEY 항목을 받은 뒤 다시 preflight 실행.")
        print("무료 새 스키마 데모는 언제든: python -m eval.run_demo")


if __name__ == "__main__":
    main()
