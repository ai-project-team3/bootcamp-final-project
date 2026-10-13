"""Loopback-only UI: serve no private reference, freeze before revealing counts."""
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
import socket
from pathlib import Path
import threading

from eval.child_blind_review import compare, fingerprint, validate_state


class ReviewServer(ThreadingHTTPServer):
    # Windows SO_REUSEADDR can route a second server's requests to the first.
    allow_reuse_address = False

    def server_bind(self):
        if hasattr(socket, 'SO_EXCLUSIVEADDRUSE'):
            self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
        super().server_bind()


def write_json_atomic(path, value):
    temp = path.with_suffix('.tmp')
    with temp.open('w', encoding='utf-8') as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(temp, path)


def make_server(directory: Path, port: int, *, assisted=False) -> ThreadingHTTPServer:
    if not assisted and (directory / 'exposure.local.json').exists():
        raise ValueError('This sample is now assisted; do not reuse it as independent blind gold')
    pack = json.loads((directory / ('assisted-pack.local.json' if assisted else 'pack.local.json')).read_text(encoding='utf-8'))
    if assisted:
        from eval.child_assisted_review import assisted_summary, validate_assisted
        validate = validate_assisted
        summarize = lambda state: assisted_summary(pack, state)
    else:
        prediction = json.loads((directory / 'predictions.private.json').read_text(encoding='utf-8'))
        if prediction['source_sha256'] != fingerprint(pack):
            raise ValueError('Private prediction source mismatch')
        if pack.get('prediction_counts_sha256', fingerprint(prediction['counts'])) != fingerprint(prediction['counts']):
            raise ValueError('Frozen prediction counts changed')
        validate = validate_state
        summarize = lambda state: compare(pack, state, prediction['counts'])
    state_path = directory / ('assisted-review.local.json' if assisted else 'review.local.json')
    result_path = directory / ('assisted-summary.local.json' if assisted else 'comparison.local.json')
    initial = json.loads(state_path.read_text(encoding='utf-8'))
    if initial['source_sha256'] != fingerprint(pack):
        raise ValueError('Review source mismatch')
    lock = threading.Lock()
    page = Path(__file__).with_name('child_assisted_review.html' if assisted else 'child_blind_review.html').read_bytes()

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
                    rows = pack['items'] if assisted else [{k:r[k] for k in ('id','text')} for r in pack['items']]
                    return self.reply(200, {'items': rows, 'state': state})
                if self.path == '/result':
                    if not state['finalized']: return self.reply(409, {'error': '정답 확정 전에는 자동 결과를 공개하지 않습니다.'})
                    result = summarize(state)
                    try:
                        write_json_atomic(result_path, result)
                    except OSError:
                        message = '검토는 완료됐지만 현황 저장에 실패했습니다. 새로고침으로 다시 시도하세요.' if assisted else '정답은 확정됐지만 비교 보고서 저장에 실패했습니다. 새로고침으로 다시 시도하세요.'
                        return self.reply(500, {'error': message})
                    return self.reply(200, result)
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
                validate(pack, incoming, finalize=finalize)
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
                    if assisted:
                        state.update(mode='ai_assisted', independent=False, criteria_accepted=False)
                    if finalize:
                        result = summarize(state)
                    # Freeze durably before any private counts reach an export file.
                    write_json_atomic(state_path, state)
                    if finalize:
                        try:
                            write_json_atomic(result_path, result)
                        except OSError:
                            # The human decision stays frozen; GET /result retries export.
                            pass
                return self.reply(200, state)
            except (ValueError, TypeError, KeyError):
                message = '검토자와 보류 사유를 확인하세요. 완료하려면 모든 항목을 결정해야 합니다.' if assisted else '검수자·분절·보류 사유를 확인하세요. 최종 확정에는 전체 항목과 두 확인란이 필요합니다.'
                return self.reply(400, {'error': message})
            except OSError:
                return self.reply(500, {'error': '저장하지 못했습니다. 입력을 유지하고 다시 시도하세요.'})

    return ReviewServer(('127.0.0.1', port), Handler)


def serve_review(directory: Path, port: int):
    server = make_server(directory, port)
    print(f'Blind review ready on http://127.0.0.1:{server.server_port}', flush=True)
    server.serve_forever()
