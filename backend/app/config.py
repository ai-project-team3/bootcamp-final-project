"""Settings. Model ids are settings, never constants — W1 measurement may swap them."""
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

REPO = Path(__file__).resolve().parents[2]


class Settings(BaseSettings):
    # The one .env lives at the repo root (gitignored, one per person). Reading it by
    # absolute path means `uvicorn` works from backend/ or from the root alike.
    model_config = SettingsConfigDict(env_file=REPO / ".env", extra="ignore")

    # MOCK=1: every route answers the spec's shape with fixed values, no keys, no GPU.
    # Teammates wire the app against this while the real server runs on the lead's PC.
    mock: bool = False

    llm_provider: str = "openai"
    # 09-25 measured: same 100 questions as gpt-5.6-luna, quality equal or better on
    # four fields, nothing worse, half the price (10.5 vs 21.6 KRW per book). results.md.
    llm_model: str = "gpt-6-luna"
    openai_api_key: str = ""
    openai_base_url: str = "https://api.openai.com/v1"
    llm_effort_judge: str = "none"
    # Modes whose judge runs on TypeSafe Jev (comma list, e.g. "story"; empty = luna everywhere). Jev answers
    # the nine choice/yes-no fields in p50 0.25 s vs luna 2.27 s (10-05 eval/bench_jev_turn.py); it writes no
    # text, so one answer filling two slots keeps only the first and value_1 is the child's words uncut —
    # that broke 2 co-op cases, none in story. Per mode because the team server is shared. Internal tests
    # only: text goes to a vendor not in the privacy policy yet (docs/배경_조각_목록.md §6-4).
    # Any Jev failure falls back to luna.
    judge_jev_modes: str = ""
    typesafe_api_key: str = ""
    jev_model: str = "jev-latest"
    # medium since 10-05: 3 stories x 2 — high p50 36.9 s · medium 12.6 s · low 8.0 s, rejected 0 at all three,
    # captions read alike (low repeated a line); eval/results.md 10-05 / eval/bench_story_title.py
    llm_effort_story: str = "medium"
    # the mascot line waits on the judge, so it gets the judge's latency budget
    llm_effort_line: str = "none"

    # 09-23 measured: large-v3 halves CER for ages 3-6 against turbo (57->37% at 3,
    # 26->15% at 5) for +0.23s. .env is gitignored, so this default is what a fresh
    # checkout or CI actually runs with — it has to be the confirmed model, not turbo.
    stt_model: str = "large-v3"
    stt_device: str = "cuda"
    stt_compute_type: str = "int8_float16"    # what combination A was measured with
    stt_warmup: bool = True                   # load at start; first calls raced the load and 502-ed (09-29)
    # lead-only test switch: keep audio + text here to hear what the model heard. Never with a child.
    stt_debug_dir: str = ""
    # #149 (10-06): below this segment avg_logprob the phone asks the child once more. -0.8 re-asks 16% of
    # 3-6 year olds' answers, 82% of them truly misheard (CER > 20%); -0.6 catches twice the garbled ones
    # for 29% re-asked. Free — word probabilities would cost +3.2 s at p95 (eval/results.md 10-06 #149)
    stt_unsure_below: float = -0.8

    # 09-25 vendor, 09-28 team scores: Ruri (smart) was the only 12/12. The lineup is
    # still open, so the voice is a setting and the app may pass its own voice_id (TypeCast only — OpenAI ignores it).
    typecast_api_key: str = ""
    typecast_model: str = "ssfm-v30"
    typecast_voice_id: str = "tc_6699eb3849dfac016c29444c"   # Siwoo (10-01 조장 · was Ruri tc_65a8c82a7e7bded32947497e)

    # 09-21 recipe (results.md): SDXL base + Lightning 8-step, 3.74 s alone · 3.86 s beside large-v3
    comfy_url: str = "http://127.0.0.1:8188"
    image_ckpt: str = "sd_xl_base_1.0.safetensors"
    image_lora: str = "sdxl_lightning_8step_lora.safetensors"
    # under the app's 15 s preset line (rule 8), so the answer lands before the app gives up
    image_deadline_s: float = 13.0
    # A redraw has no child waiting on it — the diary shows it at the next brush pause, so it may
    # wait behind the story pictures and still arrive (#32, 10-02). Phone waits 50 s.
    redraw_deadline_s: float = 45.0
    # Every server deadline sits under the phone's wait (net/Server.kt), so the server answers
    # first — a verdict, a fallback or an error — instead of the phone giving up mid-call (10-01).
    turn_deadline_s: float = 25.0        # phone waits 30 s · judge ≤ 18 s, the line gets what is left
    judge_deadline_s: float = 18.0
    story_deadline_s: float = 55.0       # phone waits 60 s · effort high: 10-01 a book passed 30 s → 502
    tts_deadline_s: float = 15.0         # phone waits 20 s
    # Who makes the mascot's voice. 10-01: TypeCast answered 403 UNUSUAL_ACTIVITY_DETECTED on the
    # new key (the free account ran out, the next free account was flagged) — OpenAI for now, the
    # vendor already in the privacy policy, same key. Back to "typecast" once that account is sorted.
    # 10-01 later: a new TypeCast key — TypeCast first again, and if it fails (402 · 403 · slow)
    # the same request falls back to OpenAI, so the mascot never goes quiet over an account issue
    # 10-01 evening, 조장: OpenAI only. A failed TypeCast try before every line cost time, and
    # rotating free keys risks the account being suspended. The fixed lines are baked with this
    # same voice into the app (eval/bake_lines.py), so the live ones must match it.
    tts_provider: str = "openai"         # openai | typecast | elevenlabs
    tts_fallback: str = ""               # "" = no fallback
    # 10-06 조장: TypeCast only for families who gave the optional consent (third-party provision · in-app
    # terms TYPECAST_VOICE). Off until the paid plan and the re-baked lines are ready — the terms say so.
    typecast_opt_in: bool = False
    # 조장 10-01: Siwoo · 밝게 (09-26 blind ★) but a little fast — 0.95 until the ear test
    # (eval/bench_tts_tempo.py) settles it
    typecast_emotion: str = "happy"      # a preset name, or "smart" (reads the neighbouring lines)
    typecast_tempo: float = 0.95
    # 10-01 조장 pick: sage on the pinned snapshot with a short "playful" instruction — the only
    # combination that rose into a child's pitch range (median F0 ~262-296 Hz vs ~210 on the current
    # model with the same words · eval/voice_pitch.py, eval/bench_tts_openai_voices3.py). OpenAI has
    # no child voice; the snapshot follows "playful, like talking with a young child" by going higher.
    # ⚠️ a pinned snapshot can be retired — bake the fixed lines while it exists
    # 10-06: Eleven v4 is wired so a measured switch is one .env line (eval/bench_tts_eleven_v4.py).
    # Not the default — 10-01 dropped ElevenLabs Flash by ear and pitch, and v4 is not measured yet.
    elevenlabs_api_key: str = ""
    elevenlabs_model: str = "eleven_v4_turbo"
    elevenlabs_voice_id: str = ""      # a Korean voice id, picked by the ear test
    openai_tts_model: str = "gpt-4o-mini-tts-2025-03-20"
    openai_tts_voice: str = "sage"
    openai_tts_instructions: str = ("Warm, friendly and playful. Speak naturally, like talking with a young child. "
                                    "Not too fast.")
    image_warmup: bool = True            # draw one picture at start so models are loaded
    # rule 8: our image model has no safety filter — every picture is checked
    moderation_model: str = "omni-moderation-latest"

    # 10-06 Play build: the whole server's paid spend per day (app/limits.py). 0 = off.
    # About 15 testers at the phone's own limit (2 books ≈ 300 won a day) stay well under it.
    daily_cap_krw: float = 10_000.0
    daily_cap_state: str = ""            # where the day's total is kept; empty = backend/daily_cap.json


    # ── admin sign-in for the endpoint monitor (app/admin.py · 10-07) ──
    # No default: with no hash the monitor and /stats stay locked. Make the hash with
    # `py backend/scripts/admin_password.py` and put both lines in the server's .env.
    admin_user: str = ""
    admin_password_hash: str = ""
    # Optional separate key for the session cookie; empty = the password hash (a new password signs everyone out)
    admin_session_secret: str = ""
    # The public site is https; False only for a plain-http local test
    admin_cookie_secure: bool = True

    # ── problem reports from the parent area (app/reports.py · #283) ──
    # Where reports and their pictures are kept; empty = backend/reports/ (gitignored). PC1: /state/reports,
    # which is C:\otto\state\reports on the host, so a redeploy keeps them (#240).
    report_dir: str = ""

settings = Settings()
