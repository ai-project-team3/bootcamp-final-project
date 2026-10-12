package com.example.finalproject_demo.demo

/** A standalone linking expression is A1 evidence, never proof of a causal answer (S1). */
internal fun storyHasLinkingExpression(text: String): Boolean =
    Regex("(^|[\\s,.!?…])(?:그래서|그리고|그다음에|그러니까|그런데)(?=$|[\\s,.!?…])").containsMatchIn(text)

internal data class QuestionStyle(val level: Level, val rising: Int = 0, val shorter: Int = 0)

/** The existing two-consecutive-turn rule, shared by live updates and replay of measured inputs. */
internal fun nextQuestionStyle(style: QuestionStyle, evidence: LanguageEvidence?, missed: Boolean = false): QuestionStyle {
    if (evidence == null) return style.copy(rising = 0, shorter = 0)
    val structure = evidence.elements.any { it in setOf("계기", "시도", "결과") } || evidence.a1
    val up = if (style.level == Level.PICK) evidence.s1 || evidence.elements.isNotEmpty() else evidence.s1 && structure
    val down = missed || (evidence.words <= 2 && !up)
    var rising = if (up) style.rising + 1 else 0
    var shorter = if (down) style.shorter + 1 else 0
    var level = style.level
    if (rising >= 2) { rising = 0; level = level.up() }
    if (shorter >= 2) { shorter = 0; level = level.down() }
    return QuestionStyle(level, rising, shorter)
}

private fun TalkLine.measuredStoryLanguage(): LanguageEvidence? = languageEvidence?.takeIf {
    who == "child" && questionType in setOf(ReportQuestionType.OPEN, ReportQuestionType.REASON) &&
        it.counted && it.words > 0 && !storyNonAnswer(text)
}

/** Stage names are never stored; replay only the last 50 measured answers and the breaks between them. */
internal fun questionStyleForHistory(sessions: List<AnswerSession>): Level? {
    val answers = sessions.filter { it.mode == "story" }.flatMap { it.answers }
    val measuredIndices = answers.indices.filter { answers[it].measuredStoryLanguage() != null }
    if (measuredIndices.size < 2) return null
    val start = measuredIndices.takeLast(50).first()
    var style = QuestionStyle(Level.CHAIN)
    for (answer in answers.drop(start)) {
        if (answer.languageEvidence?.counted == false) continue
        style = nextQuestionStyle(style, answer.measuredStoryLanguage())
    }
    return style.level
}

/** Only a new live story takes a starting style. Resuming a story preserves its in-progress rules. */
internal fun DemoState.initializeStoryQuestionStyle() {
    if (mode != StoryMode.STORY || storyStartedAtMs != 0L || turn != 0 || notes.isNotEmpty() ||
        reportProfileId != AnswerHistory.profileId) return
    val observed = if (AnswerHistory.readFailed || AnswerHistory.writeFailed) null else questionStyleForHistory(AnswerHistory.all())
    level = observed ?: profile.level ?: Level.CHAIN
    levelAtStart = level
    nextLevel = null
    s1streak = 0; noAnswerStreak = 0
}
