"""Write vocab.json + added_tokens.json from tokenizer.json so whisper.cpp's convert-h5-to-ggml.py can read them."""
import json
import sys
from pathlib import Path

for d in sys.argv[1:]:
    d = Path(d)
    tj = json.loads((d / "tokenizer.json").read_text(encoding="utf-8"))
    vocab = tj["model"]["vocab"]
    added = {t["content"]: t["id"] for t in tj.get("added_tokens", []) if t["content"] not in vocab}
    (d / "vocab.json").write_text(json.dumps(vocab, ensure_ascii=False), encoding="utf-8")
    (d / "added_tokens.json").write_text(json.dumps(added, ensure_ascii=False), encoding="utf-8")
    print(d.name, "vocab", len(vocab), "added", len(added))
