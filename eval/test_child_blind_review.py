"""Blind-review workflow checks use invented texts only."""
import pytest

from eval.child_blind_review import select_records, fingerprint, validate_state, compare


def rows():
    return [dict(speaker_id=f"{age}-{i}", age=age, text=f"예문 {age} {i}",
                 by="child", question_type="unknown") for age in range(3, 7) for i in range(20)]


def pack():
    return {"items": [{"id": "B001", "age": 3, "text": "곰이 자."},
                       {"id": "B002", "age": 4, "text": "모호한 말"}], "schema": 1}


def state(p):
    return {"source_sha256": fingerprint(p), "reviewer": "검수자", "independent": True,
            "criteria_accepted": True, "revision": 0, "finalized": False,
            "reviews": {"B001": {"status": "gold", "parts": ["곰", "이", "자"], "reason": ""},
                        "B002": {"status": "hold", "parts": [], "reason": "문맥 부족"}}}


def test_sample_has_age_quotas_distinct_children_and_no_seen_text():
    result = select_records(rows(), {"예문 3 0"}, seed=1012321)
    assert len(result) == len({r['speaker_id'] for r in result}) == 50
    assert [sum(r['age'] == age for r in result) for age in range(3, 7)] == [13, 13, 12, 12]
    assert all(r['text'] != '예문 3 0' for r in result)
    assert result == select_records(reversed(rows()), {"예문 3 0"}, seed=1012321)


def test_insufficient_sample_does_not_silently_reduce_quota():
    with pytest.raises(ValueError, match="sample"):
        select_records(rows()[:3], set(), seed=1)


def test_comparison_requires_complete_attested_frozen_gold():
    p = pack(); s = state(p)
    with pytest.raises(ValueError):
        compare(p, s, {"B001": 4, "B002": 2})
    s['finalized'] = True
    report = compare(p, s, {"B001": 4, "B002": 2})
    assert report['gold_items'] == 1 and report['held_items'] == 1
    assert report['coverage'] == .5
    assert report['exact_count_agreement'] == 0
    assert report['mean_absolute_count_error'] == 1
    assert "곰" not in str(report)


@pytest.mark.parametrize('mutation', ['missing', 'false_attestation', 'empty_part', 'wrong_source', 'extra'])
def test_invalid_final_gold_is_rejected(mutation):
    p = pack(); s = state(p)
    if mutation == 'missing': del s['reviews']['B002']
    if mutation == 'false_attestation': s['independent'] = False
    if mutation == 'empty_part': s['reviews']['B001']['parts'].append('')
    if mutation == 'wrong_source': s['source_sha256'] = 'wrong'
    if mutation == 'extra': s['reviews']['unexpected'] = s['reviews']['B001']
    with pytest.raises(ValueError): validate_state(p, s, finalize=True)


def test_all_held_is_reported_without_an_accuracy_number():
    p = pack(); s = state(p)
    s['reviews']['B001'] = {'status': 'hold', 'parts': [], 'reason': '미확정'}
    s['finalized'] = True
    result = compare(p, s, {'B001': 3, 'B002': 2})
    assert result['exact_count_agreement'] is None
    assert result['gold_items'] == 0
