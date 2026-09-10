"""Settings come from the environment. Never hardcode keys or DSNs."""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    mysql_host: str = "localhost"
    mysql_port: int = 3306
    mysql_user: str = ""
    mysql_password: str = ""
    mysql_database: str = ""

    anthropic_api_key: str = ""
    stt_provider: str = ""  # decided by the W1 spike
    stt_api_key: str = ""
    embedding_provider: str = ""
    embedding_api_key: str = ""

    template_dir: str = "backend/app/templates"
    fixture_dir: str = "fixtures"

    # Tuning targets. Confirmed against fixtures in W3, not settled constants.
    confidence_threshold: float = 0.7
    recurrence_cooldown_sec: int = 30
    card_max_rank: int = 2

    room_code_ttl_sec: int = 3600
    cors_origins: str = ""

    @property
    def database_url(self) -> str:
        return (
            f"mysql+pymysql://{self.mysql_user}:{self.mysql_password}"
            f"@{self.mysql_host}:{self.mysql_port}/{self.mysql_database}?charset=utf8mb4"
        )

    @property
    def cors_origin_list(self) -> list[str]:
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]


settings = Settings()
