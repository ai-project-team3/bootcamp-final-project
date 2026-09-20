from __future__ import annotations

from dataclasses import dataclass
from typing import Iterator, Protocol


@dataclass
class StreamChunk:
    text: str


@dataclass
class Usage:
    input_tokens: int = 0
    output_tokens: int = 0


@dataclass
class ProviderResult:
    chunks: Iterator[StreamChunk]
    usage: Usage


class JudgeProvider(Protocol):
    provider_name: str

    def stream_judge(
        self,
        *,
        model: str,
        system_prompt: str,
        user_prompt: str,
        schema: dict,
        max_output_tokens: int = 256,
        reasoning_effort: str | None = None,
    ) -> ProviderResult:
        ...
