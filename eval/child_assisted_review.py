"""Review AI-authored proposals without treating agreement as independent gold."""
import argparse
import json
from pathlib import Path
from eval.child_blind_review import empty_state, fingerprint


def validate_assisted(pack, state, *, finalize=False):
    if not isinstance(state, dict) or state.get('source_sha256') != fingerprint(pack):
        raise ValueError('Source mismatch')
    if state.get('mode') != 'ai_assisted' or state.get('independent') is not False:
        raise ValueError('Assisted decisions cannot be independent gold')
    name = state.get('reviewer')
    if not isinstance(name, str) or not name.strip() or len(name) > 80:
        raise ValueError('Reviewer required')
    if type(state.get('revision')) is not int or state['revision'] < 0:
        raise ValueError('Invalid revision')
    ids = {r['id'] for r in pack['items']}
    reviews = state.get('reviews')
    if not isinstance(reviews, dict) or set(reviews) - ids:
        raise ValueError('Invalid item IDs')
    for entry in reviews.values():
        if not isinstance(entry, dict) or set(entry) != {'status', 'reason'}:
            raise ValueError('Invalid decision')
        if entry['status'] not in {'approve', 'hold'}:
            raise ValueError('Not a gold annotation workflow')
        reason = entry['reason']
        if not isinstance(reason, str) or len(reason) > 4000:
            raise ValueError('Invalid reason')
        if entry['status'] == 'hold' and not reason.strip():
            raise ValueError('Hold requires a reason')
    if finalize and set(reviews) != ids:
        raise ValueError('Complete decisions first')


def assisted_summary(pack, state):
    if state.get('finalized') is not True:
        raise ValueError('Finish review first')
    validate_assisted(pack, state, finalize=True)
    approved = sum(e['status'] == 'approve' for e in state['reviews'].values())
    return dict(kind='ai_assisted_proposal_review', independent_blind_gold=False,
                independent_accuracy=None, items=len(pack['items']), approved=approved,
                held=len(pack['items']) - approved,
                held_ids=[k for k, e in state['reviews'].items() if e['status'] == 'hold'],
                source_sha256=fingerprint(pack), frozen_review_sha256=fingerprint(state))


def prepare(source: Path, proposals: Path, output: Path):
    original = json.loads((source / 'pack.local.json').read_text(encoding='utf-8'))
    draft = json.loads(proposals.read_text(encoding='utf-8'))
    if draft['source_sha256'] != fingerprint(original):
        raise ValueError('Proposals/source mismatch')
    lookup = {r['id']: r for r in draft['items']}
    if len(lookup) != len(draft['items']) or set(lookup) != {r['id'] for r in original['items']}:
        raise ValueError('Proposals must cover each source item exactly once')
    items = []
    for row in original['items']:
        p = lookup[row['id']]
        parts, explanations = p['parts'], p['explanations']
        if not isinstance(parts, list) or not parts or len(parts) != len(explanations):
            raise ValueError('Each part needs its own explanation')
        if not all(isinstance(t, str) and t.strip() for t in parts + explanations):
            raise ValueError('Empty proposal')
        if not isinstance(p['note'], str):
            raise ValueError('Invalid proposal note')
        items.append(dict(id=row['id'], text=row['text'], parts=parts,
                          explanations=explanations, note=p['note']))
    pack = dict(mode='ai_assisted', author=draft['author'], source_sha256=fingerprint(original), items=items)
    state = empty_state(pack)
    state.update(mode='ai_assisted', independent=False)
    # Never migrate or rewrite prior human decisions as approvals.
    output.mkdir(parents=True, exist_ok=False)
    from eval.child_blind_review_server import write_json_atomic
    write_json_atomic(source / 'exposure.local.json', dict(
        source_sha256=fingerprint(original), mode='ai_assisted',
        reason='AI suggestions prepared; retire this sample from independent blind review'))
    for name, value in [('assisted-pack.local.json', pack), ('assisted-review.local.json', state)]:
        (output / name).write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    build = sub.add_parser('prepare')
    build.add_argument('--source', type=Path, required=True)
    build.add_argument('--proposals', type=Path, required=True)
    build.add_argument('--output', type=Path, required=True)
    serve = sub.add_parser('serve')
    serve.add_argument('--directory', type=Path, required=True)
    serve.add_argument('--port', type=int, default=8882)
    args = parser.parse_args()
    if args.command == 'prepare':
        prepare(args.source, args.proposals, args.output)
    else:
        from eval.child_blind_review_server import make_server
        with make_server(args.directory, args.port, assisted=True) as server:
            print(f'Assisted review ready on http://127.0.0.1:{server.server_port}', flush=True)
            server.serve_forever()


if __name__ == '__main__':
    main()
