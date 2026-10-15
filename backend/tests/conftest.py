"""Shared by every test file."""
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.routers import tts as tts_route   # noqa: E402


@pytest.fixture(autouse=True)
def _empty_tts_cache():
    """/tts keeps lines in memory (10-14) — a line one test bought must not answer another test's request"""
    tts_route._cache.clear()
    yield
    tts_route._cache.clear()
