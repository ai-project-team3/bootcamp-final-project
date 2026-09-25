"""Settings. Model ids are settings, never constants — W1 measurement may swap them."""
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    llm_provider: str = "openai"
    # 09-25 measured: same 100 questions as gpt-5.6-luna, quality equal or better on
    # four fields, nothing worse, half the price (10.5 vs 21.6 KRW per book). results.md.
    llm_model: str = "gpt-6-luna"
    llm_api_key: str = ""
    llm_effort_judge: str = "none"
    llm_effort_story: str = "high"

    stt_provider: str = "local"
    # 09-23 measured: large-v3 halves CER for ages 3-6 against turbo (57->37% at 3,
    # 26->15% at 5) for +0.23s. .env is gitignored, so this default is what a fresh
    # checkout or CI actually runs with — it has to be the confirmed model, not turbo.
    stt_model: str = "large-v3"
    stt_api_key: str = ""
    whisper_server_url: str = "http://127.0.0.1:9000"

    port: int = 8000

    class Config:
        env_file = ".env"


settings = Settings()
