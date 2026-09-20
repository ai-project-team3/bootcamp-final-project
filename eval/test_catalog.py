from __future__ import annotations

import os
import unittest
from unittest.mock import patch

from eval.config import load_catalog
from eval.providers.mistral_adapter import MistralAdapter


class MistralCatalogTest(unittest.TestCase):
    def test_official_model_ids_and_prices(self) -> None:
        models = load_catalog()["models"]
        self.assertEqual(models["mistral-small-4"]["api_model"], "mistral-small-2603")
        self.assertEqual(models["mistral-small-4"]["input_usd_per_mtok"], 0.15)
        self.assertEqual(models["mistral-small-4"]["output_usd_per_mtok"], 0.60)
        self.assertEqual(models["ministral-3-3b"]["api_model"], "ministral-3b-2512")
        self.assertEqual(models["ministral-3-3b"]["input_usd_per_mtok"], 0.10)
        self.assertEqual(models["ministral-3-3b"]["output_usd_per_mtok"], 0.10)

    def test_stream_requests_usage_tokens(self) -> None:
        captured = {}

        def fake_post_sse(**kwargs):
            captured.update(kwargs)
            yield {
                "usage": {"prompt_tokens": 12, "completion_tokens": 4},
                "choices": [{"delta": {"content": "{}"}}],
            }

        with patch.dict(os.environ, {"MISTRAL_API_KEY": "test-only"}), patch(
            "eval.providers.mistral_adapter.post_sse", side_effect=fake_post_sse
        ):
            result = MistralAdapter().stream_judge(
                model="ministral-3b-2512",
                system_prompt="system",
                user_prompt="user",
                schema={"type": "object"},
            )
            self.assertEqual([chunk.text for chunk in result.chunks], ["{}"])

        self.assertEqual(captured["payload"]["stream_options"], {"include_usage": True})
        self.assertEqual(result.usage.input_tokens, 12)
        self.assertEqual(result.usage.output_tokens, 4)


if __name__ == "__main__":
    unittest.main()
