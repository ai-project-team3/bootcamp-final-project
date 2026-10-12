package com.example.finalproject_demo.demo

import java.text.Normalizer
import java.util.Locale

data class PickedExpression(val text: String, val count: Int)

/** Surface-word observations, not MLU-m, lemma-based NDW, or an age norm. */
data class SpeechObservations(
    val recordedAnswers: Int,
    val windowAnswers: Int,
    val meanWords: Double?,
    val distinctWords: Int,
    val reasonAnswers: Int,
    val choices: List<PickedExpression>,
)

private fun surfaceWords(text: String): List<String> = Normalizer.normalize(text, Normalizer.Form.NFC)
    .split(Regex("\\s+"))
    .map { it.trim { ch -> !ch.isLetterOrDigit() }.lowercase(Locale.ROOT) }
    .filter(String::isNotEmpty)

/** The last 50 eligible answers keep the vocabulary window comparable in size, not norm-valid. */
fun summarizeSpeech(lines: List<TalkLine>): SpeechObservations {
    val eligible = lines.filter {
        it.who == "child" && it.questionType in setOf(ReportQuestionType.OPEN, ReportQuestionType.REASON) &&
            !storyNonAnswer(it.text) && surfaceWords(it.text).isNotEmpty()
    }
    val window = eligible.takeLast(50)
    val tokens = window.map { surfaceWords(it.text) }
    val selections = lines.filter {
        it.who in setOf("child", "card") && it.questionType == ReportQuestionType.CHOICE
    }.mapNotNull {
        val text = Normalizer.normalize(it.text, Normalizer.Form.NFC).trim()
            .replace(Regex("\\s+"), " ").trim { ch -> !ch.isLetterOrDigit() }
        text.takeIf(String::isNotEmpty)
    }.groupingBy { it }.eachCount().map { (text, count) -> PickedExpression(text, count) }
        .sortedWith(compareByDescending<PickedExpression> { it.count }.thenBy { it.text })
    return SpeechObservations(
        recordedAnswers = eligible.size,
        windowAnswers = window.size,
        meanWords = if (tokens.isEmpty()) null else tokens.sumOf { it.size }.toDouble() / tokens.size,
        distinctWords = tokens.flatten().toSet().size,
        reasonAnswers = window.count { REPORT_REASON_EXPRESSION.containsMatchIn(it.text) },
        choices = selections,
    )
}
