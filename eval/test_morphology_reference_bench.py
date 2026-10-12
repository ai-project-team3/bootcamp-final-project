"""External-annotation contracts, failures and aggregation without child data."""
import pytest

from eval.language_metrics import Morpheme
from eval.morphology_reference_bench import evaluate_reference, parse_reference


def test_reference_contract_includes_suffixes_and_omits_symbols():
    _, tokens, count = parse_reference("건강히!\t건강/NNG 히/XSM !/SF")
    assert count == 2
    assert tokens[1] == Morpheme("히", "XSM")


@pytest.mark.parametrize("line", [
    "private text", "private text\t", "private text\tx/UNKNOWN",
    "private text\tmalformed", "private text\t/NNG", "\tx/NNG",
])
def test_malformed_annotations_fail_without_echoing_source(line):
    with pytest.raises(ValueError) as error:
        parse_reference(line)
    assert "private text" not in str(error.value)


def test_count_differences_are_not_token_accuracy(tmp_path):
    path = tmp_path / "reference.txt"
    path.write_text("곰이\t곰/NNG 이/JKS\n건강히\t건강/NNG 히/XSM\n", encoding="utf-8")
    def tokenize(text):
        # Wrong lexical forms can have the right count; another answer is short.
        return [Morpheme("다른", "NNG")] * (2 if text == "곰이" else 1)
    result = evaluate_reference(path, tokenize)
    assert result["arithmetic_oracle_checks_passed"] == 2
    assert result["exact_count_agreement"] == 0.5
    assert result["mean_absolute_count_error"] == 0.5
    assert result["mean_signed_count_error"] == -0.5
    assert "곰" not in str(result)


def test_empty_reference_is_not_success(tmp_path):
    path = tmp_path / "empty.txt"
    path.write_text("# no samples\n", encoding="utf-8")
    with pytest.raises(ValueError, match="no annotated"):
        evaluate_reference(path, lambda _: [])
