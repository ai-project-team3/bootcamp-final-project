"""Independent morphology fixtures for the #320 measurement contract."""
import pytest

from eval.language_metrics import Morpheme, Utterance, measure


def tokenize(text):
    return {
        "곰이 잤어.": [Morpheme("곰", "NNG"), Morpheme("이", "JKS"),
                     Morpheme("자", "VV"), Morpheme("었", "EP"),
                     Morpheme("어", "EF"), Morpheme(".", "SF")],
        "곰은 자.": [Morpheme("곰", "NNG"), Morpheme("은", "JX"),
                   Morpheme("자", "VV"), Morpheme(".", "SF")],
        "!!!": [Morpheme("!", "SF")] * 3,
        "비가 와서 갔어": [Morpheme("비", "NNG"), Morpheme("가", "JKS"),
                        Morpheme("오", "VV"), Morpheme("아서", "EC"),
                        Morpheme("가", "VV"), Morpheme("었", "EP"), Morpheme("어", "EF")],
        "왜냐하면": [Morpheme("왜냐하면", "MAJ")],
        "니까라는 이름": [Morpheme("니까", "NNP"), Morpheme("라는", "ETM"), Morpheme("이름", "NNG")],
        "응": [Morpheme("응", "IC")],
        "run": [Morpheme("run", "SL")],
    }[text]


def answer(text, by="child", question_type="open"):
    return Utterance(text, by, question_type)


def test_mlu_counts_morphology_and_ndw_collapses_inflections():
    result = measure([answer("곰이 잤어."), answer("곰은 자.")], tokenize)
    assert result.utterances == 2
    assert result.morphemes == 8
    assert result.mlu_m == 4
    assert result.ndw == 2
    assert result.content_tokens == 4


@pytest.mark.parametrize("by,kind,reason", [
    ("card", "open", "card"), ("mascot", "open", "mascot"),
    ("child", "choice", "not_open"), ("child", "confirmation", "not_open"),
    ("child", "unknown", "not_open"),
])
def test_excluded_inputs_never_reach_the_analyzer(by, kind, reason):
    def forbidden(_):
        raise AssertionError("Excluded words reached the analyzer")
    result = measure([answer("not in the fixture", by, kind)], forbidden)
    assert result.mlu_m is None
    assert result.excluded[reason] == 1


def test_punctuation_and_empty_answers_do_not_become_zero_length_samples():
    result = measure([answer(""), answer("!!!"), answer("곰은 자.")], tokenize)
    assert result.utterances == 1
    assert result.mlu_m == 3
    assert result.excluded["empty"] == 2


def test_marker_candidates_need_a_matching_part_of_speech():
    result = measure([answer("비가 와서 갔어"), answer("왜냐하면"), answer("니까라는 이름")], tokenize)
    assert result.causal_utterances == 2
    assert result.causal_markers == 2


def test_all_child_scope_is_explicit_and_still_excludes_proxy_speech():
    result = measure([answer("응", question_type="choice"), answer("응", "card")], tokenize, scope="all_child")
    assert result.utterances == 1
    assert result.mlu_m == 1
    assert result.excluded["card"] == 1


def test_symbols_do_not_count_as_content_words_and_foreign_text_is_flagged():
    result = measure([answer("run")], tokenize)
    assert result.morphemes == 1
    assert result.foreign_utterances == 1
    assert result.ndw == 0


@pytest.mark.parametrize("by,kind", [("parent", "open"), ("child", "guess")])
def test_unknown_provenance_or_question_type_is_rejected(by, kind):
    with pytest.raises(ValueError):
        measure([answer("응", by, kind)], tokenize)


def test_empty_sample_is_unknown_not_a_low_score():
    result = measure([], tokenize)
    assert result.mlu_m is None
    assert result.ndw == 0
    assert result.utterances == 0


def test_a_multi_sentence_microphone_reply_is_one_answer():
    result = measure([answer("곰이 잤어.")], lambda _: tokenize("곰이 잤어.") * 2)
    assert result.utterances == 1
    assert result.mlu_m == 10


def test_kiwi_adapter_integration_keeps_case_endings_and_drops_punctuation():
    pytest.importorskip("kiwipiepy")
    from eval.language_metrics import kiwi_tokenizer
    analyze = kiwi_tokenizer()
    # The standalone "곰이" can be a proper name. Complete predicates remove
    # that ambiguity; analyzer correctness still needs real-sample inspection.
    assert measure([answer("곰이 잤어.")], analyze).mlu_m == 5
    assert measure([answer("곰이 잤어!!!")], analyze).mlu_m == 5
    assert measure([answer("곰이 잤어."), answer("곰은 잤어.")], analyze).ndw == 2
