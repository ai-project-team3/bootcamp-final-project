"""Add a `-mt N` flag to whisper-cli that sets whisper_full_params.max_tokens (tokens per segment) — the app would set the same field."""
p = r"whisper.cpp/examples/cli/cli.cpp"
t = open(p, encoding="utf-8").read()


def one(a, b):
    global t
    assert t.count(a) == 1, a
    t = t.replace(a, b)


one("    int32_t audio_ctx     = 0;\n", "    int32_t audio_ctx     = 0;\n    int32_t max_tokens    = 0;\n")
one('        else if (arg == "-ac"   || arg == "--audio-ctx")            { params.audio_ctx       = std::stoi(ARGV_NEXT); }\n',
    '        else if (arg == "-ac"   || arg == "--audio-ctx")            { params.audio_ctx       = std::stoi(ARGV_NEXT); }\n'
    '        else if (arg == "-mt"   || arg == "--max-tokens")           { params.max_tokens      = std::stoi(ARGV_NEXT); }\n')
one("            wparams.audio_ctx        = params.audio_ctx;\n",
    "            wparams.audio_ctx        = params.audio_ctx;\n            wparams.max_tokens       = params.max_tokens;\n")
open(p, "w", encoding="utf-8", newline="").write(t)
print("ok")
