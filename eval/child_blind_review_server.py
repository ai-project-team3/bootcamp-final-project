"""Loopback-only UI: serve no private reference, freeze before revealing counts."""
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import threading

from eval.child_blind_review import compare, fingerprint, validate_state


def make_server(directory: Path, port: int) -> ThreadingHTTPServer:
    pack = json.loads((directory / 'pack.local.json').read_text(encoding='utf-8'))
    prediction = json.loads((directory / 'predictions.private.json').read_text(encoding='utf-8'))
    if prediction['source_sha256'] != fingerprint(pack):
        raise ValueError('Private prediction source mismatch')
    if pack.get('prediction_counts_sha256', fingerprint(prediction['counts'])) != fingerprint(prediction['counts']):
        raise ValueError('Frozen prediction counts changed')
    state_path = directory / 'review.local.json'
    initial = json.loads(state_path.read_text(encoding='utf-8'))
    if initial['source_sha256'] != fingerprint(pack):
        raise ValueError('Review source mismatch')
    lock = threading.Lock()
    page = Path(__file__).with_name('child_blind_review.html').read_bytes()

    def load():
        return json.loads(state_path.read_text(encoding='utf-8'))

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def local(self):
            return self.headers.get('Host') == f'127.0.0.1:{self.server.server_port}'

        def reply(self, status, obj, *, html=False):
            data = obj if html else json.dumps(obj, ensure_ascii=False).encode('utf-8')
            self.send_response(status)
            self.send_header('Content-Type', 'text/html; charset=utf-8' if html else 'application/json; charset=utf-8')
            self.send_header('Content-Length', str(len(data)))
            self.send_header('Cache-Control', 'no-store')
            self.send_header('X-Content-Type-Options', 'nosniff')
            self.send_header('Content-Security-Policy', "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; img-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'")
            self.end_headers(); self.wfile.write(data)

        def do_GET(self):
            if not self.local(): return self.reply(403, {'error': 'Local host only'})
            if self.path == '/': return self.reply(200, page, html=True)
            with lock:
                state = load()
                if self.path == '/data':
                    return self.reply(200, {'items': [{k:r[k] for k in ('id','text')} for r in pack['items']], 'state': state})
                if self.path == '/result':
                    if not state['finalized']: return self.reply(409, {'error': '정답 확정 전에는 자동 결과를 공개하지 않습니다.'})
                    return self.reply(200, compare(pack, state, prediction['counts']))
            return self.reply(404, {'error': 'Not found'})

        def do_POST(self):
            origin = f'http://127.0.0.1:{self.server.server_port}'
            if not self.local() or self.headers.get('Origin') != origin:
                return self.reply(403, {'error': 'Local same-origin requests only'})
            if self.path not in {'/save', '/finalize'} or self.headers.get('Content-Type') != 'application/json':
                return self.reply(400, {'error': 'Invalid request'})
            try:
                size = int(self.headers.get('Content-Length', 0))
                if not 0 < size < 500000: raise ValueError('Invalid size')
                incoming = json.loads(self.rfile.read(size))
                finalize = self.path == '/finalize'
                validate_state(pack, incoming, finalize=finalize)
                with lock:
                    current = load()
                    if current['finalized']:
                        return self.reply(409, {'error': '결과를 공개한 검수는 변경할 수 없습니다. 수정은 별도 재검수로 기록합니다.'})
                    if incoming['revision'] != current['revision']:
                        return self.reply(409, {'error': '다른 창에서 저장했습니다. 새로고침 후 이어가세요.'})
                    if current['reviews'] and incoming['reviewer'].strip() != current['reviewer']:
                        return self.reply(409, {'error': '검수 중에는 검수자를 바꿀 수 없습니다.'})
                    state = {k:incoming[k] for k in ('source_sha256','independent','criteria_accepted','reviews')}
                    state.update(reviewer=incoming['reviewer'].strip(), revision=current['revision']+1,
                                 finalized=finalize, updated_at=datetime.now(timezone.utc).isoformat())
                    if finalize:
                        result = compare(pack, state, prediction['counts'])
                        (directory / 'comparison.local.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
                    temp = state_path.with_suffix('.tmp')
                    temp.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding='utf-8')
                    os.replace(temp, state_path)
                return self.reply(200, state)
            except (ValueError, TypeError, KeyError):
                return self.reply(400, {'error': '검수자·분절·보류 사유를 확인하세요. 최종 확정에는 전체 항목과 두 확인란이 필요합니다.'})
            except OSError:
                return self.reply(500, {'error': '저장하지 못했습니다. 입력을 유지하고 다시 시도하세요.'})

    return ThreadingHTTPServer(('127.0.0.1', port), Handler)


def serve_review(directory: Path, port: int):
    server = make_server(directory, port)
    print(f'Blind review ready on http://127.0.0.1:{server.server_port}', flush=True)
    server.serve_forever()
