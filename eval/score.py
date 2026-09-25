from __future__ import annotations

import json
import math
import re
import statistics
from pathlib import Path
from typing import Iterable

try:
    from .corrupt import jamo_string, levenshtein
    from .forbidden import find_forbidden_hits, load_story_policy
except ImportError:
    from corrupt import jamo_string, levenshtein
    from forbidden import find_forbidden_hits, load_story_policy


def load_jsonl(path: Path) -> list[dict]:
    with path.open("r", encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def percentile(values: Iterable[float], q: float) -> float | None:
    xs = sorted(v for v in values if v is not None)
    if not xs:
        return None
    if len(xs) == 1:
        return xs[0]
    pos = (len(xs) - 1) * q
    lo = math.floor(pos)
    hi = math.ceil(pos)
    if lo == hi:
        return xs[lo]
    return xs[lo] + (xs[hi] - xs[lo]) * (pos - lo)


def prf(tp: int, fp: int, fn: int) -> dict:
    precision = tp / (tp + fp) if tp + fp else 0.0
    recall = tp / (tp + fn) if tp + fn else 0.0
    f1 = (2 * precision * recall / (precision + recall)) if precision + recall else 0.0
    return {"precision": precision, "recall": recall, "f1": f1, "tp": tp, "fp": fp, "fn": fn}


def score_binary(pairs: list[tuple[bool, bool]]) -> dict:
    tp = sum(g and p for g, p in pairs)
    fp = sum((not g) and p for g, p in pairs)
    fn = sum(g and (not p) for g, p in pairs)
    out = prf(tp, fp, fn)
    out["count"] = len(pairs)
    out["accuracy"] = sum(g == p for g, p in pairs) / len(pairs) if pairs else 0.0
    return out


_LOCATION_ENDINGS = re.compile(r"(에서|거기서|여기서|어디서)")
_SEO_ENDING = re.compile(r"[가-힣]서[ ,.!?]|[가-힣]서$")
_REASON_MARKERS = ("니까", "때문에", "왜냐하면", "그래서", "그러면", "거든")


def has_reason_marker(utterance: str) -> bool:
    """Match the frozen fixture audit's surface markers, excluding locative -에서.

    This is a diagnostic subset selector, not a replacement for the gold label.
    """
    if _SEO_ENDING.search(utterance) and _SEO_ENDING.search(_LOCATION_ENDINGS.sub("", utterance)):
        return True
    return any(marker in utterance for marker in _REASON_MARKERS)


def score_categorical_macro(pairs: list[tuple[object, object]]) -> dict:
    """null은 '양성 클래스'에서 제외하고 각 실제 슬롯명을 one-vs-rest로 macro F1.

    single-label multiclass에서 단순 accuracy만 보는 것보다 희소한 slot_2/no_longer_needed
    클래스가 묻히지 않도록 macro를 사용한다. exact accuracy도 같이 남긴다.
    """
    labels = sorted({x for pair in pairs for x in pair if x is not None}, key=str)
    per_label = {}
    for label in labels:
        binary = [(g == label, p == label) for g, p in pairs]
        per_label[str(label)] = score_binary(binary)
    macro = {
        k: (statistics.fmean(m[k] for m in per_label.values()) if per_label else 0.0)
        for k in ("precision", "recall", "f1")
    }
    return {
        **macro,
        "accuracy": sum(g == p for g, p in pairs) / len(pairs) if pairs else 0.0,
        "count": len(pairs),
        "labels": per_label,
    }


def normalized_jamo_distance(a: str, b: str) -> float:
    ja, jb = jamo_string(a or ""), jamo_string(b or "")
    denom = max(len(ja), len(jb), 1)
    return levenshtein(ja, jb) / denom


def score_value1(gold_pred_pairs: list[tuple[object, object]], threshold: float = 0.30) -> dict:
    rows = []
    for gold, pred in gold_pred_pairs:
        if gold is None:
            continue
        if pred is None:
            dist = 1.0
        else:
            dist = normalized_jamo_distance(str(gold), str(pred))
        rows.append({"gold": gold, "pred": pred, "distance": dist, "ok": dist <= threshold})
    return {
        "threshold": threshold,
        "accuracy": sum(r["ok"] for r in rows) / len(rows) if rows else 0.0,
        "count": len(rows),
        "rows": rows,
    }


def score_judge(
    *,
    fixtures: Path,
    raw: Path,
    input_price_usd_per_mtok: float = 0.0,
    output_price_usd_per_mtok: float = 0.0,
    usd_krw: float = 1400.0,
) -> dict:
    gold_rows = {x["id"]: x for x in load_jsonl(fixtures)}
    pred_rows = load_jsonl(raw)
    missing_gold = [r["id"] for r in pred_rows if "gold" not in gold_rows.get(r["id"], {})]
    if missing_gold:
        raise ValueError(f"gold label missing for {len(missing_gold)} rows, e.g. {missing_gold[:3]}")

    successes = [r for r in pred_rows if r.get("ok") and isinstance(r.get("pred"), dict)]

    def pairs_for(field: str):
        out = []
        for row in successes:
            gold = gold_rows[row["id"]]["gold"]
            if field in gold:
                out.append((gold.get(field), row["pred"].get(field)))
        return out

    categorical = {
        "slot_1": score_categorical_macro(pairs_for("slot_1")),
        "slot_2": score_categorical_macro(pairs_for("slot_2")),
        "no_longer_needed": score_categorical_macro(pairs_for("no_longer_needed")),
    }
    binary = {
        "s1_reason": score_binary([(bool(g), bool(p)) for g, p in pairs_for("s1_reason")]),
        "s2_addition": score_binary([(bool(g), bool(p)) for g, p in pairs_for("s2_addition")]),
    }

    attempted_ids = {row["id"] for row in pred_rows}
    attempted_gold = {row_id: row for row_id, row in gold_rows.items() if row_id in attempted_ids}
    marked_gold = {row_id: row for row_id, row in attempted_gold.items() if has_reason_marker(row.get("utterance", ""))}
    marked_successes = [row for row in successes if row["id"] in marked_gold]
    marked_pairs = [
        (bool(marked_gold[row["id"]]["gold"]["s1_reason"]), bool(row["pred"]["s1_reason"]))
        for row in marked_successes
    ]
    marker_score = score_binary(marked_pairs)
    trap_pairs = [(gold, pred) for gold, pred in marked_pairs if not gold]
    marker_score.update(
        fixture_count=len(marked_gold),
        json_success_rate=len(marked_successes) / len(marked_gold) if marked_gold else 0.0,
        trap_count=sum(not bool(row["gold"]["s1_reason"]) for row in marked_gold.values()),
        trap_scored_count=len(trap_pairs),
        trap_correct_count=sum(not pred for _, pred in trap_pairs),
    )
    extra_score = categorical["slot_1"]["labels"].get("extra", score_binary([]))
    extra_score = {
        **extra_score,
        "asked_count": sum(row.get("asked") == "extra" for row in attempted_gold.values()),
        "gold_count": sum(row["gold"].get("slot_1") == "extra" for row in attempted_gold.values()),
        "scored_gold_count": extra_score["tp"] + extra_score["fn"],
    }

    next_hits = []
    for row in successes:
        gold = gold_rows[row["id"]]["gold"]
        allowed = gold.get("next_slot_ok")
        # An empty list means the row has no next_slot label (25 of 100) - skip it, as
        # bench_jev does. Counting it as a miss understated every luna next_slot by 25%.
        if isinstance(allowed, list) and allowed:
            pred = row["pred"].get("next_slot")
            next_hits.append({"id": row["id"], "pred": pred, "allowed": allowed, "ok": pred in allowed})
    next_slot = {
        "accuracy": sum(x["ok"] for x in next_hits) / len(next_hits) if next_hits else 0.0,
        "count": len(next_hits),
        "rows": next_hits,
    }

    value1 = score_value1(pairs_for("value_1"), threshold=0.30)

    avg_in = statistics.fmean([r.get("in_tok", 0) for r in pred_rows]) if pred_rows else 0.0
    avg_out = statistics.fmean([r.get("out_tok", 0) for r in pred_rows]) if pred_rows else 0.0
    session_cost_usd = ((avg_in * 16 * input_price_usd_per_mtok) + (avg_out * 16 * output_price_usd_per_mtok)) / 1_000_000
    session_cost_krw = session_cost_usd * usd_krw

    return {
        "count": len(pred_rows),
        "json_success_rate": len(successes) / len(pred_rows) if pred_rows else 0.0,
        "categorical": categorical,
        "binary": binary,
        "diagnostics": {"s1_reason_marked": marker_score, "slot_1_extra": extra_score},
        "next_slot": next_slot,
        "value_1": value1,
        "ttft_p50": percentile([r.get("ttft") for r in pred_rows], 0.50),
        "ttft_p95": percentile([r.get("ttft") for r in pred_rows], 0.95),
        "total_p50": percentile([r.get("total") for r in pred_rows], 0.50),
        "total_p95": percentile([r.get("total") for r in pred_rows], 0.95),
        "total_avg": statistics.fmean([r.get("total", 0.0) for r in pred_rows]) if pred_rows else None,
        "avg_input_tokens": avg_in,
        "avg_output_tokens": avg_out,
        "session_cost_usd": session_cost_usd,
        "session_cost_krw": session_cost_krw,
        "usd_krw": usd_krw,
    }


def _is_yo_style(text: str) -> bool:
    text = text.strip()
    return bool(re.search(r"(?:요|어요|예요|에요)[.!?]?$", text))


def _scene_caption(scene: dict) -> str:
    """Use the current story schema field while retaining demo fixture compatibility."""
    return str(scene.get("caption", scene.get("subtitle", "")))


def score_stories(*, fixtures: Path, forbidden_words_path: Path) -> dict:
    stories = load_jsonl(fixtures)
    policy = load_story_policy(forbidden_words_path)
    subtitles = []
    scene_ok = 0
    placeholder_ok = 0
    forbidden_hits = []

    for story in stories:
        scenes = story.get("scenes", [])
        if len(scenes) == 6:
            scene_ok += 1
        story_text = " ".join(_scene_caption(scene) for scene in scenes)
        if "{주인공}" in story_text and "{친구1}" in story_text:
            placeholder_ok += 1
        for scene_idx, scene in enumerate(scenes, start=1):
            subtitle = _scene_caption(scene)
            subtitles.append(subtitle)
            for hit in find_forbidden_hits(subtitle, policy):
                forbidden_hits.append({
                    "story": story.get("id"),
                    "scene": scene_idx,
                    "word": hit.term,
                    "matched": hit.matched,
                    "fuzzy": hit.fuzzy,
                })

    total_subs = len(subtitles)
    yo_rate = sum(_is_yo_style(s) for s in subtitles) / total_subs if total_subs else 0.0
    under15_rate = sum(len(s.split()) <= 15 for s in subtitles) / total_subs if total_subs else 0.0
    return {
        "story_count": len(stories),
        "subtitle_count": total_subs,
        "yo_style_rate": yo_rate,
        "under_15_eojeol_rate": under15_rate,
        "forbidden_count": len(forbidden_hits),
        "forbidden_hits": forbidden_hits,
        "placeholder_integrity_rate": placeholder_ok / len(stories) if stories else 0.0,
        "scene_count_rate": scene_ok / len(stories) if stories else 0.0,
    }
