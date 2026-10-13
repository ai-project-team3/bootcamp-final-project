"""Prepare local blind morphology review and compare finalized human counts.

Human attestation is recorded, never independently verified by this tool.
Original child data and review packs must remain outside the repository.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import importlib.metadata
import json
from pathlib import Path
import statistics
import unicodedata

from eval.language_metrics import Utterance, kiwi_tokenizer, measure

QUOTAS = {3: 13, 4: 13, 5: 12, 6: 12}
CRITERIA = "otto-count-review-v1"


def normalized(text: str) -> str:
    return " ".join(unicodedata.normalize("NFC", text).split())


def fingerprint(obj: dict) -> str:
    return hashlib.sha256(json.dumps(obj, ensure_ascii=False, sort_keys=True).encode()).hexdigest()


def select_records(records, excluded_texts: set[str], *, seed: int) -> list[dict]:
    excluded = {normalized(t) for t in excluded_texts}
    candidates = []
    ages = {}
    for row in records:
        age, speaker, text = row['age'], row['speaker_id'], row['text']
        if type(age) is not int or age not in QUOTAS or not isinstance(speaker, str) or not speaker:
            raise ValueError("Invalid sample provenance")
        if speaker in ages and ages[speaker] != age:
            raise ValueError("Inconsistent sample age")
        ages[speaker] = age
        if not isinstance(text, str):
            raise ValueError("Invalid sample text")
        if row.get('by') != 'child' or not any(c.isalnum() for c in text) or normalized(text) in excluded:
            continue
        key = fingerprint({'seed': seed, 'speaker': speaker, 'age': age, 'text': text})
        candidates.append((key, row))
    chosen, children, texts = [], set(), set()
    for age, quota in QUOTAS.items():
        count = 0
        for _, row in sorted(candidates, key=lambda x: x[0]):
            t = normalized(row['text'])
            if row['age'] != age or row['speaker_id'] in children or t in texts:
                continue
            chosen.append(row)
            children.add(row['speaker_id']); texts.add(t)
            count += 1
            if count == quota:
                break
        if count != quota:
            raise ValueError(f"Insufficient distinct-child sample for age {age}")
    # Hide age order from reviewers without using any analyzer output.
    return sorted(chosen, key=lambda r: fingerprint({'seed': seed + 1, 'text': r['text']}))


def empty_state(pack: dict) -> dict:
    return dict(source_sha256=fingerprint(pack), reviewer='', independent=False,
                criteria_accepted=False, revision=0, finalized=False, reviews={})


def validate_state(pack: dict, state: dict, *, finalize: bool = False) -> None:
    if not isinstance(state, dict):
        raise ValueError("Invalid review object")
    if state.get('source_sha256') != fingerprint(pack):
        raise ValueError("Review source mismatch")
    name = state.get('reviewer')
    if not isinstance(name, str) or not name.strip() or len(name) > 80:
        raise ValueError("Reviewer name or alias is required")
    for flag in ('independent', 'criteria_accepted', 'finalized'):
        if type(state.get(flag)) is not bool:
            raise ValueError("Invalid attestation")
    if type(state.get('revision')) is not int or state['revision'] < 0:
        raise ValueError("Invalid revision")
    entries = state.get('reviews')
    ids = {r['id'] for r in pack['items']}
    if not isinstance(entries, dict) or set(entries) - ids:
        raise ValueError("Invalid review item IDs")
    for item in entries.values():
        if not isinstance(item, dict) or item.get('status') not in {'gold', 'hold'}:
            raise ValueError("Invalid review decision")
        parts, reason = item.get('parts'), item.get('reason')
        if not isinstance(parts, list) or len(parts) > 500 or not all(isinstance(p, str) and p.strip() and len(p) < 1000 for p in parts):
            raise ValueError("Invalid or empty morphology part")
        if not isinstance(reason, str) or len(reason) > 4000:
            raise ValueError("Invalid review reason")
        if item['status'] == 'gold' and not parts:
            raise ValueError("A finalized count requires morphology parts")
        if item['status'] == 'hold' and (parts or not reason.strip()):
            raise ValueError("Uncertain items need a reason and no fabricated gold")
    if finalize and (set(entries) != ids or not state['independent'] or not state['criteria_accepted']):
        raise ValueError("Complete all decisions and attest independent review before comparison")


def compare(pack: dict, state: dict, predicted: dict) -> dict:
    if state.get('finalized') is not True:
        raise ValueError("Human gold must be frozen before comparison")
    validate_state(pack, state, finalize=True)
    if set(predicted) != {r['id'] for r in pack['items']} or any(type(n) is not int or n < 0 for n in predicted.values()):
        raise ValueError("Predictions do not match the complete review pack")
    differences = []
    for row in pack['items']:
        entry = state['reviews'][row['id']]
        if entry['status'] == 'gold':
            count = len(entry['parts'])
            differences.append(dict(id=row['id'], human_count=count,
                                    predicted_count=predicted[row['id']], difference=predicted[row['id']] - count))
    errors = [r['difference'] for r in differences]
    return dict(kind='human_self_attested_count_agreement_not_expert_certification',
                sample_items=len(pack['items']), gold_items=len(errors), held_items=len(pack['items']) - len(errors),
                coverage=len(errors) / len(pack['items']),
                exact_count_agreement=sum(e == 0 for e in errors) / len(errors) if errors else None,
                mean_absolute_count_error=statistics.mean(map(abs, errors)) if errors else None,
                mean_signed_count_error=statistics.mean(errors) if errors else None,
                source_sha256=fingerprint(pack), frozen_review_sha256=fingerprint(state),
                interpretation='Pilot count agreement on resolved items; report held items; not STT, token accuracy, age norms or service effect',
                differences=differences)


def build(input_path: Path, seen_path: Path, output: Path, *, seed: int = 1012321) -> dict:
    records = [json.loads(line) for line in input_path.read_text(encoding='utf-8-sig').splitlines() if line.strip()]
    seen = json.loads(seen_path.read_text(encoding='utf-8'))
    selection = select_records(records, {r['text'] for r in seen['rows']}, seed=seed)
    pack = dict(schema=1, criteria_version=CRITERIA, seed=seed,
                purpose='50-item blind-review pilot, one answer per child; not population accuracy',
                original_sha256=hashlib.sha256(input_path.read_bytes()).hexdigest(),
                seen_reference_sha256=hashlib.sha256(seen_path.read_bytes()).hexdigest(),
                quotas=QUOTAS, items=[dict(id=f'B{i:03}', age=r['age'], text=r['text']) for i,r in enumerate(selection, 1)])
    # Freeze predictions only after selecting without model scores.
    tokenize = kiwi_tokenizer()
    predictions = {r['id']: measure([Utterance(r['text'], 'child', 'unknown')], tokenize, scope='all_child').morphemes
                   for r in pack['items']}
    pack['prediction_counts_sha256'] = fingerprint(predictions)
    pack['analyzer'] = dict(kiwi=importlib.metadata.version('kiwipiepy'),
                            model=importlib.metadata.version('kiwipiepy_model'), model_type='cong',
                            normalize_coda=False, split_complex=False, typos=None)
    reference = dict(source_sha256=fingerprint(pack), kiwi=importlib.metadata.version('kiwipiepy'),
                     model=importlib.metadata.version('kiwipiepy_model'), criteria_version=CRITERIA,
                     counts=predictions)
    output.mkdir(parents=True, exist_ok=False)
    for name, obj in [('pack.local.json', pack), ('predictions.private.json', reference),
                      ('review.local.json', empty_state(pack))]:
        (output / name).write_text(json.dumps(obj, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    return dict(items=len(selection), distinct_children=len({r['speaker_id'] for r in selection}),
                ages=dict(Counter(r['age'] for r in selection)), reviewed=0, source_sha256=fingerprint(pack))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    prepare = sub.add_parser('prepare')
    prepare.add_argument('--input', required=True, type=Path)
    prepare.add_argument('--seen-reference', required=True, type=Path)
    prepare.add_argument('--output', required=True, type=Path)
    serve = sub.add_parser('serve')
    serve.add_argument('--directory', required=True, type=Path)
    serve.add_argument('--port', default=8877, type=int)
    args = parser.parse_args()
    if args.command == 'prepare':
        print(json.dumps(build(args.input, args.seen_reference, args.output)))
    else:
        from eval.child_blind_review_server import serve_review
        serve_review(args.directory, args.port)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
