"""Compare morphology counts with local, externally annotated TSV references.

No download, transcript output, or child-accuracy claim. Each source file stays
separate because typo variants can be correlated with the clean reference.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import importlib.metadata
import json
from pathlib import Path
import statistics

from eval.language_metrics import Morpheme, Tokenizer, Utterance, kiwi_tokenizer, measure

# Independent annotation contract: do not derive the oracle from the calculator
# whitelist, otherwise missing POS classes would be invisible to this check.
REFERENCE_MORPHEMES = frozenset("""
NNG NNP NNB NP NR VV VA VX VCP VCN MM MAG MAJ IC
XPN XSN XSV XSA XSM XR JKS JKC JKG JKO JKB JKV JKQ JX JC
EP EF EC ETN ETM SL SH SN
""".split())
REFERENCE_OMITTED = frozenset("""
SF SP SS SSO SSC SE SO SW SB W_URL W_EMAIL W_HASHTAG W_MENTION
W_SERIAL W_EMOJI Z_CODA Z_SIOT
""".split())
DEFAULT_FILES = ("written.txt", "web.txt", "web_with_typos.txt", "web_with_cont_typos.txt")
UPSTREAM_COMMIT = "280302af5ff9e739bec1ce56d6df5f9436ac41d4"


def parse_reference(line: str) -> tuple[str, list[Morpheme], int]:
    fields = line.split("\t")
    if len(fields) != 2 or not fields[0].strip() or not fields[1].strip():
        raise ValueError("Reference requires text and nonempty form/POS annotations")
    tokens = []
    for item in fields[1].split():
        if "/" not in item:
            raise ValueError("Reference token is missing its POS separator")
        form, tag = item.rsplit("/", 1)
        tag = tag.split("-")[0]
        if not form or tag not in REFERENCE_MORPHEMES | REFERENCE_OMITTED:
            raise ValueError("Unknown reference POS or empty form; review the contract")
        tokens.append(Morpheme(form, tag))
    return fields[0], tokens, sum(t.tag in REFERENCE_MORPHEMES for t in tokens)


def evaluate_reference(path: Path, tokenize: Tokenizer) -> dict:
    errors, expected_counts, predicted_counts = [], [], []
    data = path.read_bytes()
    for number, line in enumerate(data.decode("utf-8-sig").splitlines(), 1):
        if not line.strip() or line.startswith("#"):
            continue
        try:
            text, gold, expected = parse_reference(line)
        except ValueError as exc:
            raise ValueError(f"Reference line {number}: {exc}") from None
        utterance = Utterance(text, "child", "unknown")
        oracle = measure([utterance], lambda _: gold, scope="all_child")
        if oracle.morphemes != expected or oracle.utterances != int(expected > 0):
            raise ValueError(f"Calculator disagrees with annotation count at line {number}")
        actual = measure([utterance], tokenize, scope="all_child").morphemes
        expected_counts.append(expected)
        predicted_counts.append(actual)
        errors.append(actual - expected)
    if not errors:
        raise ValueError("Reference has no annotated input lines")
    return {
        "sha256": hashlib.sha256(data).hexdigest(), "lines": len(errors),
        "arithmetic_oracle_checks_passed": len(errors),
        "exact_count_matches": sum(e == 0 for e in errors),
        "exact_count_agreement": sum(e == 0 for e in errors) / len(errors),
        "mean_absolute_count_error": statistics.mean(map(abs, errors)),
        "mean_signed_count_error": statistics.mean(errors),
        "reference_mean_count": statistics.mean(expected_counts),
        "predicted_mean_count": statistics.mean(predicted_counts),
        "maximum_absolute_count_error": max(map(abs, errors)),
        "error_histogram": dict(sorted(Counter(errors).items())),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    inputs = [args.directory / name for name in DEFAULT_FILES]
    if args.output.resolve() in [p.resolve() for p in inputs]:
        parser.error("Output must not overwrite a reference file")
    try:
        tokenize = kiwi_tokenizer()
        result = {
            "kind": "upstream_reference_count_agreement_not_child_accuracy",
            "expected_source_commit": UPSTREAM_COMMIT,
            "source_verification": "Compare file hashes with the published audit manifest",
            "kiwi": importlib.metadata.version("kiwipiepy"),
            "model": importlib.metadata.version("kiwipiepy_model"),
            "files": {p.name: evaluate_reference(p, tokenize) for p in inputs},
        }
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    except (ValueError, RuntimeError, OSError) as exc:
        parser.error(str(exc))
    print("Reference count audit saved; this is not a child-speech accuracy estimate.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
