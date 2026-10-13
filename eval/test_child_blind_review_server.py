"""Verify local blind-review boundaries using synthetic inputs and loopback HTTP."""
from contextlib import contextmanager
import json
import pytest
from threading import Thread
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from eval.child_blind_review import empty_state, fingerprint
from eval.child_blind_review_server import make_server
from eval import child_blind_review_server as server_module


@contextmanager
def running(tmp_path):
    pack = {'schema': 1, 'items': [{'id': 'B001', 'age': 3, 'text': '곰이 잤어.'}]}
    for name, value in [('pack.local.json', pack), ('review.local.json', empty_state(pack)),
                        ('predictions.private.json', {'source_sha256': fingerprint(pack), 'counts': {'B001': 5}})]:
        (tmp_path / name).write_text(json.dumps(value), encoding='utf-8')
    server = make_server(tmp_path, 0)
    thread = Thread(target=server.serve_forever, daemon=True); thread.start()
    try: yield f'http://127.0.0.1:{server.server_port}', pack
    finally: server.shutdown(); server.server_close(); thread.join()


def request(base, path, data=None, origin=True):
    headers = {'Content-Type': 'application/json'}
    if origin: headers['Origin'] = base
    req = Request(base + path, data=None if data is None else json.dumps(data).encode(), headers=headers)
    try:
        with urlopen(req) as response: return response.status, json.loads(response.read())
    except HTTPError as error: return error.code, json.loads(error.read())


def test_api_hides_predictions_and_age_before_gold(tmp_path):
    with running(tmp_path) as (base, pack):
        status, data = request(base, '/data')
        assert status == 200
        assert set(data['items'][0]) == {'id', 'text'}
        assert 'counts' not in str(data)
        assert request(base, '/predictions.private.json')[0] == 404
        assert request(base, '/result')[0] == 409


def test_server_rejects_an_already_bound_port(tmp_path):
    with running(tmp_path) as (base, pack):
        with pytest.raises(OSError):
            other = make_server(tmp_path, int(base.rsplit(':', 1)[1]))
            other.server_close()


def test_save_finalize_freeze_and_origin_protection(tmp_path):
    with running(tmp_path) as (base, pack):
        s = empty_state(pack)
        s.update(reviewer='QA', independent=True, criteria_accepted=True,
                 reviews={'B001': {'status': 'gold', 'parts': ['곰', '이', '자', '았', '어'], 'reason': ''}})
        assert request(base, '/save', s, origin=False)[0] == 403
        assert request(base, '/save', s)[0] == 200
        assert request(base, '/save', s)[0] == 409
        s['revision'] = 1
        code, final = request(base, '/finalize', s)
        assert code == 200 and final['finalized']
        assert request(base, '/result')[1]['exact_count_agreement'] == 1
        s['revision'] = 2
        assert request(base, '/save', s)[0] == 409


def test_failed_freeze_does_not_publish_comparison(tmp_path, monkeypatch):
    with running(tmp_path) as (base, pack):
        s = empty_state(pack)
        s.update(reviewer='QA', independent=True, criteria_accepted=True,
                 reviews={'B001': {'status': 'gold', 'parts': ['곰', '이', '자', '았', '어'], 'reason': ''}})
        original = server_module.os.replace
        def fail_state(source, target):
            if target.name == 'review.local.json':
                raise OSError('Synthetic disk failure')
            original(source, target)
        with monkeypatch.context() as patch:
            patch.setattr(server_module.os, 'replace', fail_state)
            assert request(base, '/finalize', s)[0] == 500
        assert not (tmp_path / 'comparison.local.json').exists()
        assert not request(base, '/data')[1]['state']['finalized']
        assert request(base, '/result')[0] == 409
        assert request(base, '/save', s)[0] == 200


def test_failed_comparison_export_keeps_frozen_state_and_can_retry(tmp_path, monkeypatch):
    with running(tmp_path) as (base, pack):
        s = empty_state(pack)
        s.update(reviewer='QA', independent=True, criteria_accepted=True,
                 reviews={'B001': {'status': 'gold', 'parts': ['곰', '이', '자', '았', '어'], 'reason': ''}})
        original = server_module.os.replace
        def fail_export(source, target):
            if target.name == 'comparison.local.json':
                raise OSError('Synthetic export failure')
            original(source, target)
        with monkeypatch.context() as patch:
            patch.setattr(server_module.os, 'replace', fail_export)
            assert request(base, '/finalize', s)[0] == 200
            assert request(base, '/result')[0] == 500
        assert request(base, '/data')[1]['state']['finalized']
        assert request(base, '/save', s)[0] == 409
        assert request(base, '/result')[1]['exact_count_agreement'] == 1
        assert json.loads((tmp_path / 'comparison.local.json').read_text(encoding='utf-8'))['exact_count_agreement'] == 1
