"""Settings. Model ids are settings, never constants — W1 measurement may swap them."""
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    llm_provider: str = "openai"
    llm_model: str = "gpt-5.6-luna"
    llm_api_key: str = ""
    llm_effort_judge: str = "none"
    llm_effort_story: str = "high"

    stt_provider: str = "local"
    stt_model: str = "large-v3-turbo"
    stt_api_key: str = ""
    whisper_server_url: str = "http://127.0.0.1:9000"

    port: int = 8000

    class Config:
        env_file = ".env"


settings = Settings()
