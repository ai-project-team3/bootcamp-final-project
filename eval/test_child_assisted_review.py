"""Assisted decisions must never become independent human gold."""
import json
import pytest
from eval.child_blind_review import empty_state, fingerprint
from eval.child_assisted_review import prepare, validate_assisted, assisted_summary
from eval.child_blind_review_server import make_server
from eval.test_child_blind_review_server import request
from threading import Thread


def fixture_pack():
    return {'items': [{'id': 'B001', 'text': '곰이 잤어.'}], 'mode': 'ai_assisted'}


def test_assisted_requires_reason_and_rejects_independent_claim():
    p = fixture_pack(); s = empty_state(p)
    s.update(reviewer='QA', mode='ai_assisted', reviews={'B001': {'status': 'hold', 'reason': ''}})
    with pytest.raises(ValueError): validate_assisted(p, s)
    s['reviews']['B001']['reason'] = 'Ending unclear'
    validate_assisted(p, s, finalize=True)
    s['independent'] = True
    with pytest.raises(ValueError): validate_assisted(p, s)
    s['independent'] = False
    s['reviews']['B001']['status'] = 'gold'
    with pytest.raises(ValueError): validate_assisted(p, s)


def test_summary_is_not_accuracy():
    p = fixture_pack(); s = empty_state(p)
    s.update(reviewer='QA', mode='ai_assisted', finalized=True,
             reviews={'B001': {'status': 'approve', 'reason': ''}})
    r = assisted_summary(p, s)
    assert r['approved'] == 1 and r['held'] == 0
    assert r['independent_blind_gold'] is False
    assert r['independent_accuracy'] is None
    assert 'exact_count_agreement' not in r


def test_prepare_preserves_old_state_and_server_roundtrip(tmp_path):
    source = tmp_path / 'source'; source.mkdir()
    p = fixture_pack()
    (source / 'pack.local.json').write_text(json.dumps(p), encoding='utf-8')
    old = source / 'review.local.json'; old.write_text('existing human work', encoding='utf-8')
    proposals = tmp_path / 'proposals.json'
    proposals.write_text(json.dumps({'source_sha256': fingerprint(p), 'author': 'AI assistant',
        'items': [{'id': 'B001', 'parts': ['곰', '이', '자', '았', '어'],
                   'explanations': ['noun', 'subject', 'stem', 'past', 'ending'], 'note': ''}]}), encoding='utf-8')
    target = tmp_path / 'assisted'
    prepare(source, proposals, target)
    assert old.read_text(encoding='utf-8') == 'existing human work'
    assert (source / 'exposure.local.json').exists()
    with pytest.raises(ValueError, match='now assisted'): make_server(source, 0)
    with pytest.raises(FileExistsError): prepare(source, proposals, target)
    server = make_server(target, 0, assisted=True)
    thread = Thread(target=server.serve_forever, daemon=True); thread.start()
    base = f'http://127.0.0.1:{server.server_port}'
    try:
        data = request(base, '/data')[1]
        assert data['items'][0]['parts'] == ['곰', '이', '자', '았', '어']
        s = data['state']; s['reviewer'] = 'QA'
        s['reviews'] = {'B001': {'status': 'approve', 'reason': ''}}
        assert request(base, '/save', s, origin=False)[0] == 403
        code, saved = request(base, '/save', s)
        assert code == 200 and saved['mode'] == 'ai_assisted' and not saved['independent']
        assert request(base, '/save', s)[0] == 409
        assert request(base, '/finalize', saved)[0] == 200
        r = request(base, '/result')[1]
        assert r['approved'] == 1 and r['independent_accuracy'] is None
        assert request(base, '/save', saved)[0] == 409
    finally:
        server.shutdown(); server.server_close(); thread.join()
