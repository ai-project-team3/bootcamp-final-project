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
    llm_effort_story: str = "high"
    # the mascot line waits on the judge, so it gets the judge's latency budget
    llm_effort_line: str = "none"

    stt_provider: str = "local"
    # 09-23 measured: large-v3 halves CER for ages 3-6 against turbo (57->37% at 3,
    # 26->15% at 5) for +0.23s. .env is gitignored, so this default is what a fresh
    # checkout or CI actually runs with — it has to be the confirmed model, not turbo.
    stt_model: str = "large-v3"
    stt_device: str = "cuda"
    stt_compute_type: str = "int8_float16"    # what combination A was measured with

    # 09-25 vendor, 09-28 team scores: Ruri (smart) was the only 12/12. The lineup is
    # still open, so the voice is a setting and the app may pass its own voice_id.
    typecast_api_key: str = ""
    typecast_model: str = "ssfm-v30"
    typecast_voice_id: str = "tc_65a8c82a7e7bded32947497e"

    # 09-21 recipe (results.md): SDXL base + Lightning 8-step, 3.74 s alone · 3.86 s beside large-v3
    comfy_url: str = "http://127.0.0.1:8188"
    image_ckpt: str = "sd_xl_base_1.0.safetensors"
    image_lora: str = "sdxl_lightning_8step_lora.safetensors"
    # under the app's 15 s preset line (rule 8), so the answer lands before the app gives up
    image_deadline_s: float = 13.0
    # rule 8: our image model has no safety filter — every picture is checked
    moderation_model: str = "omni-moderation-latest"

    port: int = 8000


settings = Settings()
