from __future__ import annotations

from .anthropic_adapter import AnthropicAdapter
from .mistral_adapter import MistralAdapter
from .mock import MockProvider
from .openai_adapter import OpenAIAdapter
from .typesafe_adapter import TypeSafeAdapter


def create_provider(name: str):
    if name == "mock":
        return MockProvider()
    if name == "openai":
        return OpenAIAdapter()
    if name == "anthropic":
        return AnthropicAdapter()
    if name == "mistral":
        return MistralAdapter()
    if name == "typesafe":
        return TypeSafeAdapter()
    raise ValueError(f"unknown provider: {name}")


__all__ = ["MockProvider", "OpenAIAdapter", "AnthropicAdapter", "MistralAdapter", "TypeSafeAdapter", "create_provider"]
